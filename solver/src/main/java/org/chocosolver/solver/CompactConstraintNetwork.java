/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.view.IView;

import java.util.*;
import java.util.stream.Collectors;

public class CompactConstraintNetwork {

    private Map<Variable, VarNode> varNodes = new HashMap<>();
    private Map<IView<?>, Variable> viewToClosestVariableToObjective = new HashMap<>();
    private Variable objective;
    protected Set<Variable> processedVariables = new HashSet<>(); // variables already processed
    protected Set<Propagator<?>> processedContraints = new HashSet<>(); // constraints already processed
    protected Queue<Variable> parentVars = new ArrayDeque<>(); // variables to process at the current iteration
    protected Set<Variable> childVars = new HashSet<>(); // new variables to process for the next iteration

    public Variable getMostRelevantVar(Variable var) {
        return isView(var) ? (IntVar) viewToClosestVariableToObjective.get((IView<?>) var) : var;
    }

    public VarNode getVarNode(Variable var) {
        if (var == null)
            return null;
        return varNodes.get(var);
    }

    public static class VarNode {
        public Variable variable;
        public List<DirectedPropagatorEdge> parents;
        public int distanceToObjective;

        public VarNode(Variable variable, int distanceToObjective) {
            this.variable = variable;
            this.parents = new ArrayList<>();
            this.distanceToObjective = distanceToObjective;
        }

        public void addParent(DirectedPropagatorEdge parent) {
            parents.add(parent);
        }
    }

    public static class DirectedPropagatorEdge {
        public Propagator<?> propagator;
        public Variable goingTowardObjective;
        public Variable goingAwayFromObjective;

        private DirectedPropagatorEdge(Propagator<?> propagator, Variable goingTowardObjective, Variable goingAwayFromObjective) {
            this.propagator = propagator;
            this.goingTowardObjective = goingTowardObjective;
            this.goingAwayFromObjective = goingAwayFromObjective;
        }

        public boolean isActive() {
            return propagator.isActive() && !goingTowardObjective.isInstantiated();
        }
    }

    private static boolean isConcreteVar(Variable variable) {
        return (variable.getTypeAndKind() & Variable.VAR) !=0;
    }

    private static boolean isConstant(Variable variable) {
        return (variable.getTypeAndKind() & Variable.CSTE) !=0;
    }

    private static boolean isView(Variable variable) {
        return (variable.getTypeAndKind() & Variable.VIEW) !=0;
    }

    public static Variable[] getConcreteVars(Variable variable) {
        if (variable instanceof IView<?>) {
            Variable[] variables = ((IView<?>) variable).getVariables();
            Set<Variable> vars = new HashSet<>();
            for (Variable var: variables) {
                Variable[] observed = getConcreteVars(var);
                for (Variable obs: observed) {
                    if (isConcreteVar(obs)) {
                        vars.add(var);
                    }
                }
            }
            Variable[] varArray = new Variable[vars.size()];
            int i = 0;
            for (Variable v: vars) {
                if (v == null) {
                    throw new RuntimeException("invalid");
                }
                varArray[i++] = v;
            }
            return varArray;
        } else {
            return new Variable[] {variable};
        }
    }

    public CompactConstraintNetwork(Model model) {
        objective = model.getObjective();
        storeShortestPathToObjective();
        setPathForViews();
    }

    private void storeShortestPathToObjective() {
        Variable[] concreteObjective = getConcreteVars(objective);
        parentVars.clear();
        for (Variable objectiveVar: concreteObjective) {
            varNodes.put(objectiveVar, new VarNode(objectiveVar, 0)); // variables corresponding to the objective have no parent associated
            parentVars.add(objectiveVar);
            processedVariables.add(objectiveVar);
        }
        int distance = 1;
        while (!parentVars.isEmpty()) {
            for (Variable varSource: parentVars) {
                Variable[] originalSourceArray = getConcreteVars(varSource);
                for (Variable parent: originalSourceArray) {
                    // for each variable, look at its unprocessed constraints
                    for (Propagator<?> p : parent.streamPropagators().collect(Collectors.toList())) {
                        if (!processedContraints.contains(p)) {
                            // for each new discovered constraint
                            processedContraints.add(p);
                            for (Variable variable : p.getVars()) {
                                for (Variable child : getConcreteVars(variable)) {
                                    if (!processedVariables.contains(child) && !isConstant(child)) {
                                        // stores the path for reaching this variable
                                        childVars.add(child);
                                        if (!varNodes.containsKey(child)) {
                                            varNodes.put(child, new VarNode(child, distance));
                                        }
                                        varNodes.get(child).addParent(new DirectedPropagatorEdge(p, parent, child));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            processedVariables.addAll(childVars);
            parentVars.clear();
            parentVars.addAll(childVars);
            childVars.clear();
            distance += 1;
        }
    }

    private void setPathForViews() {
        Model model = objective.getModel();
        for (Variable var: model.getVars()) {
            if (isView(var)) {
                Variable[] concreteVars = getConcreteVars(var);
                int shortestPath = Integer.MAX_VALUE;
                Variable bestVar = null;
                for (Variable concrete: concreteVars) {
                    VarNode n = varNodes.get(concrete);
                    if (n != null) {
                        int distance = n.distanceToObjective;
                        if (distance < shortestPath) {
                            bestVar = concrete;
                            shortestPath = distance;
                        }
                    }
                }
                viewToClosestVariableToObjective.put((IView<?>) var, bestVar);
            }
        }
    }

}
