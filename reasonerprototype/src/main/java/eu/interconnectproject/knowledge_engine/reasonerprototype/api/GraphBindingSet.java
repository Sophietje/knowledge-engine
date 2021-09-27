package eu.interconnectproject.knowledge_engine.reasonerprototype.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import eu.interconnectproject.knowledge_engine.reasonerprototype.api.TriplePattern.Literal;
import eu.interconnectproject.knowledge_engine.reasonerprototype.api.TriplePattern.Value;
import eu.interconnectproject.knowledge_engine.reasonerprototype.api.TriplePattern.Variable;

public class GraphBindingSet {

	private Set<TriplePattern> graphPattern;
	private Set<TripleVarBinding> bindings;

	public GraphBindingSet(Set<TriplePattern> aGraphPattern) {

		this.graphPattern = aGraphPattern;
		bindings = new HashSet<>();
	}

	public Set<TriplePattern> getGraphPattern() {
		return graphPattern;
	}

	public Set<TripleVarBinding> getBindings() {
		return bindings;
	}

	public BindingSet toBindingSet() {
		BindingSet bs = new BindingSet();
		for (TripleVarBinding tvb : this.bindings) {
			bs.add(tvb.toBinding());
		}
		return bs;
	}

	public void add(TripleVarBinding aTripleVarBinding) {
		this.bindings.add(aTripleVarBinding);
	}

	public Set<TripleVar> getTripleVars() {
		Set<TripleVar> vars = new HashSet<>();
		for (TriplePattern tp : graphPattern) {
			for (Variable var : tp.getVars()) {
				vars.add(new TripleVar(tp, var));
			}
		}
		return vars;
	}

	public GraphBindingSet getFullBindingSet() {

		GraphBindingSet gbs = new GraphBindingSet(this.graphPattern);
		Set<TripleVar> vars = this.getTripleVars();
		int nrOfVars = vars.size();
		for (TripleVarBinding tvb : bindings) {
			if (tvb.keySet().size() == nrOfVars) {
				gbs.add(tvb);
			}
		}
		return gbs;
	}

	/**
	 * Checks whether all variables in the triples in the given graph pattern are
	 * fully bound.
	 * 
	 * @param graphPattern
	 * @return
	 */
	public GraphBindingSet getFullBindingSet(Set<TriplePattern> graphPattern) {

		GraphBindingSet gbs = new GraphBindingSet(this.graphPattern);
		for (TripleVarBinding tvb : bindings) {
			boolean allVariablesAvailable = true;
			for (TriplePattern tp : graphPattern) {
				for (Variable var : tp.getVars()) {
					if (!tvb.containsKey(new TripleVar(tp, var))) {
						allVariablesAvailable = false;
					}
				}
			}
			if (allVariablesAvailable)
				gbs.add(tvb);
		}
		return gbs;
	}

	public GraphBindingSet getPartialBindingSet() {
		GraphBindingSet gbs = new GraphBindingSet(this.graphPattern);
		Set<TripleVar> vars = this.getTripleVars();
		int nrOfVars = vars.size();
		for (TripleVarBinding tvb : bindings) {
			if (tvb.keySet().size() < nrOfVars) {
				gbs.add(tvb);
			}
		}
		return gbs;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((bindings == null) ? 0 : bindings.hashCode());
		result = prime * result + ((graphPattern == null) ? 0 : graphPattern.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof GraphBindingSet)) {
			return false;
		}
		GraphBindingSet other = (GraphBindingSet) obj;
		if (bindings == null) {
			if (other.bindings != null) {
				return false;
			}
		} else if (!bindings.equals(other.bindings)) {
			return false;
		}
		if (graphPattern == null) {
			if (other.graphPattern != null) {
				return false;
			}
		} else if (!graphPattern.equals(other.graphPattern)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return bindings.toString();
	}

