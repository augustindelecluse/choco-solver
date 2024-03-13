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
import org.chocosolver.solver.search.strategy.assignments.DecisionOperator;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory;
import org.chocosolver.solver.variables.IntVar;

import java.util.function.BiPredicate;
import java.util.function.Function;

import static org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory.makeIntEq;

public class IntDomainBestPruning implements IntValueSelector {

    /**
     * Maximum enumerated domain size this selector falls into.
     * Otherwise, only bounds are considered.
     */
    private final int maxdom;

    /**
     * The decision operator used to make the decision
     */
    protected final DecisionOperator<IntVar> dop;

    /**
     * Condition for tie breaking
     */
    private final BiPredicate<IntVar, Integer> condition;

    private final IntValueSelector fallbackValueSelector;

    private final Function<IntVar, Boolean> trigger;
    private final boolean pruning;   // TODO pruning activation seems to prevent the triggering of some constraints
    protected final int[] invalidValuesRemoved;
    protected int nInvalidValuesRemoved = 0;

    /**
     * Create a value selector that returns the best value wrt to the objective to optimize.
     * When an enumerated variable domain exceeds {@link #maxdom}, only bounds are considered.
     *
     * <p>
     * {@code condition} is called when the evaluated {@code value} returns a score
     * equals to the current best one. In that case, if {@code condition} returns {@code true}
     * then {@code value} is retained as the new best candidate, otherwise the previous one
     * is kept.
     * </p>
     *
     * @param maxdom           a maximum domain size to satisfy to use this value selector
     * @param intValueSelector fallback value selector
     * @param trigger          the function that indicates when the best value selector is applied.
     *                         When it returns true, the best value selector is applied.
     *                         Otherwise, the fallback value selector is applied.
     * @param dop              the decision operator used to make the decision
     * @param condition        predicate to break ties
     * @param pruning          if true, removes a value if it is found invalid
     */
    public IntDomainBestPruning(int maxdom, IntValueSelector intValueSelector, Function<IntVar, Boolean> trigger,
                                DecisionOperator<IntVar> dop, BiPredicate<IntVar, Integer> condition, boolean pruning) {
        this.maxdom = maxdom;
        this.dop = dop;
        this.condition = condition;
        this.fallbackValueSelector = intValueSelector;
        this.trigger = trigger;
        this.pruning = pruning;
        invalidValuesRemoved = new int[maxdom];
    }

    /**
     * Create a value selector that returns the best value wrt to the objective to optimize.
     * When an enumerated variable domain exceeds {@link #maxdom}, only bounds are considered.
     *
     * <p>
     * {@code condition} is called when the evaluated {@code value} returns a score
     * equals to the current best one. In that case, if {@code condition} returns {@code true}
     * then {@code value} is retained as the new best candidate, otherwise the previous one
     * is kept.
     * </p>
     *
     * @param intValueSelector fallback value selector
     * @param trigger          the function that indicates when the best value selector is applied.
     *                         When it returns true, the best value selector is applied.
     *                         Otherwise, the fallback value selector is applied.
     */
    public IntDomainBestPruning(IntValueSelector intValueSelector, Function<IntVar, Boolean> trigger) {
        this(100, intValueSelector, trigger, makeIntEq(), (k, v) -> false, true);
    }

    /**
     * Create a value selector for assignments that returns the best value wrt to the objective to
     * optimize. When an enumerated variable domain exceeds 100, only bounds are considered.
     *
     * <p>
     * {@code condition} is called when the evaluated {@code value} returns a score
     * equals to the current best one. In that case, if {@code condition} returns {@code true}
     * then {@code value} is retained as the new best candidate, otherwise the previous one
     * is kept.
     * </p>
     *
     * @param condition predicate to break ties
     * @apiNote The default values are:
     * <ul>
     *     <li>maxdom is set to 100</li>
     *     <li>the trigger is set to restart count % 16 == 0</li>
     *     <li>the decision operator is set to '='</li>
     * </ul>
     */
    public IntDomainBestPruning(BiPredicate<IntVar, Integer> condition) {
        this(100,
                new IntDomainMin(),
                v -> true,
                makeIntEq(),
                condition,
                true);
    }


