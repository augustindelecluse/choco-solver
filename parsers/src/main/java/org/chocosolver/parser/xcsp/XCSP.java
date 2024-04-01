/*
 * This file is part of choco-parsers, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.parser.xcsp;

import org.chocosolver.parser.Level;
import org.chocosolver.parser.RegParser;
import org.chocosolver.parser.SetUpException;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.BlackBoxConfigurator;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.SearchParams;
import org.chocosolver.solver.variables.Variable;
import org.kohsuke.args4j.Option;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Created by cprudhom on 01/09/15.
 * Project: choco-parsers.
 */
public class XCSP extends RegParser {

    // Contains mapping with variables and output prints
    public XCSPParser[] parsers;
    public String[] args;

    @SuppressWarnings("FieldMayBeFinal")
    @Option(name = "-cs", usage = "set to true to check solution with org.xcsp.checker.SolutionChecker")
    private boolean cs = false;

    @SuppressWarnings("FieldMayBeFinal")
    @Option(name = "-flt")
    private boolean flatten = false;

    /**
     * Needed to print the last solution found
     */
    private final StringBuilder output = new StringBuilder();

    public XCSP() {
        super("ChocoXCSP");
    }

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
    public void createSolver() {
        super.createSolver();
        //if (level.isLoggable(Level.COMPET)) {
        //    System.out.println("c Choco 231102");
        //}
        String iname = Paths.get(instance).getFileName().toString();
        parsers = new XCSPParser[nb_cores];
        for (int i = 0; i < nb_cores; i++) {
            Model threadModel = new Model(iname + "_" + (i + 1), defaultSettings);
            threadModel.getSolver().logWithANSI(ansi);
            portfolio.addModel(threadModel);
            parsers[i] = new XCSPParser();
        }
    }

    @Override
    public void buildModel() {
        List<Model> models = portfolio.getModels();
        for (int i = 0; i < models.size(); i++) {
            Model m = models.get(i);
            Solver s = m.getSolver();
            try {
                long ptime = -System.currentTimeMillis();
                parse(m, parsers[i]);
                if (logFilePath != null) {
                    s.log().remove(System.out);
                    s.log().add(new PrintStream(Files.newOutputStream(Paths.get(logFilePath)), true));
                } else {
                    s.logWithANSI(ansi);
                }
                if (level.isLoggable(Level.INFO)) {
                    s.log().white().printf("File parsed in %d ms%n", (ptime + System.currentTimeMillis()));
                }
                if (level.is(Level.JSON)) {
                    s.getMeasures().setReadingTimeCount(System.nanoTime() - s.getModel().getCreationTime());
                    s.log().printf(Locale.US,
                            "{\t\"name\":\"%s\",\n" +
                                    "\t\"variables\": %d,\n" +
                                    "\t\"constraints\": %d,\n" +
                                    "\t\"policy\": \"%s\",\n" +
                                    "\t\"parsing time\": %.3f,\n" +
                                    "\t\"building time\": %.3f,\n" +
                                    "\t\"memory\": %d,\n" +
                                    "\t\"stats\":[",
                            instance,
                            m.getNbVars(),
                            m.getNbCstrs(),
                            m.getSolver().getObjectiveManager().getPolicy(),
                            (ptime + System.currentTimeMillis()) / 1000f,
                            s.getReadingTimeCount(),
                            m.getEstimatedMemory()
                    );
                }
            } catch (Exception e) {
                if (level.isLoggable(Level.INFO)) {
                    s.log().red().print("s UNSUPPORTED\n");
                    s.log().printf("c %s\n", e.getMessage());
                }
                e.printStackTrace();
                throw new RuntimeException("UNSUPPORTED");
            }
        }
    }

    public void parse(Model target, XCSPParser parser) throws Exception {
        parser.model(target, instance);
        // and define a search strategy
        freesearch(target.getSolver());
    }