	/**
	 * Simply the union between the two bindingsets. Does nothing complex for now.
	 * 
	 * @param gbs
	 * @return
	 */
	public GraphBindingSet merge(GraphBindingSet aGraphBindingSet) {
		GraphBindingSet gbs = new GraphBindingSet(this.graphPattern);

		if (this.bindings.isEmpty()) {
			for (TripleVarBinding tvb2 : aGraphBindingSet.getBindings()) {
				gbs.add(tvb2);
			}
		} else {
			// Cartesian product is the base case
			boolean firstTime = true;
			for (TripleVarBinding tvb1 : this.bindings) {
				gbs.add(tvb1);

				for (TripleVarBinding otherB : aGraphBindingSet.getBindings()) {
					if (firstTime)
						gbs.add(otherB);

					// always add a merged version of the two bindings, except when they conflict.
					if (tvb1.isOverlapping(otherB) && !tvb1.isConflicting(otherB)) {
						gbs.add(tvb1.merge(otherB));
					}
				}
				firstTime = false;
			}
		}

		return gbs;
	}

	public boolean isEmpty() {

		return this.bindings.isEmpty();

	}

	/**
	 * Translate this bindingset using the given match. The variablenames will be
	 * changed and variables not relevant in the match will be removed.
	 * 
	 * The format of the mapping is expected to be translate <from triple pattern>,
	 * <to triple pattern>.
	 * 
	 * It also filters bindings that are incompatible with the match.
	 * 
	 * @param match
	 * @return
	 */
	public GraphBindingSet translate(Set<TriplePattern> graphPattern, Set<Map<TriplePattern, TriplePattern>> match) {
		GraphBindingSet newOne = new GraphBindingSet(graphPattern);
		TripleVarBinding newB;

		if (this.bindings.isEmpty()) {
			// bindings coming through the match.
			for (Map<TriplePattern, TriplePattern> entry : match) {
				newB = new TripleVarBinding();
				for (Map.Entry<TriplePattern, TriplePattern> keyValue : entry.entrySet()) {
					Map<Value, Value> mapping = keyValue.getKey().matchesWithSubstitutionMap(keyValue.getValue());
					for (Map.Entry<Value, Value> singleMap : mapping.entrySet()) {
						if (singleMap.getValue() instanceof Variable && singleMap.getKey() instanceof Literal) {
							// if the binding set is empty (and we are translating child results back to current node results, we actually do not want to add the static literal.
							newB.put(new TripleVar(keyValue.getValue(), (Variable) singleMap.getValue()),
									(Literal) singleMap.getKey());
						}
					}

				}
				newOne.add(newB);
			}

		} else {

			for (TripleVarBinding b : this.bindings) {
				newB = new TripleVarBinding();
				boolean skip = false;
				for (Map<TriplePattern, TriplePattern> entry : match) {
					for (Map.Entry<TriplePattern, TriplePattern> keyValue : entry.entrySet()) {
						if (b.containsTriplePattern(keyValue.getKey())) {
							Map<Value, Value> mapping = keyValue.getKey()
									.matchesWithSubstitutionMap(keyValue.getValue());
							for (Map.Entry<Value, Value> singleMap : mapping.entrySet()) {
								if (singleMap.getValue() instanceof Variable && singleMap.getKey() instanceof Literal) {
									newB.put(new TripleVar(keyValue.getValue(), (Variable) singleMap.getValue()),
											(Literal) singleMap.getKey());
								} else if (singleMap.getValue() instanceof Variable && b
										.containsKey(new TripleVar(keyValue.getKey(), (Variable) singleMap.getKey()))) {
									TripleVar aTripleVar2 = new TripleVar(keyValue.getKey(),
											(Variable) singleMap.getKey());
									newB.put(new TripleVar(keyValue.getValue(), (Variable) singleMap.getValue()),
											b.get(aTripleVar2));
								} else if (singleMap.getValue() instanceof Literal
										&& b.containsKey(
												new TripleVar(keyValue.getKey(), (Variable) singleMap.getKey()))
										&& b.get(new TripleVar(keyValue.getKey(), (Variable) singleMap.getKey()))
												.equals(singleMap.getValue())) {
									// we do not have to add it, if we translate it back.
									skip = false;
									
								} else {
									skip = true;
								}
							}
						}
					}
				}
				if (!skip)
					newOne.add(newB);
			}
		}
		return newOne;

	}

	public void addAll(Set<TripleVarBinding> permutatedTVBs) {
		this.bindings.addAll(permutatedTVBs);
	}
}
