/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.search.strategy.selectors.values;

import org.chocosolver.solver.CompactConstraintNetwork;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.variables.Variable;

import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;

public class SubSetToObjective {

    private CompactConstraintNetwork network;
    private HashSet<Propagator<?>> onlyActivated = new HashSet<>();

    HashSet<Variable> currentVariables = new HashSet<>();
    HashSet<Variable> nextVariables = new HashSet<>();
    HashSet<Propagator<?>> propagatorsOnShortestPath = new HashSet<>();

    public SubSetToObjective(Model model) {
        network = new CompactConstraintNetwork(model);
    }

    /**
     * Active only the subset of propagators that are on the shortest path between this variable and the objective
     * All other propagators will be set as inactive
     * @param variable
     * @return true if the objective can be reached by propagating a change on the variable
     */
    public boolean deactivatePropagatorsOutsideShortestPath(Variable variable) {
        onlyActivated.clear();
        Model model = variable.getModel();
        Variable objective = model.getObjective();
        boolean reachedObjective = false;
        currentVariables.clear();
        nextVariables.clear();
        propagatorsOnShortestPath.clear();
        for (Variable v: CompactConstraintNetwork.getConcreteVars(variable)) {
            currentVariables.add(v);
            if (v == objective) {
                reachedObjective = true;
            }
        }
        while (!currentVariables.isEmpty()) {
            for (Variable var: currentVariables) {
                CompactConstraintNetwork.VarNode varNode = network.getVarNode(var);
                if (varNode != null ) {
                    List<CompactConstraintNetwork.DirectedPropagatorEdge> parents = varNode.parents;
                    for (CompactConstraintNetwork.DirectedPropagatorEdge edge : parents) {
                        // only consider propagators that are active
                        if (edge.propagator.isActive()) {
                            if (edge.goingTowardObjective == objective) {
                                reachedObjective = true;
                            }
                            if (!edge.goingTowardObjective.isInstantiated()) {
                                nextVariables.add(edge.goingTowardObjective);
                            }
                            propagatorsOnShortestPath.add(edge.propagator);
                        }
                    }
                }
            }
            for (Variable var: currentVariables) {
                var.forEachPropagator(deactivator);
            }
            // reachedObjective = reachedObjective || currentVariables.contains(objective);
            // clear currentVariables and swap with nextVariables
            currentVariables.clear();
            HashSet<Variable> tmp = currentVariables;
            currentVariables = nextVariables;
            nextVariables = tmp;
        }
        return reachedObjective;
    }

    private BiConsumer<Variable, Propagator<?>> deactivator = (variable, propagator) -> {
        if (propagator.isActive() && !propagatorsOnShortestPath.contains(propagator)) {
            propagator.setPassive();
        }
    };

}
