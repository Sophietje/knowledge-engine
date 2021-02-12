package eu.interconnectproject.knowledge_engine.smartconnector.api;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link PostResult} contains the result of the
 * {@link PostKnowledgeInteraction}, of course including the {@link Binding}s,
 * but (in the future) also information on how the result is formed (which
 * {@link KnowledgeBase}s contributed etc.)
 */
public class PostResult {

	private final BindingSet bindings;
	private final Map<URI, PostExchangeInfo> exchangeInfoPerKnowledgeBase;

	/**
	 * Create a {@link PostResult}.
	 * 
	 * @param someBindings A {@link BindingSet} that contains the solutions to an
	 *                     {@link PostKnowledgeInteraction} question. It is either
	 *                     empty, or contains one or more {@link Binding}s with a
	 *                     value for every available variable in the
	 *                     {@link GraphPattern}.
	 */
	public PostResult(BindingSet someBindings, Set<ExchangeInfo> exchangeInfos) {
		this.bindings = someBindings;

		exchangeInfoPerKnowledgeBase = exchangeInfos.stream()
				.collect(Collectors.toMap(x -> x.getKnowledgeBaseId(), x -> (PostExchangeInfo) x));

	}

	/**
	 * @return The {@link BindingSet} that contains the results of the
	 *         {@link PostKnowledgeInteraction} that happened.
	 */
	public BindingSet getBindings() {
		return this.bindings;
	}

	public Map<URI, PostExchangeInfo> getExchangeInfoPerKnowledgeBase() {
		return Collections.unmodifiableMap(exchangeInfoPerKnowledgeBase);
	}
}