    @Override
    public void freesearch(Solver solver) {
        BlackBoxConfigurator bb = BlackBoxConfigurator.init();
        boolean isOpt = solver.getObjectiveManager().isOptimization();
        SearchParams.BestSelection opt = isOpt ? SearchParams.BestSelection.BEST : SearchParams.BestSelection.NONE;
        final SearchParams.ValSelConf defaultValSel;
        final SearchParams.VarSelConf defaultVarSel;
        final SearchParams.ResConf defaultResConf;
        if (free) {
            defaultValSel = valsel;
            defaultVarSel = varsel;
            defaultResConf = restarts;
            bb.setNogoodOnRestart(true)
                    .setRestartOnSolution(true)
                    .setExcludeObjective(true)
                    .setExcludeViews(false)
                    .setMetaStrategy(
                            lc > 0 ? m -> Search.lastConflict(m, lc) :
                                    cos ? Search::conflictOrderingSearch :
                                            m -> m);
        } else {
            // variable selection
            defaultValSel = new SearchParams.ValSelConf(
                    SearchParams.ValueSelection.MIN, opt, 1, isOpt);
            defaultVarSel = new SearchParams.VarSelConf(
                    SearchParams.VariableSelection.DOMWDEG, Integer.MAX_VALUE);
            // restart policy
            defaultResConf = new SearchParams.ResConf(
                    SearchParams.Restart.LUBY, 500, 50_000, true);
            // other parameters
            bb.setNogoodOnRestart(true)
                    .setRestartOnSolution(true)
                    .setExcludeObjective(true)
                    .setExcludeViews(false)
                    .setMetaStrategy(m -> Search.lastConflict(m, 1));
        }
        valsel = defaultValSel;
        varsel = defaultVarSel;
        restarts = defaultResConf;
        //System.out.println("for freeSearch " + defaultValSel);
        //System.out.println("for freeSearch " + defaultVarSel);
        bb.setIntVarStrategy((vars) -> defaultVarSel.make().apply(vars, defaultValSel.make().apply(vars[0].getModel())));
        bb.setRestartPolicy(defaultResConf.make());

        if (level.isLoggable(Level.INFO)) {
            solver.log().println(bb.toString());
        }
        bb.complete(solver.getModel(), solver.getSearch());
    }

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
                onSolution(solver, parsers[0]);
            }
            //} catch (IOException e) {
            //    throw new RuntimeException(e);
            //}
        } else {
            if (solver.solve()) {
                onSolution(solver, parsers[0]);
            }
        }
        userinterruption = false;
        Runtime.getRuntime().removeShutdownHook(statOnKill);
        finalOutPut(solver);
    }

    protected void manyThread() {
        boolean enumerate = portfolio.getModels().get(0).getResolutionPolicy() != ResolutionPolicy.SATISFACTION || all;
        if (enumerate) {
            while (portfolio.solve()) {
                onSolution(getModel().getSolver(), parsers[bestModelID()]);
            }
        } else {
            if (portfolio.solve()) {
                onSolution(getModel().getSolver(), parsers[bestModelID()]);
            }
        }
        userinterruption = false;
        Runtime.getRuntime().removeShutdownHook(statOnKill);
        finalOutPut(getModel().getSolver());
    }

    private List<SolutionOverTime> solOverTime = new ArrayList<>();

    public static class SolutionOverTime {

        private double timeS;
        private int objective;
        private long nodes;
        private long failures;
        private long restarts;

        public SolutionOverTime(double timeS, int objective, long nodes, long failures, long restarts) {
            this.timeS = timeS;
            this.objective = objective;
            this.nodes = nodes;
            this.failures = failures;
            this.restarts = restarts;
        }

        @Override
        public String toString() {
            return String.format("(obj:%d;t:%.3f;nodes:%d;fails:%d;restarts:%d)",
                    objective,
                    timeS,
                    nodes,
                    failures,
                    restarts);
        }
    }


    private void onSolution(Solver solver, XCSPParser parser) {
        solOverTime.add(new SolutionOverTime(
                solver.getTimeCount(),
                solver.getObjectiveManager().getBestSolutionValue().intValue(),
                solver.getNodeCount(),
                solver.getFailCount(),
                solver.getRestartCount()
                ));
        /*
        output.setLength(0);
        output.append(parser.printSolution(!flatten));
        if (solver.getObjectiveManager().isOptimization()) {
            if (level.isLoggable(Level.COMPET) || level.is(Level.RESANA)) {
                solver.log().printf(java.util.Locale.US, "o %d %.1f\n",
                        solver.getObjectiveManager().getBestSolutionValue().intValue(),
                        solver.getTimeCount());
            }
            if (level.is(Level.JSON)) {
                solver.log().printf(Locale.US, "%s\n\t\t{\"bound\":%d, \"time\":%.1f, " +
                                "\"solutions\":%d, \"nodes\":%d, \"failures\":%d, \"restarts\":%d}",
                        solver.getSolutionCount() > 1 ? "," : "",
                        solver.getObjectiveManager().getBestSolutionValue().intValue(),
                        solver.getTimeCount(),
                        solver.getSolutionCount(),
                        solver.getNodeCount(),
                        solver.getFailCount(),
                        solver.getRestartCount());
            }
        } else {
            if (level.isLoggable(Level.COMPET)) {
                solver.log().println(output.toString());
            }
            if (level.is(Level.JSON)) {
                solver.log().printf(Locale.US, "\t\t{\"time\":%.1f," +
                                "\"solutions\":%d, \"nodes\":%d, \"failures\":%d, \"restarts\":%d}",
                        solver.getTimeCount(),
                        solver.getSolutionCount(),
                        solver.getNodeCount(),
                        solver.getFailCount(),
                        solver.getRestartCount());
            }
        }

        if (level.isLoggable(Level.INFO)) {
            solver.log().white().printf("%s %n", solver.getMeasures().toOneLineString());
        }
        if (cs) {
            try {
                output.insert(0, "s SATISFIABLE\n");
                new SolutionChecker(true, instance, new ByteArrayInputStream(output.toString().getBytes()));
            } catch (Exception e) {
                throw new RuntimeException("wrong solution found twice");
            }
        }

         */
    }

    @Override
    public boolean setUp(String... args) throws SetUpException {
        this.args = args;
        return super.setUp(args);
    }

    private String solOverTimeString() {
        return solOverTime.stream().map(SolutionOverTime::toString).collect(Collectors.joining(""));
    }

    public static boolean isConcreteVar(Variable var) {
        return (var.getTypeAndKind() & Variable.VAR) !=0;
    }

    private void finalOutPut(Solver solver) {
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
        /*
        Logger log = solver.log().bold();
        if (solver.getSolutionCount() > 0) {
            log = log.green();
            if (solver.getObjectiveManager().isOptimization() && complete) {
                output.insert(0, "s OPTIMUM FOUND\n");
            } else {
                output.insert(0, "s SATISFIABLE\n");
            }
        } else if (complete) {
            output.insert(0, "s UNSATISFIABLE\n");
            log = log.red();
        } else {
            output.insert(0, "s UNKNOWN\n");
            log = log.black();
        }
        if (level.isLoggable(Level.COMPET)) {
            output.append("d FOUND SOLUTIONS ").append(solver.getSolutionCount()).append("\n");
            log.println(output.toString());
        }
        log.reset();
        if (level.is(Level.RESANA)) {
            solver.log().printf(java.util.Locale.US, "s %s %.1f\n",
                    complete ? "T" : "S",
                    solver.getTimeCount());
        }
        if (level.is(Level.JSON)) {
            solver.log().printf(Locale.US, "\n\t],\n\t\"exit\":{\"time\":%.1f, " +
                            "\"bound\":%d, \"nodes\":%d, \"failures\":%d, \"restarts\":%d, \"status\":\"%s\"}\n}",
                    solver.getTimeCount(),
                    solver.getObjectiveManager().isOptimization() ?
                            solver.getObjectiveManager().getBestSolutionValue().intValue() :
                            solver.getSolutionCount(),
                    solver.getNodeCount(),
                    solver.getFailCount(),
                    solver.getRestartCount(),
                    solver.getSearchState()
            );
        }
        if (level.is(Level.IRACE)) {
            solver.log().printf(Locale.US, "%d %d",
                    solver.getObjectiveManager().isOptimization() ?
                            (solver.getObjectiveManager().getPolicy().equals(ResolutionPolicy.MAXIMIZE) ? -1 : 1)
                                    * solver.getObjectiveManager().getBestSolutionValue().intValue() :
                            -solver.getSolutionCount(),
                    complete ?
                            (int) Math.ceil(solver.getTimeCount()) :
                            Integer.MAX_VALUE);
        }
        if (level.isLoggable(Level.INFO)) {
            solver.log().bold().white().printf("%s \n", solver.getMeasures().toOneLineString());
        }
        if (csv) {
            solver.printCSVStatistics();
        }
        if (cs) {
            try {
                new SolutionChecker(true, instance, new ByteArrayInputStream(output.toString().getBytes()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

         */
    }
}
