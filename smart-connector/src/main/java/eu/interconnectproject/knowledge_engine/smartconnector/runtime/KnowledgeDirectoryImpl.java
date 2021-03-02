package eu.interconnectproject.knowledge_engine.smartconnector.runtime;

import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

import eu.interconnectproject.knowledge_engine.smartconnector.runtime.KnowledgeDirectory;
import eu.interconnectproject.knowledge_engine.smartconnector.impl.SmartConnectorImpl;

/**
 * This is a KnowledgeDirectory implementation that only provides information
 * for {@link SmartConnectorImpl}s inside this JVM! NEEDS TO BE REPLACED ONCE WE
 * SUPPORT DISTRIBUTED KNOWLEDGE ENGINES! TODO
 */
public class KnowledgeDirectoryImpl implements KnowledgeDirectory {

	/**
	 * Constructor may only be called by {@link Runtime}
	 */
	KnowledgeDirectoryImpl() {
	}

	@Override
	public Set<URI> getKnowledgeBaseIds() {
		return KeRuntime.localSmartConnectorRegistry().getSmartConnectors().stream().map(sc -> sc.getKnowledgeBaseId())
				.collect(Collectors.toSet());
	}

}