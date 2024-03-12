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
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction;
import org.chocosolver.solver.search.loop.monitors.IMonitorSolution;
import org.chocosolver.solver.variables.IntVar;

import java.util.function.Function;

public class IntDomainReverseBest implements IntValueSelector, IMonitorSolution, IMonitorContradiction {

    protected Model model;
    protected final IntValueSelector fallbackValueSelector;
    protected int lb;
    protected int ub;
    private final Function<IntVar, Boolean> trigger;
    // TODO increment value of delta after X backtrack without finding a solution
    //  and reset to 1 if a solution has just been found
    private int initDelta = 1;
    private int maxDelta = -1;
    private int nFailuresWithoutSol = 0;
    private int failureWithoutSolLimit = 10_000;


    public IntDomainReverseBest(Model model, IntValueSelector fallBack, Function<IntVar, Boolean> trigger) {
        this.model = model;
        this.fallbackValueSelector = fallBack;
        this.trigger = trigger;
    }

    public IntDomainReverseBest(Model model) {
        this(model, new IntDomainMin(), v -> true);
    }

    @Override
    public int selectValue(IntVar var) throws ContradictionException {
        // fixes the objective to its best bound and then
        // selects the min value on the shrunk domain of the variable
        if (!trigger.apply(var)) {
            return fallbackValueSelector.selectValue(var);
        }
        Model model = var.getModel();
        ResolutionPolicy rp = model.getSolver().getObjectiveManager().getPolicy();
        if (rp == ResolutionPolicy.SATISFACTION)
            throw new RuntimeException("IntDomainReverseBest should only be used for optimisation problems");
        int delta = initDelta;
        IntVar objective = (IntVar) model.getObjective();
        lb = objective.getLB();
        ub = objective.getUB();
        while (true) {
            model.getEnvironment().worldPush(); // save the state
            try {
                /*
                Test incrementally with more and more values available for the objective. If minimization:
                iter 0: objective is {lb}
                iter 1: objective is {lb+1, lb+2}
                iter 1: objective is {lb+3, lb+4, lb+5, lb+6}
                etc.
                 */
                if (rp == ResolutionPolicy.MINIMIZE) { // fix to the lower bound
                    objective.updateUpperBound(boundWithDelta(objective, rp, delta), Cause.Null);
                } else { // fix to the upper bound
                    objective.updateLowerBound(boundWithDelta(objective, rp, delta), Cause.Null);
                }
                // trigger the fixpoint and pick the best value for the variable
                int best = selectWithPropagate(var);
                model.getSolver().getEngine().flush();
                model.getEnvironment().worldPop(); // backtrack
                lb = objective.getLB();
                ub = objective.getUB();
                return best;
            } catch (ContradictionException cex) {
                model.getSolver().getEngine().flush();
                model.getEnvironment().worldPop();
                // failed to compute a valid value when assigning to the best bound
                // the bound was not good enough, tighten it and do it again
                // if the domain becomes emptied, a contradiction is thrown, caught at the dfs level and
                // a backtrack will occur
                if (rp == ResolutionPolicy.MINIMIZE) {
                    objective.updateLowerBound(boundWithDelta(objective, rp, delta) + 1, Cause.Null);
                } else {
                    objective.updateUpperBound(boundWithDelta(objective, rp, delta) - 1, Cause.Null);
                }
                delta *= 2;
            }
        }
    }

    public int boundWithDelta(IntVar var, ResolutionPolicy rp, int delta) {
        if (rp == ResolutionPolicy.MINIMIZE) {
            return var.getLB() - 1 + delta;
        } else {
            return var.getUB() + 1 - delta;
        }
    }

    protected int selectWithPropagate(IntVar variableToSelect) throws ContradictionException {
        // TODO use relaxed fixpoint instead
        // TODO check with calls to variable.swapOnPassivate() to deactivate propagators
        variableToSelect.getModel().getSolver().getEngine().propagate();
        if (variableToSelect.getDomainSize() == 1)
            return variableToSelect.getLB();
        return fallbackValueSelector.selectValue(variableToSelect); // pick the value
    }


    @Override
    public boolean init() {
        maxDelta = model.getObjective().getDomainSize();
        if (!model.getSolver().getSearchMonitors().contains(this)) {
            model.getSolver().plugMonitor(this);
        }
        return fallbackValueSelector.init();
    }

    @Override
    public void onContradiction(ContradictionException cex) {
        nFailuresWithoutSol += 1;
        if (nFailuresWithoutSol % failureWithoutSolLimit == 0) {
            if (initDelta < maxDelta) {
                initDelta *= 2;
                initDelta = Math.min(initDelta, maxDelta);
                //System.out.println("increased delta to " + initDelta);
            }
        }
    }

    @Override
    public void onSolution() {
        nFailuresWithoutSol = 0;
        initDelta = 1;
        //System.out.println("reset delta to " + initDelta);
    }
}
