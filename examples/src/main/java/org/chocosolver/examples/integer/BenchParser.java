/*
 * This file is part of examples, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.examples.integer;

import org.chocosolver.parser.Level;
import org.chocosolver.parser.RegParser;
import org.chocosolver.parser.SetUpException;
import org.chocosolver.parser.xcsp.XCSP;
import org.chocosolver.parser.xcsp.XCSPParser;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ParallelPortfolio;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.variables.IntVar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.chocosolver.parser.xcsp.XCSP.isConcreteVar;

public abstract class BenchParser extends RegParser {

    public String[] args;
    /**
     * Create a bench parser
     */
    protected BenchParser() {
        super("Bench");
    }

    /**
     * Creates a greedy search for a specific problem (white-box / problem-dependent selector)
     * @return
     */
    public abstract IntValueSelector makeGreedy();

    @Override
    public Thread actionOnKill() {
        return new Thread(() -> {
            if (userinterruption) {
                finalOutPut(getModel().getSolver());
                if (level.isLoggable(Level.COMPET)) {
                    getModel().getSolver().log().bold().red().print("c Unexpected resolution interruption!");
                }
            }
        });
    }

    @Override
    public boolean setUp(String... args) throws SetUpException {
        this.args = args;
        return super.setUp(args);
    }

    public void declareModel(Model model) {
        portfolio.addModel(model);
    }

    @Override
    public void buildModel() {
        Model model = makeModel();
        declareModel(model);
    }

    private void onSolution(Solver solver) {
        solOverTime.add(new XCSP.SolutionOverTime(
                solver.getTimeCount(),
                solver.getObjectiveManager().getBestSolutionValue().intValue(),
                solver.getNodeCount(),
                solver.getFailCount(),
                solver.getRestartCount()
        ));
    }

    @Override
    protected void singleThread() {
        Model model = portfolio.getModels().get(0);
        boolean enumerate = model.getResolutionPolicy() != ResolutionPolicy.SATISFACTION || all;
        Solver solver = model.getSolver();
        if (level.isLoggable(Level.INFO)) {
            //solver.printShortFeatures();
            getModel().displayVariableOccurrences();
            getModel().displayPropagatorOccurrences();
        }
        if (enumerate) {
            //model.getSolver().solve();
            //try (SearchViz vis = new GraphvizGenerator("output.txt", model.getSolver())) {
            while (model.getSolver().solve()) {
                onSolution(solver);
            }
            //} catch (IOException e) {
            //    throw new RuntimeException(e);
            //}
        } else {
            if (solver.solve()) {
                onSolution(solver);
            }
        }
        userinterruption = false;
        Runtime.getRuntime().removeShutdownHook(statOnKill);
        finalOutPut(solver);
    }

    @Override
    protected void manyThread() {

    }

    @Override
    public void configureSearch() {
        makeSearch(decisionVars());
    }

    /**
     * Gives the model representing the problem
     * @return
     */
    public abstract Model makeModel();

    /**
     * The decision variables used for branching
     * @return
     */
    public abstract IntVar[] decisionVars();

    public void makeSearch(IntVar[] decisionVars) {
        Model model = getModel();
        Solver solver = model.getSolver();
        valsel.setGreedy(m -> makeGreedy());
        AbstractStrategy<IntVar> search = varsel.make().apply(decisionVars, valsel.make().apply(model));
        solver.addRestarter(restarts.make().apply(solver));
        solver.setSearch(search);
        if (limits.getTime() > -1) {
            solver.limitTime(limits.getTime());
        }
        if (limits.getSols() > -1) {
            solver.limitSolution(limits.getSols());
        }
        if (limits.getRuns() > -1) {
            solver.limitRestart(limits.getRuns());
        }
    }

    private List<XCSP.SolutionOverTime> solOverTime = new ArrayList<>();

    private String solOverTimeString() {
        return solOverTime.stream().map(XCSP.SolutionOverTime::toString).collect(Collectors.joining(""));
    }

    protected void finalOutPut(Solver solver) {
        boolean complete = !userinterruption && runInTime();//solver.getSearchState() == SearchState.TERMINATED;
        Runtime runtime = Runtime.getRuntime();
        double allocatedMemory = ((double) runtime.totalMemory()) / (1024*1024);
        int nVars = solver.getModel().getNbVars();
        int nVarsConcrete = Arrays.stream(solver.getModel().getVars()).mapToInt(var -> isConcreteVar(var) ? 1 : 0).sum();
        int nConstraints = solver.getModel().getNbCstrs();
        System.out.printf("%s,%d,%s,%s,%s,%s,%b,%.3f,%d,%d,%d,%.2f,%d,%d,%d,%s%n",
                instance,
                limits.getTime() / 1000,
                varsel,
                valsel,
                restarts,
                solOverTimeString(),
                complete,
                solver.getTimeCount(),
                solver.getNodeCount(),
                solver.getFailCount(),
                solver.getRestartCount(),
                allocatedMemory,
                nVars,
                nVarsConcrete,
                nConstraints,
                Arrays.stream(args).map(s -> s.replace(",", ";")).collect(Collectors.joining(" ")).replace(", ", " "));
    }
}
