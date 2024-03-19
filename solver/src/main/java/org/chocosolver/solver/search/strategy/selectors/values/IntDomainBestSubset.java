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
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperator;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.view.IView;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class IntDomainBestSubset extends IntDomainBestPruning {

    private SubSetToObjective subSetToObjective = null;

    public IntDomainBestSubset(int maxdom, IntValueSelector intValueSelector, Function<IntVar, Boolean> trigger,
                               DecisionOperator<IntVar> dop, BiPredicate<IntVar, Integer> condition, boolean pruning) {
        super(maxdom, intValueSelector, trigger, dop, condition, pruning);
    }

    public IntDomainBestSubset() {
        super();
    }

    public IntDomainBestSubset(IntValueSelector intValueSelector, Function<IntVar, Boolean> trigger) {
        super(intValueSelector, trigger);
    }

    private void createConstraintGraph(Model model) {
        subSetToObjective = new SubSetToObjective(model);
    }

    private boolean isConstraintGraphCreated() {
        return subSetToObjective != null;
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
        try {
            // save the environment because some propagators will be set as inactive
            model.getEnvironment().worldPush();
            /*IntVar originalVar = isView(var) ? (IntVar) viewToClosestVariableToObjective.get((IView<?>) var) : var;
            Set<Propagator<?>> valid = constraintsOnShortestPath.get(originalVar);
            // removes the propagators that are not on the shortest path between the variable and the objective
            // TODO check for onVariableUpdate? can be changed there to deactivate the variable,
            //  perhaps better than deactivating the propagators
            for (Propagator<?> propagator: model.getSolver().getEngine().getPropagators()) {
                if (!valid.contains(propagator) && propagator.isActive()) {
                    propagator.setPassive();
                }
            }
             */
            boolean objectiveReached = subSetToObjective.deactivatePropagatorsOutsideShortestPath(var);
            int value;
            if (objectiveReached) {
                value = super.selectValue(var);
            } else {
                nInvalidValuesRemoved = 0;
                value = var.getLB(); // the objective will not change by modifying this variable
            }
            // reactivate the propagators
            model.getEnvironment().worldPop();
            // removes the values that were detected as invalid
            for (int idx = 0 ; idx < nInvalidValuesRemoved ; idx++) {
                int val = invalidValuesRemoved[idx];
                dop.unapply(var, val, Cause.Null);
            }
            return value;
        } catch (ContradictionException cex) {
            // reactivate the propagators
            model.getEnvironment().worldPop();
            throw cex;
        }
    }


}
