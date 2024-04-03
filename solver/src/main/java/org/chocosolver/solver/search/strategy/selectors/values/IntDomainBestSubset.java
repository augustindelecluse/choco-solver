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
        pruning = false;
        Model model = var.getModel();
        if (!isConstraintGraphCreated()) {
            createConstraintGraph(model);
        }
        int value;
        // save the environment because some propagators will be set as inactive
        model.getEnvironment().worldPush();
        boolean objectiveReached = subSetToObjective.deactivatePropagatorsOutsideShortestPath(var);
        try {
            if (objectiveReached) {
                value = super.selectValue(var);
            } else {
                nInvalidValuesRemoved = 0;
                value = var.getLB(); // the objective will not change by modifying this variable
            }
        } catch (ContradictionException cex) {
            // reactivate the propagators
            model.getEnvironment().worldPop();
            throw cex;
        }
        // reactivate the propagators
        model.getEnvironment().worldPop();
        // removes the values that were detected as invalid
        for (int idx = 0 ; idx < nInvalidValuesRemoved ; idx++) {
            int val = invalidValuesRemoved[idx];
            dop.unapply(var, val, Cause.Null);
        }
        if (var.getDomainSize() <= 2) {
            // this prevents the search space exploration to not be notified of a change in the domain of the objective variable
            model.getSolver().getEngine().propagate();
        }
        return value;
    }


}
