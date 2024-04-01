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

import org.chocosolver.parser.SetUpException;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.variables.IntVar;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class QAPBench extends BenchParser{

    public QAPInstance qapInstance;
    IntVar[] x;
    IntVar[] fixed;
    public Map<IntVar, Integer> indices;

    public class QAPInstance {
        int maxWeight;
        int maxDist;
        int[][] weight;
        int[][] dist;
        int n;

        public QAPInstance(String filePath) {
            File file = new File(filePath);
            try (Scanner scanner = new Scanner(file)) {
                scanner.useDelimiter("\\s+|\\r\\n|\\n|\\r"); // Use whitespace or any newline as delimiter

                // Read the size of the matrices
                if (scanner.hasNextInt()) {
                    n = scanner.nextInt();
                }

                // Initialize matrices
                weight = new int[n][n];
                dist = new int[n][n];
                maxDist = Integer.MIN_VALUE;
                maxWeight = Integer.MIN_VALUE;

                // Read the flow matrix
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        if (scanner.hasNextInt()) {
                            weight[i][j] = scanner.nextInt();
                            maxWeight = Math.max(maxWeight, weight[i][j]);
                        }
                    }
                }

                // Read the distance matrix
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        if (scanner.hasNextInt()) {
                            dist[i][j] = scanner.nextInt();
                            maxDist = Math.max(maxDist, dist[i][j]);
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                System.err.println("File not found: " + e.getMessage());
            }
        }
    }

    public QAPBench() {
        super();
    }

    @Override
    public boolean setUp(String... args) throws SetUpException {
        boolean setup = super.setUp(args);
        this.qapInstance = new QAPInstance(this.instance);
        return setup;
    }

    /**
     * Opens the facility at the place minimizing the weighted flow with already placed facilities
     * @return
     */
    @Override
    public IntValueSelector makeGreedy() {
        return new Greedy();
    }

    private class Greedy implements IntValueSelector {

        @Override
        public int selectValue(IntVar var) throws ContradictionException {
            int index = indices.get(var);
            // look for the closest successor
            int bestPlace = var.getLB();
            int smallestCost = Integer.MAX_VALUE;
            int ub = var.getUB();
            // collect the fixed variables
            int nFixed = 0;
            for (int i = 0 ; i < x.length; i++) {
                if (x[i].isInstantiated()) {
                    fixed[nFixed++] = x[i];
                }
            }
            if (nFixed == 0)
                return var.getLB();

            for (int v = var.getLB(); v <= ub; v = var.nextValue(v)) {
                int cost = 0;
                for (int i = 0 ; i < nFixed ; i++) {
                    IntVar other = x[i];
                    int otherFacilityPlace = other.getLB();
                    int otherIdx = indices.get(other);
                    int dist = qapInstance.dist[v][otherFacilityPlace];
                    int weight = qapInstance.weight[index][otherIdx];
                    cost += dist * weight;
                }
                if (cost < smallestCost) {
                    bestPlace = v;
                    smallestCost = cost;
                }
            }
            return bestPlace;
        }

    }

    @Override
    public Model makeModel() {
        Model model = new Model("QAP");
        int n = qapInstance.n;
        // VARIABLES
        // For each facility, the index where it is opened
        x = model.intVarArray("x", n, 0, n - 1);
        // For each city, the distance to the succ visited one
        int max = qapInstance.maxDist * qapInstance.maxWeight;
        indices = new HashMap<>();
        fixed = new IntVar[x.length];
        //IntVar[] distances = model.intVarArray("distances", 2*n, 0, max);
        IntVar[] weightedDistances = new IntVar[n*n];
        // Total cost
        if (max * n * n < 0 || max * n * n > Integer.MAX_VALUE / 2) {
            max = Integer.MAX_VALUE / 2;
        } else {
            max = max * n * n;
        }
        IntVar cost = model.intVar("cost", 0, max);

        // CONSTRAINTS
        Tuples tuples = new Tuples(true);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                tuples.add(i, j, qapInstance.dist[i][j]);
            }
            indices.put(x[i], i);
        }
        for (int k = 0, i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                // The Table constraint ensures that one combination holds
                // in a solution
                IntVar distance = model.intVar("dist_" + i + "_" + j, 0, qapInstance.maxDist);
                // distance = dist[x[i]][x[j]]
                model.table(new IntVar[]{x[i], x[j], distance}, tuples).post();
                // cost = dist[x[i]][x[j]] * weight[i][j]
                weightedDistances[k] = model.mul(distance, qapInstance.weight[i][j]);
                k++;
            }
        }
        // Each facility is opened once
        model.allDifferent(x).post();
        // Defining the sum of weighted distances
        model.sum(weightedDistances, "=", cost).post();

        model.setObjective(Model.MINIMIZE, cost);
        return model;
    }

    @Override
    public IntVar[] decisionVars() {
        return x;
    }

    /*
MIN       data/qap/qapdata/tai10a.dat,60,(varsel=DOMWDEG;flushRate=2147483647),(valsel=MIN;best=None;bestFreq=1;last=false),(pol=NONE;cutoff=0;offset=0;geo=1.0;resetOnSolution=false),(obj:194756;t:0.025;nodes:10;fails:0;restarts:0)(obj:177680;t:0.026;nodes:11;fails:0;restarts:0)(obj:175632;t:0.032;nodes:20;fails:8;restarts:0)(obj:171654;t:0.052;nodes:62;fails:47;restarts:0)(obj:171148;t:0.061;nodes:93;fails:77;restarts:0)(obj:169944;t:0.065;nodes:112;fails:93;restarts:0)(obj:169828;t:0.066;nodes:119;fails:98;restarts:0)(obj:169430;t:0.069;nodes:131;fails:109;restarts:0)(obj:167794;t:0.080;nodes:184;fails:159;restarts:0)(obj:166812;t:0.102;nodes:281;fails:255;restarts:0)(obj:164516;t:0.102;nodes:284;fails:256;restarts:0)(obj:162846;t:0.122;nodes:390;fails:361;restarts:0)(obj:162392;t:0.196;nodes:988;fails:956;restarts:0)(obj:159054;t:0.222;nodes:1208;fails:1173;restarts:0)(obj:155022;t:0.223;nodes:1217;fails:1181;restarts:0)(obj:152950;t:0.223;nodes:1223;fails:1185;restarts:0)(obj:151070;t:0.883;nodes:7115;fails:7076;restarts:0)(obj:149450;t:0.972;nodes:7983;fails:7941;restarts:0)(obj:149088;t:1.004;nodes:8303;fails:8258;restarts:0)(obj:148952;t:1.171;nodes:10041;fails:9994;restarts:0)(obj:148942;t:1.527;nodes:13711;fails:13663;restarts:0)(obj:146320;t:1.528;nodes:13717;fails:13668;restarts:0)(obj:145220;t:1.533;nodes:13775;fails:13724;restarts:0)(obj:138130;t:1.534;nodes:13782;fails:13729;restarts:0)(obj:138022;t:25.127;nodes:234465;fails:234410;restarts:0)(obj:135828;t:25.214;nodes:235245;fails:235187;restarts:0)(obj:135028;t:34.811;nodes:321168;fails:321109;restarts:0),false,60.004,545033,544976,0,250.00,218,121,112,-f -varh DOMWDEG -lc 1 -valsel MIN;None;1;false -restarts NONE;0;1.0;0;false -limit 00h01m00s data/qap/qapdata/tai10a.dat
GREEDY data/qap/qapdata/tai10a.dat,60,(varsel=DOMWDEG;flushRate=2147483647),(valsel=GREEDY;best=None;bestFreq=1;last=false),(pol=NONE;cutoff=0;offset=0;geo=1.0;resetOnSolution=false),(obj:187078;t:0.030;nodes:10;fails:0;restarts:0)(obj:186230;t:0.031;nodes:11;fails:0;restarts:0)(obj:182614;t:0.035;nodes:15;fails:2;restarts:0)(obj:175722;t:0.037;nodes:18;fails:3;restarts:0)(obj:156530;t:0.037;nodes:20;fails:3;restarts:0)(obj:153438;t:0.049;nodes:35;fails:15;restarts:0)(obj:149516;t:0.240;nodes:1174;fails:1153;restarts:0)(obj:148472;t:0.563;nodes:3802;fails:3778;restarts:0)(obj:147830;t:0.659;nodes:4616;fails:4591;restarts:0)(obj:147800;t:0.804;nodes:6002;fails:5975;restarts:0)(obj:143710;t:0.804;nodes:6012;fails:5982;restarts:0)(obj:143088;t:1.098;nodes:8804;fails:8772;restarts:0)(obj:140684;t:1.106;nodes:8892;fails:8858;restarts:0)(obj:140232;t:1.107;nodes:8900;fails:8864;restarts:0)(obj:138306;t:2.064;nodes:17876;fails:17838;restarts:0)(obj:138130;t:5.089;nodes:45451;fails:45412;restarts:0)(obj:136722;t:13.123;nodes:116936;fails:116895;restarts:0)(obj:136272;t:13.123;nodes:116941;fails:116898;restarts:0)(obj:135640;t:19.200;nodes:172328;fails:172284;restarts:0),false,60.004,529736,529692,0,250.00,218,121,112,-f -varh DOMWDEG -lc 1 -valsel GREEDY;None;1;false -restarts NONE;0;1.0;0;false -limit 00h01m00s data/qap/qapdata/tai10a.dat
     */
    public static void main(String[] args) {
        //QAPBench bench = new QAPBench();
        mainFrom(args, QAPBench::new);
    }

}
