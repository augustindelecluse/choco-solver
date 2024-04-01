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
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMin;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.search.strategy.strategy.StrategiesSequencer;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Task;
import org.chocosolver.solver.variables.Variable;

import java.io.*;
import java.util.*;
import java.util.stream.IntStream;

public class JobShopBench extends BenchParser {

    public JobShopBench() {
        super();
    }

    public class JobShopInstance {
        int [][] duration;
        int [][] machine;
        int horizon;
        int nJobs;
        int nMachines;

        /**
         * Read the job-shop instance from the specified file
         * @param file
         */
        public JobShopInstance(String file) {
            try {
                FileInputStream istream = new FileInputStream(file);
                BufferedReader in = new BufferedReader(new InputStreamReader(istream));
                String line = in.readLine();
                while (line.charAt(0) == '#') {
                    line = in.readLine();
                }
                StringTokenizer tokenizer = new StringTokenizer(line);
                nJobs = Integer.parseInt(tokenizer.nextToken());
                nMachines = Integer.parseInt(tokenizer.nextToken());

                duration = new int[nJobs][nMachines];
                machine = new int[nJobs][nMachines];
                horizon = 0;
                for (int i = 0; i < nJobs; i++) {
                    tokenizer = new StringTokenizer(in.readLine());
                    for (int j = 0; j < nMachines; j++) {
                        machine[i][j] = Integer.parseInt(tokenizer.nextToken());
                        duration[i][j] = Integer.parseInt(tokenizer.nextToken());
                        horizon += duration[i][j];
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Collect the variables related to the specified machine
         *
         * @param variables is nJobs x nMachine matrix
         * @param m         a machine index
         * @return an array containing all the variables[i][j] such that machine[i][j] == m
         */
        public IntVar[] collect(IntVar[][] variables, int m) {
            ArrayList<IntVar> res = new ArrayList<IntVar>();
            for (int i = 0; i < nJobs; i++) {
                for (int j = 0; j < nMachines; j++) {
                    if (machine[i][j] == m) {
                        res.add(variables[i][j]);
                    }
                }
            }
            return res.toArray(new IntVar[]{});
        }

        public int[] collect(int[][] data, int m) {
            ArrayList<Integer> res = new ArrayList<Integer>();
            for (int i = 0; i < nJobs; i++) {
                for (int j = 0; j < nMachines; j++) {
                    if (machine[i][j] == m) {
                        res.add(data[i][j]);
                    }
                }
            }
            return res.stream().mapToInt(i -> i).toArray();
        }
    }

    IntVar[] precedences;
    IntVar[][] start;
    IntVar makespan;
    JobShopInstance jobShopInstance;
    Map<IntVar, TaskDisjunction> getTask = new HashMap<>();

    public class TaskDisjunction {
        IntVar start1, start2;
        IntVar end1, end2;

        public TaskDisjunction(IntVar start1, IntVar end1, IntVar start2, IntVar end2) {
            this.start1 = start1;
            this.end1 = end1;
            this.start2 = start2;
            this.end2 = end2;
        }

        public int slack() {
            return start1.getDomainSize() + start2.getDomainSize();
        }

        public int slackIfBefore() {
            int slack1 = Math.min(start2.getUB() - 1, start1.getUB()) - start1.getLB();
            int slack2 = start2.getUB() - Math.max(start2.getLB(), end1.getLB());
            return slack1 + slack2;
        }

        /**
         * The total slack if activity 1 would be placed after activity 2
         * @return slack if activity 1 would be placed after activity 2
         */
        public int slackIfAfter() {
            int slack2 = Math.min(start1.getUB() - 1, start2.getUB()) - start2.getLB();
            int slack1 = start1.getUB() - Math.max(start1.getLB(), end2.getLB());
            return slack1 + slack2;
        }

    }

    // TODO test with branching on precedences with largest slack?

    public AbstractStrategy<IntVar> makePrecedenceSearch() {
        return new IntStrategy(precedences, largestSlackSelector(), valsel.make().apply(getModel()));
    }

    @Override
    public IntValueSelector makeGreedy() {
        return largestSlackIntSelector();
    }

    public void makeSearch(IntVar[] decisionVars) {
        Model model = getModel();
        Solver solver = model.getSolver();
        valsel.setGreedy(m -> makeGreedy());
        //AbstractStrategy<IntVar> precedenceSearch = varsel.make().apply(precedences, valsel.make().apply(model));
        AbstractStrategy<IntVar> precedenceSearch = makePrecedenceSearch();
        // TODO only one alternative that fixes the makespan to its minimum value. This currently gives 2 alternatives
        AbstractStrategy<IntVar> fixMakeSpan = new IntStrategy(new IntVar[] {makespan}, new FirstFail(model), new IntDomainMin());
        StrategiesSequencer<IntVar> search = new StrategiesSequencer<>(precedenceSearch, fixMakeSpan);
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

    @Override
    public Model makeModel() {
        Model model = new Model("JobShop");
        // variable creation
        start = new IntVar[jobShopInstance.nJobs][jobShopInstance.nMachines];
        IntVar[][] end = new IntVar[jobShopInstance.nJobs][jobShopInstance.nMachines];
        IntVar capacity = model.intVar("capacity", 1);
        for (int i = 0; i < jobShopInstance.nJobs; i++) {
            for (int j = 0; j < jobShopInstance.nMachines; j++) {
                start[i][j] = model.intVar("start_"+i+"_"+j, 0, jobShopInstance.horizon);
                end[i][j] = model.offset(start[i][j], jobShopInstance.duration[i][j]);
            }
        }
        // job precedence
        for (int i = 0; i < jobShopInstance.nJobs; i++) {
            for (int j = 1; j < jobShopInstance.nMachines; j++) {
                precedence(end[i][j-1], start[i][j]).post();
            }
        }

        // precedence and no overlap between tasks
        List<IntVar> precedenceList = new ArrayList<>();
        for (int m = 0; m < jobShopInstance.nMachines; m++) {
            // collect activities on machine m
            IntVar[] start_m = jobShopInstance.collect(start, m);
            IntVar[] end_m = jobShopInstance.collect(end, m);
            int[] dur_m = jobShopInstance.collect(jobShopInstance.duration, m);
            IntVar[] consumption = IntStream.range(0, start_m.length).mapToObj(i -> capacity).toArray(IntVar[]::new);

            for (int a1 = 0; a1 < start_m.length; a1++) {
                for (int a2 = a1+1; a2 < start_m.length ; a2++) {
                    BoolVar a1_before_a2 = model.boolVar();
                    BoolVar a2_before_a1 = model.boolNotView(a1_before_a2);
                    Constraint end_a1_before_start_a2 = precedence(end_m[a1], start_m[a2]);
                    Constraint end_a2_before_start_a1 = precedence(end_m[a2], start_m[a1]);
                    end_a1_before_start_a2.reifyWith(a1_before_a2);
                    end_a2_before_start_a1.reifyWith(a2_before_a1);
                    precedenceList.add(a1_before_a2);
                    TaskDisjunction disjunction = new TaskDisjunction(start_m[a1], end_m[a1], start_m[a2], end_m[a2]);
                    getTask.put(a1_before_a2, disjunction);
                }
            }
            Task[] tasks = new Task[start_m.length];
            for (int i = 0 ; i < tasks.length ; i++) {
                tasks[i] = new Task(start_m[i], dur_m[i], end_m[i]);
            }
            model.cumulative(tasks, consumption, capacity).post();
        }
        precedences = precedenceList.toArray(new IntVar[precedenceList.size()]);

        IntVar[] endLast = new IntVar[jobShopInstance.nJobs];
        for (int i = 0; i < jobShopInstance.nJobs; i++) {
            endLast[i] = end[i][jobShopInstance.nMachines - 1];
        }
        makespan = model.max("makespan", endLast);
        model.setObjective(Model.MINIMIZE, makespan);
        return model;
    }

    static Constraint precedence(IntVar x, IntVar y) {
        return x.getModel().arithm(x, "<=", y);
    }

    @Override
    public boolean setUp(String... args) throws SetUpException {
        boolean setup = super.setUp(args);
        this.jobShopInstance = new JobShopInstance(this.instance);
        return setup;
    }

    private VariableSelector<IntVar> largestSlackSelector() {
        return variables -> {
            assert variables == precedences;
            IntVar bestPred = null;
            int smallestSlack = Integer.MAX_VALUE;
            for (IntVar pred: precedences) {
                if (!pred.isInstantiated()) {
                    int slack = getTask.get(pred).slack();
                    if (slack < smallestSlack) {
                        smallestSlack = slack;
                        bestPred = pred;
                    }
                }
            }
            return bestPred;
        };
    }

    private IntValueSelector largestSlackIntSelector() {
        return pred -> {
            TaskDisjunction taskDisjunction = getTask.get(pred);
            if (taskDisjunction.slackIfBefore() > taskDisjunction.slackIfAfter()) {
                return 1; // activity 1 before activity 2
            } else {
                return 0; // activity 2 before activity 1
            }
        };
    }

    @Override
    public IntVar[] decisionVars() {
        return precedences;
    }

    /*
    data/jobshop/big/uclouvain.txt,600,(varsel=DOMWDEG;flushRate=2147483647),(valsel=MIN;best=None;bestFreq=1;last=false),(pol=NONE;cutoff=0;offset=0;geo=1.0;resetOnSolution=false),(obj:2385;t:0.060;nodes:236;fails:0;restarts:0)(obj:2216;t:0.067;nodes:246;fails:23;restarts:0)(obj:2158;t:0.069;nodes:249;fails:24;restarts:0)(obj:2097;t:0.074;nodes:259;fails:35;restarts:0)(obj:2087;t:0.081;nodes:284;fails:99;restarts:0)(obj:2030;t:0.085;nodes:309;fails:119;restarts:0)(obj:2014;t:0.089;nodes:333;fails:145;restarts:0)(obj:1985;t:0.098;nodes:371;fails:192;restarts:0)(obj:1975;t:0.101;nodes:394;fails:217;restarts:0)(obj:1918;t:0.103;nodes:419;fails:234;restarts:0)(obj:1902;t:0.105;nodes:439;fails:257;restarts:0)(obj:1849;t:0.107;nodes:472;fails:289;restarts:0)(obj:1833;t:0.109;nodes:491;fails:309;restarts:0)(obj:1781;t:0.113;nodes:532;fails:368;restarts:0)(obj:1765;t:0.114;nodes:552;fails:389;restarts:0)(obj:1749;t:0.118;nodes:596;fails:428;restarts:0)(obj:1733;t:0.120;nodes:620;fails:454;restarts:0)(obj:1680;t:0.123;nodes:657;fails:485;restarts:0)(obj:1664;t:0.124;nodes:673;fails:506;restarts:0)(obj:1650;t:0.129;nodes:717;fails:559;restarts:0)(obj:1634;t:0.130;nodes:738;fails:582;restarts:0)(obj:1623;t:0.134;nodes:769;fails:609;restarts:0)(obj:1607;t:0.136;nodes:790;fails:627;restarts:0)(obj:1581;t:0.138;nodes:821;fails:656;restarts:0)(obj:1565;t:0.140;nodes:837;fails:675;restarts:0)(obj:1553;t:0.145;nodes:884;fails:731;restarts:0)(obj:1547;t:0.147;nodes:913;fails:755;restarts:0)(obj:1531;t:0.149;nodes:941;fails:776;restarts:0)(obj:1517;t:0.153;nodes:986;fails:821;restarts:0)(obj:1501;t:0.156;nodes:1010;fails:842;restarts:0)(obj:1490;t:0.163;nodes:1042;fails:873;restarts:0)(obj:1474;t:0.166;nodes:1064;fails:891;restarts:0)(obj:1448;t:0.172;nodes:1096;fails:922;restarts:0)(obj:1432;t:0.176;nodes:1116;fails:941;restarts:0)(obj:1421;t:0.182;nodes:1164;fails:997;restarts:0)(obj:1419;t:0.183;nodes:1171;fails:999;restarts:0)(obj:1414;t:0.185;nodes:1182;fails:1010;restarts:0)(obj:1412;t:0.186;nodes:1191;fails:1016;restarts:0)(obj:1405;t:0.188;nodes:1210;fails:1036;restarts:0)(obj:1403;t:0.189;nodes:1218;fails:1039;restarts:0)(obj:1398;t:0.191;nodes:1230;fails:1050;restarts:0)(obj:1396;t:0.192;nodes:1238;fails:1054;restarts:0)(obj:1391;t:0.195;nodes:1285;fails:1098;restarts:0)(obj:1384;t:0.197;nodes:1320;fails:1131;restarts:0)(obj:1383;t:0.200;nodes:1360;fails:1187;restarts:0)(obj:1379;t:0.204;nodes:1401;fails:1232;restarts:0)(obj:1363;t:0.205;nodes:1421;fails:1242;restarts:0)(obj:1353;t:0.209;nodes:1469;fails:1282;restarts:0)(obj:1350;t:0.209;nodes:1472;fails:1285;restarts:0)(obj:1349;t:0.210;nodes:1478;fails:1288;restarts:0)(obj:1342;t:0.213;nodes:1504;fails:1310;restarts:0)(obj:1339;t:0.213;nodes:1507;fails:1313;restarts:0)(obj:1338;t:0.214;nodes:1513;fails:1316;restarts:0)(obj:1326;t:0.216;nodes:1529;fails:1329;restarts:0)(obj:1323;t:0.216;nodes:1532;fails:1332;restarts:0)(obj:1322;t:0.217;nodes:1538;fails:1335;restarts:0)(obj:1300;t:0.219;nodes:1562;fails:1357;restarts:0)(obj:1296;t:0.220;nodes:1567;fails:1359;restarts:0)(obj:1284;t:0.221;nodes:1581;fails:1373;restarts:0)(obj:1281;t:0.222;nodes:1584;fails:1376;restarts:0)(obj:1280;t:0.222;nodes:1590;fails:1379;restarts:0)(obj:1278;t:0.225;nodes:1622;fails:1420;restarts:0)(obj:1276;t:0.226;nodes:1627;fails:1424;restarts:0)(obj:1275;t:0.227;nodes:1631;fails:1431;restarts:0)(obj:1271;t:0.228;nodes:1651;fails:1444;restarts:0)(obj:1267;t:0.229;nodes:1657;fails:1448;restarts:0)(obj:1258;t:0.232;nodes:1701;fails:1478;restarts:0)(obj:1256;t:0.233;nodes:1707;fails:1482;restarts:0)(obj:1248;t:0.236;nodes:1746;fails:1523;restarts:0)(obj:1247;t:0.237;nodes:1761;fails:1541;restarts:0)(obj:1244;t:0.239;nodes:1802;fails:1572;restarts:0)(obj:1242;t:0.240;nodes:1810;fails:1578;restarts:0)(obj:1239;t:0.243;nodes:1839;fails:1618;restarts:0)(obj:1223;t:0.244;nodes:1854;fails:1631;restarts:0)(obj:1220;t:0.246;nodes:1890;fails:1657;restarts:0)(obj:1218;t:0.247;nodes:1896;fails:1662;restarts:0)(obj:1214;t:0.249;nodes:1938;fails:1697;restarts:0)(obj:1206;t:0.250;nodes:1956;fails:1720;restarts:0)(obj:1197;t:0.253;nodes:2007;fails:1767;restarts:0)(obj:1190;t:0.254;nodes:2013;fails:1771;restarts:0)(obj:1181;t:0.255;nodes:2036;fails:1793;restarts:0)(obj:1179;t:0.255;nodes:2038;fails:1797;restarts:0)(obj:1174;t:0.256;nodes:2044;fails:1799;restarts:0)(obj:1167;t:0.257;nodes:2069;fails:1824;restarts:0)(obj:1162;t:0.263;nodes:2114;fails:1876;restarts:0)(obj:1160;t:0.265;nodes:2132;fails:1898;restarts:0)(obj:1159;t:0.267;nodes:2147;fails:1911;restarts:0)(obj:1154;t:0.267;nodes:2154;fails:1915;restarts:0)(obj:1153;t:0.269;nodes:2172;fails:1936;restarts:0)(obj:1148;t:0.274;nodes:2219;fails:1968;restarts:0)(obj:1147;t:0.275;nodes:2231;fails:1980;restarts:0)(obj:1146;t:0.276;nodes:2243;fails:1990;restarts:0)(obj:1144;t:0.277;nodes:2255;fails:2001;restarts:0)(obj:1142;t:0.278;nodes:2269;fails:2011;restarts:0)(obj:1137;t:0.279;nodes:2283;fails:2025;restarts:0)(obj:1136;t:0.280;nodes:2298;fails:2040;restarts:0)(obj:1131;t:0.281;nodes:2313;fails:2053;restarts:0)(obj:1126;t:0.283;nodes:2331;fails:2071;restarts:0)(obj:1124;t:0.284;nodes:2341;fails:2082;restarts:0)(obj:1122;t:0.285;nodes:2357;fails:2095;restarts:0)(obj:1120;t:0.286;nodes:2369;fails:2105;restarts:0)(obj:1115;t:0.287;nodes:2383;fails:2117;restarts:0)(obj:1113;t:0.289;nodes:2415;fails:2140;restarts:0)(obj:1112;t:0.291;nodes:2434;fails:2172;restarts:0)(obj:1107;t:0.292;nodes:2445;fails:2180;restarts:0)(obj:1106;t:0.294;nodes:2471;fails:2206;restarts:0)(obj:1097;t:0.300;nodes:2515;fails:2249;restarts:0)(obj:1093;t:0.303;nodes:2542;fails:2276;restarts:0)(obj:1090;t:0.305;nodes:2562;fails:2295;restarts:0)(obj:1087;t:0.306;nodes:2579;fails:2312;restarts:0)(obj:1085;t:0.308;nodes:2600;fails:2329;restarts:0)(obj:1084;t:0.309;nodes:2603;fails:2332;restarts:0)(obj:1083;t:0.311;nodes:2626;fails:2353;restarts:0)(obj:1081;t:0.313;nodes:2653;fails:2372;restarts:0)(obj:1080;t:0.314;nodes:2657;fails:2376;restarts:0)(obj:1077;t:0.316;nodes:2685;fails:2400;restarts:0)(obj:1075;t:0.318;nodes:2700;fails:2411;restarts:0)(obj:1074;t:0.318;nodes:2703;fails:2414;restarts:0)(obj:1073;t:0.320;nodes:2731;fails:2444;restarts:0)(obj:1071;t:0.323;nodes:2760;fails:2473;restarts:0)(obj:1070;t:0.326;nodes:2795;fails:2499;restarts:0)(obj:1061;t:0.328;nodes:2822;fails:2526;restarts:0)(obj:1058;t:0.331;nodes:2863;fails:2564;restarts:0)(obj:1057;t:0.333;nodes:2886;fails:2593;restarts:0)(obj:1048;t:0.336;nodes:2929;fails:2616;restarts:0)(obj:1047;t:0.337;nodes:2955;fails:2651;restarts:0)(obj:1044;t:0.340;nodes:2984;fails:2682;restarts:0)(obj:1043;t:0.341;nodes:3007;fails:2704;restarts:0)(obj:1041;t:0.344;nodes:3041;fails:2732;restarts:0)(obj:1038;t:0.345;nodes:3052;fails:2741;restarts:0)(obj:1027;t:0.347;nodes:3078;fails:2766;restarts:0)(obj:1025;t:0.348;nodes:3089;fails:2773;restarts:0)(obj:1024;t:0.349;nodes:3107;fails:2790;restarts:0)(obj:1018;t:0.350;nodes:3125;fails:2807;restarts:0)(obj:1016;t:0.352;nodes:3142;fails:2823;restarts:0)(obj:1014;t:0.352;nodes:3155;fails:2833;restarts:0)(obj:1012;t:0.354;nodes:3179;fails:2859;restarts:0)(obj:1011;t:0.355;nodes:3186;fails:2862;restarts:0)(obj:1006;t:0.357;nodes:3223;fails:2888;restarts:0)(obj:1004;t:0.358;nodes:3238;fails:2901;restarts:0)(obj:1001;t:0.360;nodes:3267;fails:2937;restarts:0)(obj:1000;t:0.361;nodes:3290;fails:2965;restarts:0)(obj:994;t:0.364;nodes:3324;fails:2996;restarts:0)(obj:991;t:0.365;nodes:3346;fails:3011;restarts:0)(obj:988;t:0.367;nodes:3373;fails:3041;restarts:0)(obj:986;t:0.369;nodes:3393;fails:3059;restarts:0)(obj:981;t:0.369;nodes:3400;fails:3066;restarts:0)(obj:978;t:0.371;nodes:3420;fails:3087;restarts:0)(obj:976;t:0.375;nodes:3449;fails:3115;restarts:0)(obj:975;t:0.377;nodes:3478;fails:3145;restarts:0)(obj:971;t:0.381;nodes:3517;fails:3173;restarts:0)(obj:970;t:0.383;nodes:3549;fails:3193;restarts:0)(obj:961;t:0.384;nodes:3567;fails:3208;restarts:0)(obj:955;t:0.385;nodes:3600;fails:3240;restarts:0)(obj:953;t:0.387;nodes:3623;fails:3263;restarts:0)(obj:951;t:0.388;nodes:3655;fails:3297;restarts:0)(obj:947;t:0.390;nodes:3685;fails:3326;restarts:0)(obj:946;t:0.392;nodes:3717;fails:3356;restarts:0)(obj:942;t:0.395;nodes:3759;fails:3394;restarts:0)(obj:938;t:0.398;nodes:3789;fails:3414;restarts:0)(obj:925;t:0.399;nodes:3809;fails:3438;restarts:0)(obj:920;t:0.400;nodes:3828;fails:3457;restarts:0)(obj:918;t:0.402;nodes:3848;fails:3475;restarts:0)(obj:917;t:0.404;nodes:3873;fails:3505;restarts:0)(obj:915;t:0.405;nodes:3891;fails:3519;restarts:0)(obj:913;t:0.409;nodes:3916;fails:3548;restarts:0)(obj:911;t:0.411;nodes:3936;fails:3575;restarts:0)(obj:908;t:0.413;nodes:3965;fails:3590;restarts:0)(obj:906;t:0.415;nodes:3979;fails:3609;restarts:0)(obj:905;t:0.455;nodes:4414;fails:4047;restarts:0)(obj:904;t:1.778;nodes:14577;fails:14203;restarts:0)(obj:899;t:1.779;nodes:14591;fails:14215;restarts:0)(obj:898;t:1.779;nodes:14602;fails:14225;restarts:0)(obj:895;t:2.170;nodes:17109;fails:16722;restarts:0)(obj:893;t:2.171;nodes:17126;fails:16735;restarts:0)(obj:890;t:2.171;nodes:17140;fails:16748;restarts:0)(obj:889;t:2.172;nodes:17164;fails:16777;restarts:0)(obj:881;t:2.174;nodes:17186;fails:16799;restarts:0)(obj:880;t:2.174;nodes:17195;fails:16815;restarts:0)(obj:871;t:2.177;nodes:17220;fails:16828;restarts:0)(obj:870;t:2.177;nodes:17231;fails:16847;restarts:0)(obj:864;t:2.195;nodes:17366;fails:16973;restarts:0)(obj:861;t:2.195;nodes:17381;fails:16985;restarts:0)(obj:860;t:2.246;nodes:17710;fails:17318;restarts:0)(obj:856;t:2.299;nodes:17993;fails:17604;restarts:0)(obj:855;t:2.301;nodes:18009;fails:17622;restarts:0)(obj:854;t:2.323;nodes:18246;fails:17846;restarts:0)(obj:853;t:2.355;nodes:18513;fails:18111;restarts:0)(obj:845;t:2.359;nodes:18553;fails:18145;restarts:0)(obj:844;t:2.359;nodes:18558;fails:18150;restarts:0)(obj:843;t:2.360;nodes:18566;fails:18159;restarts:0)(obj:842;t:3.438;nodes:35453;fails:35041;restarts:0)(obj:841;t:4.473;nodes:53288;fails:52879;restarts:0)(obj:840;t:4.473;nodes:53293;fails:52884;restarts:0)(obj:839;t:4.483;nodes:53384;fails:52968;restarts:0)(obj:838;t:4.525;nodes:53948;fails:53525;restarts:0)(obj:836;t:4.677;nodes:55288;fails:54864;restarts:0)(obj:835;t:4.677;nodes:55298;fails:54872;restarts:0)(obj:832;t:4.678;nodes:55312;fails:54886;restarts:0)(obj:829;t:4.679;nodes:55323;fails:54896;restarts:0)(obj:826;t:4.679;nodes:55334;fails:54906;restarts:0)(obj:823;t:4.682;nodes:55363;fails:54924;restarts:0)(obj:820;t:4.682;nodes:55375;fails:54935;restarts:0)(obj:819;t:4.685;nodes:55396;fails:54958;restarts:0)(obj:818;t:4.685;nodes:55400;fails:54961;restarts:0)(obj:815;t:4.685;nodes:55407;fails:54965;restarts:0)(obj:813;t:4.685;nodes:55422;fails:54975;restarts:0)(obj:810;t:4.810;nodes:56165;fails:55718;restarts:0)(obj:808;t:4.811;nodes:56169;fails:55721;restarts:0),true,5.054,57498,57081,0,250.00,674,316,566,-f -varh DOMWDEG -lc 1 -valsel MIN;BestSubset;1;false -restarts NONE;0;1.0;0;false -limit 00h10m00s data/jobshop/big/uclouvain.txt

     */

    public static void main(String[] args) {
        mainFrom(args, JobShopBench::new);
    }
}
