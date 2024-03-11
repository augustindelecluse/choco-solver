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

import org.chocosolver.solver.*;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.propagation.PropagationEngine;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.events.PropagatorEventType;
import org.chocosolver.solver.variables.view.IView;

import java.util.*;
import java.util.function.Function;

public class IntDomainBestManual extends IntDomainBestPruning {

    private CompactConstraintNetwork network = null;

    HashSet<Variable> currentVariables = new HashSet<>();
    HashSet<Variable> nextVariables = new HashSet<>();
    HashSet<Propagator<?>> propagated = new HashSet<>();

    public IntDomainBestManual() {
        super();
    }

    public IntDomainBestManual(IntValueSelector intValueSelector, Function<IntVar, Boolean> trigger) {
        super(intValueSelector, trigger);
    }

    private void createConstraintGraph(Model model) {
        network = new CompactConstraintNetwork(model);
    }

    private boolean isConstraintGraphCreated() {
        return network != null;
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
    protected int boundWithPropagate(IntVar var, int val) throws ContradictionException {
        IntVar originalVar = (IntVar) network.getMostRelevantVar(var);
        Model model = var.getModel();
        dop.apply(var, val, Cause.Null);
        PropagationEngine engine = model.getSolver().getEngine();
        var.schedulePropagators(engine);
        CompactConstraintNetwork.VarNode varNode = network.getVarNode(originalVar);
        if (varNode != null) {
            // propagate manually the constraints until reaching the objective
            currentVariables.clear();
            nextVariables.clear();
            propagated.clear();
            currentVariables.add(originalVar);
            while (!currentVariables.isEmpty()) {
                for (Variable child : currentVariables) {
                    Variable childV = network.getMostRelevantVar(child);
                    for (CompactConstraintNetwork.DirectedPropagatorEdge edge : network.getVarNode(childV).parents) {
                        Propagator<?> propagator = edge.propagator;
                        if (propagator.isActive() && propagator.isScheduled() && !propagated.contains(propagator)) {
                            try {
                                engine.execute(propagator);
                            } catch (ContradictionException cex) {
                                model.getSolver().getEngine().flush();
                                throw cex;
                            }
                            nextVariables.add(edge.target);
                            propagated.add(propagator);
                        }
                    }
                }
                // clear currentVariables and swap with nextVariables
                currentVariables.clear();
                HashSet<Variable> tmp = currentVariables;
                currentVariables = nextVariables;
                nextVariables = tmp;
            }
        }
        model.getSolver().getEngine().flush();
        ResolutionPolicy rp = model.getSolver().getObjectiveManager().getPolicy();
        if (rp == ResolutionPolicy.SATISFACTION) {
            return 1;
        } else if (rp == ResolutionPolicy.MINIMIZE) {
            return ((IntVar) model.getObjective()).getLB();
        } else {
            return -((IntVar) model.getObjective()).getUB();
        }
    }
}
