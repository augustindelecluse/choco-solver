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

import org.chocosolver.memory.trailing.trail.chunck.ChunckedIntTrail;
import org.chocosolver.solver.Cause;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;

public class IntDomainBestDichotomy implements IntValueSelector{

    protected IntValueSelector selectOnTies = new IntDomainMin();
    protected int bestLB;
    protected int bestUB;

    @Override
    public int selectValue(IntVar var) throws ContradictionException {
        Model model = var.getModel();
        IntVar objective = ((IntVar) model.getObjective());
        bestLB = objective.getLB();
        bestUB = objective.getUB();
        ResolutionPolicy rp = model.getResolutionPolicy();
        int value = propagate(rp, objective, bestLB, bestUB, var);
        if (rp == ResolutionPolicy.MINIMIZE) {
            objective.updateLowerBound(bestLB, Cause.Null);
        } else {
            objective.updateUpperBound(bestUB, Cause.Null);
        }
        return value;
    }

    protected int propagate(ResolutionPolicy rp, IntVar objective, int lb, int ub, IntVar var) throws ContradictionException {
        Model model = objective.getModel();
        objective.updateLowerBound(lb, Cause.Null);
        objective.updateUpperBound(ub, Cause.Null);
        objective.getModel().getSolver().getEngine().propagate();
        bestLB = Math.max(bestLB, lb);
        bestUB = Math.min(bestUB, ub);
        if (lb == ub) {
            if (var.getDomainSize() == 1) {
                return var.getLB();
            } else {
                return selectOnTies.selectValue(var);
            }
        }
        ContradictionException c = null;
        if (rp == ResolutionPolicy.MINIMIZE) {
            // try first on the left part
            int pivot = lb + (ub - lb) / 2;
            int leftLB = lb;
            int leftUB = pivot;
            int rightLB = pivot + 1;
            int rightUB = ub;
            try {
                model.getEnvironment().worldPush();
                int value = propagate(rp, objective, leftLB, leftUB, var);
                model.getSolver().getEngine().flush();
                model.getEnvironment().worldPop();
                return value;
            } catch (ContradictionException cex) {
                // interval [leftLB..leftUB] is invalid, need to try another one
                model.getSolver().getEngine().flush();
                model.getEnvironment().worldPop();
                c = cex;
            }
            try {
                model.getEnvironment().worldPush();
                int value = propagate(rp, objective, rightLB, rightUB, var);
                model.getSolver().getEngine().flush();
                model.getEnvironment().worldPop();
                return value;
            } catch (ContradictionException cex) {
                // interval [rightLB..rightUB] is invalid, need to try another one
                model.getSolver().getEngine().flush();
                model.getEnvironment().worldPop();
                c = cex;
            }
        }
        throw c;
    }

}
