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

    public IntDomainReverseBestSubset(Model model) {
        this(model, new IntDomainMin(), v -> true);
    }

    public IntDomainReverseBestSubset(Model model, IntValueSelector fallBack, Function<IntVar, Boolean> trigger) {
        super(model, fallBack, trigger);
    }

    public IntDomainReverseBestSubset(Model model, IntValueSelector fallBack) {
        this(model, fallBack, v -> true);
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
        // save the environment because some propagators will be set as inactive
        model.getEnvironment().worldPush();
        boolean canReachVariable = subSetToObjective.deactivatePropagatorsOutsideShortestPath(var);
        int value;
        try {
            // select the best value
            if (canReachVariable)
                value = super.selectValue(var);
            else
                value = var.getLB();
        } catch (ContradictionException cex) {
            // reactivate the propagators and give the error
            model.getEnvironment().worldPop();
            throw cex;
        }
        // remember the bounds deduced by the super class
        int lb = ((IntVar) model.getObjective()).getLB();
        int ub = ((IntVar) model.getObjective()).getUB();
        // reactivate the propagators
        model.getEnvironment().worldPop();
        // put back the bounds deduced by the super class
        ((IntVar) model.getObjective()).updateLowerBound(lb, Cause.Null);
        ((IntVar) model.getObjective()).updateUpperBound(ub, Cause.Null);
        if (model.getObjective().getDomainSize() <= 2) {
            // this prevents the search space exploration to not be notified of a change in the domain of the objective variable
            model.getSolver().getEngine().propagate();
        }
        return value;
    }
}
