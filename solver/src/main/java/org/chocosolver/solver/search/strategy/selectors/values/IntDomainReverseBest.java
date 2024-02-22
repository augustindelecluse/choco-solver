package org.chocosolver.solver.search.strategy.selectors.values;

import org.chocosolver.solver.Cause;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;

public class IntDomainReverseBest implements IntValueSelector {

    private final IntValueSelector fallbackValueSelector;

    public IntDomainReverseBest(IntValueSelector fallBack) {
        this.fallbackValueSelector = fallBack;
    }

    public IntDomainReverseBest() {
        this(new IntDomainMin());
    }

    @Override
    public int selectValue(IntVar var) throws ContradictionException {
        // fixes the objective to its best bound and then
        // selects the min value on the shrunk domain of the variable
        Model model = var.getModel();
        ResolutionPolicy rp = model.getSolver().getObjectiveManager().getPolicy();
        if (rp == ResolutionPolicy.SATISFACTION)
            throw new RuntimeException("IntDomainReverseBest should only be used for optimisation problems");
        while (true) {
            model.getEnvironment().worldPush(); // save the state
            try {
                if (rp == ResolutionPolicy.MINIMIZE) { // fix to the lower bound
                    ((IntVar) model.getObjective()).instantiateTo(((IntVar) model.getObjective()).getLB(), Cause.Null);
                } else { // fix to the upper bound
                    ((IntVar) model.getObjective()).instantiateTo(((IntVar) model.getObjective()).getUB(), Cause.Null);
                }
                // trigger the fixpoint and pick the best value for the variable
                model.getSolver().getEngine().propagate();
                // TODO use relaxed fixpoint instead
                int best = fallbackValueSelector.selectValue(var); // pick the value
                model.getEnvironment().worldPop(); // backtrack
                return best;
            } catch (ContradictionException cex) {
                model.getEnvironment().worldPop();
                // failed to compute a valid value when assigning to the best bound
                // the bound was not good enough, tighten it and do it again
                // if the domain becomes emptied, a contradiction is thrown, caught at the dfs level and
                // a backtrack will occur
                if (rp == ResolutionPolicy.MINIMIZE) {
                    ((IntVar) model.getObjective()).removeValue(((IntVar) model.getObjective()).getLB(), Cause.Null);
                } else {
                    ((IntVar) model.getObjective()).removeValue(((IntVar) model.getObjective()).getUB(), Cause.Null);
                }
            }
        }
    }
}
