/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.search;

import org.chocosolver.solver.ICause;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainBest;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainLast;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainReverseBest;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.DomOverWDeg;
import org.chocosolver.solver.search.strategy.selectors.variables.InputOrder;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class TestContradictionInValueSelector {

    private class MinDomButErrorIfMatchingVar implements IntValueSelector, ICause {

        private MinDomButErrorIfMatchingVar(IntVar... vars) {
            forbidden.addAll(Arrays.asList(vars));
        }

        private Set<IntVar> forbidden = new HashSet<>();

        @Override
        public int selectValue(IntVar var) throws ContradictionException {
            System.out.println("branching on " + var);
            if (forbidden.contains(var))
                var.contradiction(this, "");
            return var.getLB();
        }
    }

    @Test(groups = "1s", timeOut = 6000)
    public void testContradictionAfterRoot() {
        Model model = new Model();
        IntVar x0 = model.intVar("x0", 0, 1);
        IntVar x1 = model.intVar("x1", 0, 1);
        Solver solver = model.getSolver();
        solver.setSearch(
                Search.intVarSearch(
                        new InputOrder<>(model),
                        new MinDomButErrorIfMatchingVar(x1),
                        new IntVar[] {x0, x1})
        );
        solver.showShortStatistics();
        while (model.getSolver().solve()) {
        }
        assertFalse(model.getSolver().isStopCriterionMet());
        assertEquals(solver.getFailCount(), 2);
        assertEquals(solver.getSolutionCount(), 0);
        assertEquals(solver.getNodeCount(), 3);
    }

    @Test(groups = "1s", timeOut = 6000)
    public void testContradictionAtRoot() {
        Model model = new Model();
        IntVar x0 = model.intVar("x0", 0, 1);
        IntVar x1 = model.intVar("x1", 0, 1);
        Solver solver = model.getSolver();
        solver.setSearch(
                Search.intVarSearch(
                        new InputOrder<>(model),
                        new MinDomButErrorIfMatchingVar(x0),
                        new IntVar[] {x0, x1})
        );
        solver.showShortStatistics();
        while (model.getSolver().solve()) {
        }
        assertFalse(model.getSolver().isStopCriterionMet());
        assertEquals(solver.getFailCount(), 1);
        assertEquals(solver.getSolutionCount(), 0);
        assertEquals(solver.getNodeCount(), 1);
    }



}
