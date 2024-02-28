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

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class IntDomainBestSubset extends IntDomainBestPruning {

    protected Map<Variable, Set<Propagator<?>>> constraintsOnShortestPath = new HashMap<>();

    public IntDomainBestSubset(int maxdom, IntValueSelector intValueSelector, Function<IntVar, Boolean> trigger,
                               DecisionOperator<IntVar> dop, BiPredicate<IntVar, Integer> condition, boolean pruning) {
        super(maxdom, intValueSelector, trigger, dop, condition, pruning);
    }

    public IntDomainBestSubset() {
        super();
    }

    private void createConstraintGraph(Model model) {
        ConstraintNetwork constraintGraph = new ConstraintNetwork(model);
        for (Variable var: constraintGraph.variables()) {
            constraintsOnShortestPath.put(var, new HashSet<>());
            int distToObjective = constraintGraph.hopToObjective(var);
            for (int depth = distToObjective - 1 ; depth >= 0  ; depth--) {
                for (Propagator<?> propagator: constraintGraph.constraintsAtDistanceFromObjective(var, depth)) {
                    constraintsOnShortestPath.get(var).add(propagator);
                }
            }
        }
    }

    private boolean isConstraintGraphCreated() {
        return !constraintsOnShortestPath.isEmpty();
    }

    @Override
    public int selectValue(IntVar var) throws ContradictionException {
        Model model = var.getModel();
        if (!isConstraintGraphCreated()) {
            createConstraintGraph(model);
        }
        try {
            // save the environment because some propagators will be set as inactive
            // TODO this has the side effect of deactivating the pruning
            model.getEnvironment().worldPush();
            Set<Propagator<?>> valid = constraintsOnShortestPath.get(var);
            // removes the propagators that are not on the shortest path between the variable and the objective
            // TODO check for onVariableUpdate? can be changed there to deactivate the variable,
            //  perhaps better than deactivating the propagators
            for (Propagator<?> propagator: model.getSolver().getEngine().getPropagators()) {
                if (!valid.contains(propagator) && propagator.isActive()) {
                    propagator.setPassive();
                }
            }
            int value = super.selectValue(var);
            // reactivate the propagators
            model.getEnvironment().worldPop();
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
