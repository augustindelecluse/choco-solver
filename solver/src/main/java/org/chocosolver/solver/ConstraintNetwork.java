package org.chocosolver.solver;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.view.IView;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes a bipartite graph of constraints and variables.
 * Used to find the shortest paths within the graph
 */
public class ConstraintNetwork {

    private Variable objective;
    protected Map<Variable, VariablePath> updateDirections = new HashMap<>();
    private Map<IView<?>, Variable> viewToClosestVariableToObjective;

    protected Set<Variable> processedVariables = new HashSet<>(); // variables already processed
    protected Set<Propagator<?>> processedContraints = new HashSet<>(); // constraints already processed
    protected Queue<Variable> currentVars = new ArrayDeque<>(); // variables to process at the current iteration
    protected Set<Variable> newVars = new HashSet<>(); // new variables to process for the next iteration


    private static class DirectedPropagatorEdge {
        Propagator<?> propagator;
        Variable target;

        private DirectedPropagatorEdge(Propagator propagator, Variable target) {
            this.propagator = propagator;
            this.target = target;
        }
    }

    private static class VariablePath {
        private final List<Set<DirectedPropagatorEdge>> paths = new ArrayList<>();
        private final int distanceToObjective;
        private final Variable originalVariable;

        private VariablePath(Variable variable, int distanceToObjective) {
            assert isConcreteVar(variable);
            for (int i = 0 ; i < distanceToObjective + 1; i++)
                paths.add(new HashSet<>());
            this.distanceToObjective = distanceToObjective;
            this.originalVariable = variable;
        }

        private void addDirectParent(VariablePath parent) {
            // add all the path from the parent except its last one
            for (int i = 0 ; i < parent.distanceToObjective ; i++) {
                paths.get(i).addAll(parent.paths.get(i));
            }
            // from the last path, only store the update requests that are relevant for this variable
            for (DirectedPropagatorEdge edge : parent.paths.get(parent.distanceToObjective)) {
                if (edge.target == originalVariable) {
                    paths.get(parent.distanceToObjective).add(edge);
                }
            }
        }

        private Iterable<DirectedPropagatorEdge> pathAtLevel(int i) {
            return paths.get(i);
        }

        private void addLatestPath(DirectedPropagatorEdge request) {
            paths.get(distanceToObjective).add(request);
        }
    }

    private static boolean isConcreteVar(Variable variable) {
        return (variable.getTypeAndKind() & Variable.VAR) !=0;
    }

    private static boolean isConstant(Variable variable) {
        return (variable.getTypeAndKind() & Variable.CSTE) !=0;
    }

    private static boolean isView(Variable variable) {
        return (variable.getTypeAndKind() & Variable.VIEW) !=0;
    }

    private static Variable[] getConcreteVars(Variable variable) {
        if (variable instanceof IView<?>) {
            Variable[] variables = ((IView<?>) variable).getVariables();
            Set<Variable> vars = new HashSet<>();
            for (Variable var: variables) {
                Variable[] observed = getConcreteVars(var);
                for (Variable obs: observed) {
                    if (isConcreteVar(obs)) {
                        vars.add(var);
                    }
                }
            }
            return (Variable[]) vars.toArray();
        } else {
            return new Variable[] {variable};
        }
    }

    public int hopToObjective(Variable variable) {
        Variable[] vars = getConcreteVars(variable);
        return Arrays.stream(vars).map(v -> updateDirections.get(v).paths.size() - 1).min(Integer::compareTo).get();
    }

    public Iterable<Propagator<?>> constraintsAtDistanceFromObjective(Variable variable, int level) {
        Variable originalVariable;
        if (isView(variable)) {
            originalVariable = viewToClosestVariableToObjective.get((IView<?>) variable);
        } else {
            originalVariable = variable;
        }
        return updateDirections.get(originalVariable).paths.get(level).stream().map(d -> d.propagator).collect(Collectors.toList());
    }

    public Iterable<Variable> variables() {
        return updateDirections.keySet();
    }

    public ConstraintNetwork(Model model) {
        objective = model.getObjective();
        storeShortestPathToObjective();
        setPathForViews();
    }

    private void storeShortestPathToObjective() {
        clearTemporaryDatastructures();
        Variable[] concreteObjective = getConcreteVars(objective);
        for (Variable objectiveVar: concreteObjective) {
            currentVars.add(objectiveVar);
            updateDirections.put(objectiveVar, new VariablePath(objective, 0));
            processedVariables.add(objectiveVar);
        }
        Set<Variable> successors = new HashSet<>();
        int level = 0;
        while (!currentVars.isEmpty()) {
            for (Variable varSource: currentVars) {
                Variable[] originalSourceArray = getConcreteVars(varSource);
                for (Variable originalSource: originalSourceArray) {
                    // for each variable, look at its unprocessed constraints
                    successors.clear();
                    for (Propagator<?> p : originalSource.streamPropagators().collect(Collectors.toList())) {
                        if (!processedContraints.contains(p)) {
                            // for each new discovered constraint
                            processedContraints.add(p);
                            for (Variable variable : p.getVars()) {
                                for (Variable target : getConcreteVars(variable)) {
                                    if (!processedVariables.contains(target) && !isConstant(target)) {
                                        // stores the path for reaching this variable
                                        newVars.add(target);
                                        if (!updateDirections.containsKey(target)) {
                                            updateDirections.put(target, new VariablePath(target, level + 1));
                                        }
                                        updateDirections.get(originalSource).addLatestPath(new DirectedPropagatorEdge(p, target));
                                        successors.add(target);
                                    }
                                }
                            }
                        }
                    }
                    for (Variable succ: successors) { // stores the path for reaching this variable
                        updateDirections.get(succ).addDirectParent(updateDirections.get(originalSource));
                    }
                }
            }
            processedVariables.addAll(newVars);
            currentVars.clear();
            currentVars.addAll(newVars);
            newVars.clear();
            level += 1;
        }
    }

    private void setPathForViews() {
        Model model = objective.getModel();
        for (Variable var: model.getVars()) {
            if (isView(var)) {
                Variable[] concreteVars = getConcreteVars(var);
                int shortestPath = Integer.MAX_VALUE;
                Variable bestVar = null;
                for (Variable concrete: concreteVars) {
                    int distance = hopToObjective(concrete);
                    if (distance < shortestPath) {
                        bestVar = concrete;
                        shortestPath = distance;
                    }
                }
                viewToClosestVariableToObjective.put((IView<?>) var, bestVar);
            }
        }
    }

    private void clearTemporaryDatastructures() {
        newVars.clear();
        currentVars.clear();
        processedContraints.clear();
        processedVariables.clear();
    }

}
