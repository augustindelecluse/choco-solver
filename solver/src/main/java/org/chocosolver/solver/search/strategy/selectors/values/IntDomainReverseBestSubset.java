package org.chocosolver.solver.search.strategy.selectors.values;

import org.chocosolver.solver.Cause;
import org.chocosolver.solver.ConstraintNetwork;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IntDomainReverseBestSubset extends IntDomainReverseBest {

    protected Map<Variable, Set<Propagator<?>>> constraintsOnShortestPath = new HashMap<>();

    public IntDomainReverseBestSubset() {
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
            model.getEnvironment().worldPush();
            Set<Propagator<?>> valid = constraintsOnShortestPath.get(var);
            // removes the propagators that are not on the shortest path between the variable and the objective
            for (Propagator<?> propagator: model.getSolver().getEngine().getPropagators()) {
                if (!valid.contains(propagator) && propagator.isActive()) {
                    propagator.setPassive();
                }
            }
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