    /**
     * Create a value selector for assignments that returns the best value wrt to the objective to
     * optimize. When an enumerated variable domain exceeds 100, only bounds are considered.
     * Always-false condition is set by default.
     *
     * @apiNote The default values are:
     * <ul>
     *     <li>maxdom is set to 100</li>
     *     <li>the trigger is set to restart count % 16 == 0</li>
     *     <li>the decision operator is set to '='</li>
     *     <li>the predicate to break ties is lexico</li>
     * </ul>
     */
    public IntDomainBestPruning() {
        this(100,
                new IntDomainMin(),
                v -> true,
                makeIntEq(),
                (k, v) -> false,
                true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int selectValue(IntVar var) throws ContradictionException {
        if (var.getDomainSize() == 1)
            return var.getLB();
        if (!trigger.apply(var)) {
            return fallbackValueSelector.selectValue(var);
        }
        assert var.getModel().getObjective() != null;
        nInvalidValuesRemoved = 0;
        int bestV;
        if (var.hasEnumeratedDomain() && var.getDomainSize() < maxdom) {
            int bestCost = Integer.MAX_VALUE;
            int ub = var.getUB();
            // if decision is '<=', default value is LB, UB in any other cases
            bestV = dop == DecisionOperatorFactory.makeIntReverseSplit() ? ub : var.getLB();
            for (int v = var.getLB(); v <= ub; v = var.nextValue(v)) {
                int bound = bound(var, v, true);
                if (bound < bestCost || (bound == bestCost && condition.test(var, v))) {
                    bestCost = bound;
                    bestV = v;
                }
            }
        } else {
            int lbB = bound(var, var.getLB(), false);
            int ubB = bound(var, var.getUB(), false);
            // if values are equivalent
            if (lbB == ubB) {
                // if decision is '<=', default value is LB, UB in any other cases
                bestV = dop == DecisionOperatorFactory.makeIntReverseSplit() ? var.getUB() : var.getLB();
            } else {
                bestV = lbB < ubB ? var.getLB() : var.getUB();
            }
        }
        if (pruning && nInvalidValuesRemoved > 0) {
            for (int i = 0 ; i < nInvalidValuesRemoved ; i++) {
                int val = invalidValuesRemoved[i];
                dop.unapply(var, val, Cause.Null);
            }
            // the left branch will be x = v and the right branch x != v
            //if (var.getDomainSize() <= 2) {
                // ensure that the fixpoint will be computed on both branches, in some cases it can be bypassed
                var.getModel().getSolver().getEngine().propagate();
            //} else {
                // the fixpoint will be computed correctly with both x = v and x != v
            //    var.getModel().getSolver().getEngine().flush();
            //}
        }
        return bestV;
    }

    protected int bound(IntVar var, int val, boolean removeIfInvalid) throws ContradictionException {
        Model model = var.getModel();
        int cost;
        // // if decision is '<=' ('>='), UB (LB) should be ignored to avoid infinite loop
        if (dop == DecisionOperatorFactory.makeIntSplit() && val == var.getUB()
                || dop == DecisionOperatorFactory.makeIntReverseSplit() && val == var.getLB()) {
            return Integer.MAX_VALUE;
        }
        model.getEnvironment().worldPush();
        boolean valid = true;
        try {
            cost = boundWithPropagate(var, val);
        } catch (ContradictionException cex) {
            cost = Integer.MAX_VALUE;
            valid = false;
        }
        model.getSolver().getEngine().flush();
        model.getEnvironment().worldPop();
        if (pruning && !valid && removeIfInvalid) {
            // removes the value if the operation failed
            //dop.unapply(var, val, Cause.Null);
            invalidValuesRemoved[nInvalidValuesRemoved++] = val;
        }
        return cost;
    }

    protected int boundWithPropagate(IntVar var, int val) throws ContradictionException {
        Model model = var.getModel();
        dop.apply(var, val, Cause.Null);
        model.getSolver().getEngine().propagate();
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
