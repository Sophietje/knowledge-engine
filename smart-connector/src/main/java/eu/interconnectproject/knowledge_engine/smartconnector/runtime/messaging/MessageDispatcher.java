package eu.interconnectproject.knowledge_engine.smartconnector.runtime.messaging;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.interconnectproject.knowledge_engine.smartconnector.api.SmartConnector;
import eu.interconnectproject.knowledge_engine.smartconnector.messaging.KnowledgeMessage;
import eu.interconnectproject.knowledge_engine.smartconnector.runtime.KeRuntime;
import eu.interconnectproject.knowledge_engine.smartconnector.runtime.KnowledgeDirectoryProxy;
import eu.interconnectproject.knowledge_engine.smartconnector.runtime.messaging.inter_ker.api.factories.MessagingApiServiceFactory;
import eu.interconnectproject.knowledge_engine.smartconnector.runtime.messaging.inter_ker.api.factories.SmartConnectorManagementApiServiceFactory;
import eu.interconnectproject.knowledge_engine.smartconnector.runtime.messaging.inter_ker.model.KnowledgeEngineRuntimeDetails;

/**
 * The {@link MessageDispatcher} is responsible for sending messages between
 * {@link SmartConnector}s (also know as the SPARQL+ protocol).
 *
 * Within a single JVM you can have multiple {@link SmartConnector}s. Such a JVM
 * is called a Knowledge Engine Runtime. Message exchange between those
 * {@link SmartConnector}s happens in-memory. For message exchange between
 * Knowledge Engine Runtimes, a JSON- and REST-based peer-to-peer protocol is
 * used. Knowledge Engine Runtimes communicate directly with each other, but the
 * KnowledgeDirectory is used as a bootstrapping mechanism for peers to find
 * each other.
 *
 * The {@link MessageDispatcher} is responsible for communication between
 * {@link SmartConnector} within the same Knowledge Engine Runtime, for
 * communication between {@link SmartConnector}s in different Knowledge Engine
 * Runtimes and for communication with the Knowledge Directory.
 *
 * The {@link MessageDispatcher} is configured in the {@link KeRuntime} class.
 */
public class MessageDispatcher implements KnowledgeDirectoryProxy {

	private static final Logger LOG = LoggerFactory.getLogger(MessageDispatcher.class);

	public static final String PEER_PROTOCOL = "http";

	private static enum State {
		NEW, RUNNING, STOPPED
	}

	private final String myHostname;
	private final int myPort;
	private final String kdHostname;
	private final int kdPort;

	private State state;
	private Server httpServer;

	private KnowledgeDirectoryConnection knowledgeDirectoryConnectionManager = null;
	private LocalSmartConnectorConnectionManager localSmartConnectorConnectionsManager = null;
	private RemoteKerConnectionManager remoteSmartConnectorConnectionsManager = null;

	/**
	 * Construct the {@link MessageDispatcher} in a distributed mode, with an
	 * external KnowledgeDirectory.
	 *
	 * @param myHostname
	 * @param myPort
	 * @param kdHostname
	 * @param kdPort
	 */
	public MessageDispatcher(String myHostname, int myPort, String kdHostname, int kdPort) {
		this.myHostname = myHostname;
		this.myPort = myPort;
		this.kdHostname = kdHostname;
		this.kdPort = kdPort;
		this.state = State.NEW;
	}

	/**
	 * Construct the {@link MessageDispatcher} in a JVM-only mode, without an
	 * external Knowledge Directory.
	 */
	public MessageDispatcher() {
		this(null, 0, null, 0);
	}

	boolean runsInDistributedMode() {
		return kdHostname != null;
	}

	public void start() throws Exception {
		// Check and update state
		if (state != State.NEW) {
			throw new IllegalStateException("DistributedMesasgeDispatcher already started or stopped");
		}
		this.state = State.RUNNING;

		// Start the LocalSmartConnnectorConnectionsManager
		localSmartConnectorConnectionsManager = new LocalSmartConnectorConnectionManager(this);
		localSmartConnectorConnectionsManager.start();

		if (runsInDistributedMode()) {
			// Start Knowledge Directory Connection Manager
			this.knowledgeDirectoryConnectionManager = new KnowledgeDirectoryConnection(kdHostname, kdPort, myHostname,
					myPort);
			this.getKnowledgeDirectoryConnectionManager().start();

			// Start the RemoteSmartConnnectorConnectionsManager
			remoteSmartConnectorConnectionsManager = new RemoteKerConnectionManager(this);
			getRemoteSmartConnectorConnectionsManager().start();

			// Start HTTP Server
			this.startHttpServer();
		}
	}

	public void stop() throws Exception {
		if (state != State.RUNNING) {
			throw new IllegalStateException("Can only stop server when it is running");
		}
		this.state = State.STOPPED;

		// Stop the LocalSmartConnnectorConnectionsManager
		localSmartConnectorConnectionsManager.stop();

		if (runsInDistributedMode()) {
			// Stop the RemoteSmartConnnectorConnectionsManager
			getRemoteSmartConnectorConnectionsManager().stop();

			// Stop the connection with the Knowledge Directory
			knowledgeDirectoryConnectionManager.stop();

			// Stop HTTP server
			this.stopHttpServer();
		}
	}

