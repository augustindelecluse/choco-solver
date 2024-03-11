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
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.view.IView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class IntDomainReverseBestSubset extends IntDomainReverseBest {

    private SubSetToObjective subSetToObjective = null;

    public IntDomainReverseBestSubset() {
        this(new IntDomainMin(), v -> true);
    }

    public IntDomainReverseBestSubset(IntValueSelector fallBack, Function<IntVar, Boolean> trigger) {
        super(fallBack, trigger);
    }

    public IntDomainReverseBestSubset(IntValueSelector fallBack) {
        this(fallBack, v -> true);
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
            subSetToObjective.deactivatePropagatorsOutsideShortestPath(var);
            int value = super.selectValue(var);
            // reactivate the propagators
            model.getEnvironment().worldPop();
            // use the lower and upper bounds that were computed
            ((IntVar) model.getObjective()).updateLowerBound(lb, Cause.Null);
            ((IntVar) model.getObjective()).updateUpperBound(ub, Cause.Null);
            return value;
        } catch (ContradictionException cex) {
            // reactivate the propagators
            model.getEnvironment().worldPop();
            throw cex;
        }
    }
}
