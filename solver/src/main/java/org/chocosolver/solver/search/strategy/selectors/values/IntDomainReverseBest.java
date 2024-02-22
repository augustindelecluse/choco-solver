package org.chocosolver.solver.search.strategy.selectors.values;

import org.chocosolver.solver.Cause;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;

public class IntDomainReverseBest implements IntValueSelector {

    private IntValueSelector fallBack;

    @Override
    public int selectValue(IntVar var) throws ContradictionException {
        // fixes the objective to its best bound and then
        // selects the min value on the shrunk domain of the variable
        int best = 0;
        Model model = var.getModel();
        ResolutionPolicy rp = model.getSolver().getObjectiveManager().getPolicy();
        if (rp == ResolutionPolicy.SATISFACTION)
            throw new RuntimeException("IntDomainReverseBest should only be used for optimisation problems");
        model.getEnvironment().worldPush();
        try {
            if (rp == ResolutionPolicy.MINIMIZE) {
                ((IntVar) model.getObjective()).instantiateTo(((IntVar) model.getObjective()).getLB(), Cause.Null);
            } else {
                ((IntVar) model.getObjective()).instantiateTo(((IntVar) model.getObjective()).getUB(), Cause.Null);
            }
        } catch (ContradictionException cex) {
            // failed to assign to the value of the bound
            model.getEnvironment().worldPop();
            //throw cex; // TODO throw exception
        }
        // trigger the fixpoint and pick the best value for the variable
        try {
            model.getSolver().getEngine().propagate();
            // TODO use relaxed fixpoint instead
            best = fallBack.selectValue(var); // pick the value
            model.getEnvironment().worldPop(); // backtrack
            return best;
        } catch (ContradictionException cex) {
            model.getEnvironment().worldPop();
            // the lower bound was not good enough, tighten it
            if (rp == ResolutionPolicy.MINIMIZE) {
                ((IntVar) model.getObjective()).removeValue(((IntVar) model.getObjective()).getLB(), Cause.Null);
            } else {
                ((IntVar) model.getObjective()).removeValue(((IntVar) model.getObjective()).getUB(), Cause.Null);
            }
        }
        return best;
    }
}