	private void startHttpServer() throws Exception {
		LOG.info("Starting Inter-KER REST API on port {}.", myPort);

		httpServer = new Server(myPort);

		ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);

		ctx.setContextPath("/");
		httpServer.setHandler(ctx);

		ServletHolder serHol = ctx.addServlet(ServletContainer.class, "/*");
		serHol.setInitOrder(1);
		serHol.setInitParameter("jersey.config.server.provider.packages",
				"eu.interconnectproject.knowledge_engine.smartconnector.runtime.messaging");
		serHol.setInitParameter("port", String.valueOf(myPort));

		SmartConnectorManagementApiServiceFactory.registerSmartConnectorManagementApiService(myPort,
				remoteSmartConnectorConnectionsManager);
		MessagingApiServiceFactory.registerMessagingApiService(myPort,
				remoteSmartConnectorConnectionsManager.getMessageReceiver());

		httpServer.start();
	}

	public void joinHttpServer() throws InterruptedException {
		if (state != State.RUNNING) {
			throw new IllegalStateException("Can only join server when it is running");
		}
		httpServer.join();
	}

	private void stopHttpServer() throws Exception {
		httpServer.stop();
		httpServer.destroy();
		SmartConnectorManagementApiServiceFactory.unregisterSmartConnectorManagementApiService(myPort);
		MessagingApiServiceFactory.unregisterMessagingApiService(myPort);
	}

	/**
	 * This is an internal method called by the
	 * {@link LocalSmartConnectorConnection}, which sends the message to the right
	 * (local or remote) sender
	 *
	 * @param message
	 * @throws IOException
	 */
	void sendToLocalOrRemoteSmartConnector(KnowledgeMessage message) throws IOException {
		boolean success = false;
		LocalSmartConnectorConnection localSender = localSmartConnectorConnectionsManager
				.getLocalSmartConnectorConnection(message.getToKnowledgeBase());
		if (localSender != null) {
			localSender.deliverToLocalSmartConnector(message);
			success = true;
		} else {
			if (runsInDistributedMode()) {
				// must be a remote smart connector then
				RemoteKerConnection remoteSender = getRemoteSmartConnectorConnectionsManager()
						.getRemoteKerConnection(message.getToKnowledgeBase());
				if (remoteSender != null) {
					remoteSender.sendToRemoteSmartConnector(message);
					success = true;
				}
			}
		}
		if (!success) {
			// Cannot find a remote or a local sender
			throw new IOException("Could not send message " + message.getMessageId() + ", the Knowledge Base "
					+ message.getToKnowledgeBase() + " is not known");
		}
	}

	/**
	 * This is an internal method called by the REMOTE receiver, which sends the
	 * message to the right local SmartConnector
	 *
	 * @param message
	 * @throws IOException
	 */
	void deliverToLocalSmartConnector(KnowledgeMessage message) throws IOException {
		LocalSmartConnectorConnection cm = localSmartConnectorConnectionsManager
				.getLocalSmartConnectorConnection(message.getToKnowledgeBase());
		if (cm != null) {
			cm.deliverToLocalSmartConnector(message);
		} else {
			throw new IOException("Could not deliver message " + message.getMessageId() + ", the Knowledge Base "
					+ message.getToKnowledgeBase() + " is not known locally");
		}
	}

	KnowledgeEngineRuntimeDetails getMyKnowledgeEngineRuntimeDetails() {
		KnowledgeEngineRuntimeDetails kers = new KnowledgeEngineRuntimeDetails();
		// TODO check state of the knowledgeDirectoryConnectionManager
		kers.setRuntimeId(getKnowledgeDirectoryConnectionManager().getMyKnowledgeDirectoryId());
		kers.setSmartConnectorIds(localSmartConnectorConnectionsManager.getLocalSmartConnectorIds().stream()
				.map(URI::toString).collect(Collectors.toList()));
		return kers;
	}

	public RemoteKerConnectionManager getRemoteSmartConnectorConnectionsManager() {
		return remoteSmartConnectorConnectionsManager;
	}

	LocalSmartConnectorConnectionManager getLocalSmartConnectorConnectionManager() {
		return localSmartConnectorConnectionsManager;
	}

	KnowledgeDirectoryConnection getKnowledgeDirectoryConnectionManager() {
		return knowledgeDirectoryConnectionManager;
	}

	/**
	 * Implementation of the {@link KnowledgeDirectoryProxy}
	 */
	@Override
	public Set<URI> getKnowledgeBaseIds() {
		HashSet<URI> set = new HashSet<>();
		set.addAll(this.localSmartConnectorConnectionsManager.getLocalSmartConnectorIds());
		if (runsInDistributedMode()) {
			set.addAll(this.getRemoteSmartConnectorConnectionsManager().getRemoteSmartConnectorIds());
		}
		return set;
	}

}
