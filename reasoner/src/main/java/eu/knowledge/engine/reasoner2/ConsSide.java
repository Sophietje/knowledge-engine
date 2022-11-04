package eu.knowledge.engine.reasoner2;

import java.util.Set;

import eu.knowledge.engine.reasoner.Match;
import eu.knowledge.engine.reasoner.api.TripleVarBindingSet;
import eu.knowledge.engine.reasoner2.reasoningnode.RuleNode;

/**
 * @author nouwtb
 *
 */
public interface ConsSide {

	public void addConsequentNeighbour(RuleNode neighbour, Set<Match> matches);

	public Set<RuleNode> getConsequentNeighbours();

	public boolean addFilterBindingSetInput(RuleNode aNeighbor, TripleVarBindingSet bs);
}
