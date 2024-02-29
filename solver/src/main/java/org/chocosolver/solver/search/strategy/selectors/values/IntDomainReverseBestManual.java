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

import org.chocosolver.solver.Cause;
import org.chocosolver.solver.ConstraintNetwork;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.events.PropagatorEventType;
import org.chocosolver.solver.variables.view.IView;

import java.util.*;

public class IntDomainReverseBestManual extends IntDomainReverseBest {

    public IntDomainReverseBestManual() {
        super();
    }

    public IntDomainReverseBestManual(IntValueSelector fallBack) {
        super(fallBack);
    }

    protected Map<Variable, List<Set<Propagator<?>>>> constraintsOnShortestPath = new HashMap<>();
    private Map<IView<?>, Variable> viewToClosestVariableToObjective;

    private void createConstraintGraph(Model model) {
        ConstraintNetwork constraintGraph = new ConstraintNetwork(model);
        viewToClosestVariableToObjective = constraintGraph.getViewToClosestVariableToObjective();
        for (Variable var: constraintGraph.variables()) {
            constraintsOnShortestPath.put(var, new ArrayList<>());
            int distToObjective = constraintGraph.hopToObjective(var);
            int i = 0;
            for (int depth = distToObjective - 1 ; depth >= 0  ; depth--) {
                constraintsOnShortestPath.get(var).add(new HashSet<>());
                for (Propagator<?> propagator: constraintGraph.constraintsAtDistanceFromObjective(var, depth)) {
                    constraintsOnShortestPath.get(var).get(i).add(propagator);
                }
                i++;
            }
        }
    }

    private boolean isConstraintGraphCreated() {
        return !constraintsOnShortestPath.isEmpty();
    }

    private boolean isView(Variable variable) {
        return (variable.getTypeAndKind() & Variable.VIEW) !=0;
    }

    @Override
    public int selectValue(IntVar var) throws ContradictionException {
        Model model = var.getModel();
        if (!isConstraintGraphCreated()) {
            createConstraintGraph(model);
        }
        return super.selectValue(var);
    }

    @Override
    protected int selectWithPropagate(IntVar var) throws ContradictionException {
        IntVar originalVar = isView(var) ? (IntVar) viewToClosestVariableToObjective.get((IView<?>) var) : var;
        Model model = var.getModel();
        int distToObjective = constraintsOnShortestPath.get(originalVar).size();
        for (int level = distToObjective - 1; level >= 0; level--) {
            for (Propagator<?> propagator: constraintsOnShortestPath.get(originalVar).get(level)) {
                try {
                    if (propagator.isActive()) {
                        propagator.propagate(PropagatorEventType.FULL_PROPAGATION.getMask());
                    }
                } catch (ContradictionException cex) {
                    model.getSolver().getEngine().flush();
                    throw cex;
                }
            }
        }
        model.getSolver().getEngine().flush();
        return fallbackValueSelector.selectValue(var);
    }
}
