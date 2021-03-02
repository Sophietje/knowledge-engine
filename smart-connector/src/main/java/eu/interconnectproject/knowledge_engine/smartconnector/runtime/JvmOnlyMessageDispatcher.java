package eu.interconnectproject.knowledge_engine.smartconnector.runtime;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.interconnectproject.knowledge_engine.smartconnector.api.SmartConnector;
import eu.interconnectproject.knowledge_engine.smartconnector.runtime.LocalSmartConnectorRegistry;
import eu.interconnectproject.knowledge_engine.smartconnector.runtime.SmartConnectorRegistryListener;
import eu.interconnectproject.knowledge_engine.smartconnector.messaging.AnswerMessage;
import eu.interconnectproject.knowledge_engine.smartconnector.messaging.AskMessage;
import eu.interconnectproject.knowledge_engine.smartconnector.messaging.KnowledgeMessage;
import eu.interconnectproject.knowledge_engine.smartconnector.messaging.MessageDispatcherEndpoint;
import eu.interconnectproject.knowledge_engine.smartconnector.messaging.PostMessage;
import eu.interconnectproject.knowledge_engine.smartconnector.messaging.ReactMessage;
import eu.interconnectproject.knowledge_engine.smartconnector.messaging.SmartConnectorEndpoint;
import eu.interconnectproject.knowledge_engine.smartconnector.impl.SmartConnectorImpl;

/**
 * This class is responsible for delivering messages between
 * {@link SmartConnectorImpl}s. Once constructed, it registers itself at the
 * {@link LocalSmartConnectorRegistry} and at all the {@link SmartConnector}s.
 *
 * THIS VERSION ONLY WORKS FOR THE JVM ONLY. REPLACE FOR DISTRIBUTED VERSION OF
 * KNOWLEDGE ENGINE. TODO
 */
public class JvmOnlyMessageDispatcher implements SmartConnectorRegistryListener {

	protected static Logger LOG = LoggerFactory.getLogger(JvmOnlyMessageDispatcher.class);

	private class SmartConnectorHandler implements MessageDispatcherEndpoint {

		private final SmartConnectorEndpoint endpoint;

		public SmartConnectorHandler(SmartConnectorEndpoint sce) {
			this.endpoint = sce;
		}

		public void start() {
			this.endpoint.setMessageDispatcher(this);
		}

		@Override
		public void send(KnowledgeMessage message) throws IOException {
			assert message.getFromKnowledgeBase()
					.equals(this.endpoint.getKnowledgeBaseId()) : "the fromKnowledgeBaseId should be mine, but isn't.";

			SmartConnectorHandler receiver = JvmOnlyMessageDispatcher.this.handlers.get(message.getToKnowledgeBase());
			if (receiver == null) {
				throw new IOException("There is no KnowledgeBase with ID " + message.getToKnowledgeBase());
			} else {
				KeRuntime.executorService().execute(() -> {

					try {

						if (message instanceof AnswerMessage) {
							receiver.getEndpoint().handleAnswerMessage((AnswerMessage) message);
						} else if (message instanceof AskMessage) {
							receiver.getEndpoint().handleAskMessage((AskMessage) message);
						} else if (message instanceof PostMessage) {
							receiver.getEndpoint().handlePostMessage((PostMessage) message);
						} else if (message instanceof ReactMessage) {
							receiver.getEndpoint().handleReactMessage((ReactMessage) message);
						} else {
							assert false;
						}
					} catch (Throwable t) {
						LOG.error("No errors should occur.", t);
					}
				});
			}
		}

		public SmartConnectorEndpoint getEndpoint() {
			return this.endpoint;
		}

		public void close() {
			this.endpoint.unsetMessageDispatcher();
		}
	}

	private final Map<URI, SmartConnectorHandler> handlers = new HashMap<>();

	/**
	 * Constructor may only be called by {@link KeRuntime}
	 */
	JvmOnlyMessageDispatcher() {
		KeRuntime.localSmartConnectorRegistry().addListener(this);

		// Add all the smart connectors that already existed before we were registered
		// as listener
		for (SmartConnectorImpl sc : KeRuntime.localSmartConnectorRegistry().getSmartConnectors()) {
			this.smartConnectorAdded(sc);
		}
	}

	@Override
	public void smartConnectorAdded(SmartConnectorImpl smartConnector) {
		// Create a new SmartConnectorHandler and attach it
		SmartConnectorEndpoint endpoint = smartConnector.getSmartConnectorEndpoint();
		SmartConnectorHandler handler = new SmartConnectorHandler(endpoint);
		this.handlers.put(endpoint.getKnowledgeBaseId(), handler);
		handler.start();
	}

	@Override
	public void smartConnectorRemoved(SmartConnectorImpl smartConnector) {
		SmartConnectorHandler handler = this.handlers.remove(smartConnector.getKnowledgeBaseId());
		handler.close();
	}

	public void stop() {
		KeRuntime.localSmartConnectorRegistry().removeListener(this);
		this.handlers.values().forEach(SmartConnectorHandler::close);
		this.handlers.clear();
	}

}