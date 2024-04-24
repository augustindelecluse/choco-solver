This repository contains the source code of the paper "Black-Box Value Heuristics for Solving Optimization Problems with Constraint Programming".

The repository itself is a fork of [Choco-solver](https://github.com/chocoteam/choco-solver), that has been modified.

# Classes of interest

- The Reverse Look-Ahead implementation can be found at `org/chocosolver/solver/search/strategy/selectors/values/IntDomainReverseBest.java`
- The Restricted Fixpoint (RF) is handled by the following classes:
  - The `CompactConstraintNetwork` class that computes the shortest paths to the objective
  - The `SubSetToObjective` class (`org/chocosolver/solver/search/strategy/selectors/values/SubSetToObjective.java`) that deactivates constraints outside of the shortest paths in order to run a restricted fixpoint
  - BIVS+RF is implemented in the `IntDomainBestSubset` class (`org/chocosolver/solver/search/strategy/selectors/values/IntDomainBestSubset.java`)
  - RLA+RF is implemented in the `IntDomainReverseBestSubset` class (`org/chocosolver/solver/search/strategy/selectors/values/IntDomainReverseBestSubset.java`)
- The models for the problems used are located within the `examples` project
  - The TSP model can be found at `org/chocosolver/examples/integer/TSP.java`
  - The QAP model can be found at `org/chocosolver/examples/integer/QAPBench.java`
  - The JobShop model can be found at `org/chocosolver/examples/integer/JobShopBench.java`

For an example of usage, see for instance the search defined in the TSP `org/chocosolver/examples/integer/TSP.java`:

```java
solver.setSearch(Search.intVarSearch(
        new DomOverWDeg<>(succ, 42), // succ are the decision variables
        //new IntDomainLast(lastSol, new IntDomainReverseBest(model), null), // RLA
        //new IntDomainReverseBest(model), // RLA
        //new IntDomainReverseBestSubset(model), // RLA+RF
        //new IntDomainBest((k, v) -> false), // BIVS
        new IntDomainBestSubset(), // BIVS+RF
        //new IntDomainMin(), // MIN
        succ) // succ are the decision variables
);
```

# Executables for the benchmark

The executables used for compiling the project and running the experiments are located within the `scripts` folder.
The results of running the executables are added into the `results` folder.

The scripts of interest are:
- `scripts/run_xp_tsp_with_attribution.sh`
  - This script requires the TSP instances to be downloaded, which can be achieved by running the `scripts/download_tsp_instances.sh` script
- `scripts/run_xp_jobshop_with_attribution_small.sh`
- `scripts/run_xp_qap_with_attribution.sh`
- `scripts/run_xp_xcsp_with_attribution.sh`

The requirements for running those scripts are:
- maven
- java
- GNU parallel
- git

The pseudonyms of the methods are the following:
- BEST: BIVS
- REVERSEBEST: RLA
- BESTSUBSET: BIVS+RF
- REVERSEBESTSUBSET: RLA+RF


# Running one instance

First, be sure to compile the project and build the relevant executable:

```
mvn clean package -DskipTests -q
```

Then, for running the experiments, the commands are the following:

## TSP

```
java -cp .:examples/target/examples-4.10.15-SNAPSHOT-jar-with-dependencies.jar org.chocosolver.examples.integer.TSPBench
```

## QAP

```
java -cp .:examples/target/examples-4.10.15-SNAPSHOT-jar-with-dependencies.jar org.chocosolver.examples.integer.QAPBench
```

## JobShop

```
java -cp .:examples/target/examples-4.10.15-SNAPSHOT-jar-with-dependencies.jar org.chocosolver.examples.integer.JobShopBench
```

## XCSP

```
java -cp .:parsers/target/choco-parsers-4.10.15-SNAPSHOT-jar-with-dependencies.jar org.chocosolver.parser.xcsp.ChocoXCSP
```

All those command give take as input the following parameters:

```
-f -varh DOMWDEG -lc 1 -valsel VALSEL -restarts NONE,0,1.0,0,false -limit $TIMEOUT $INSTANCE
```

Where `TIMEOUT` is a timeout to use (for instance `"00h30m00s"`), `INSTANCE` is the path to an instance to solve and `VALSEL` is the value selection to use. The relevant ones are:
- MIN: `"MIN,None,1,false"`
- BIVS: `"MIN,Best,1,false"`
- BIVS+RF: `"MIN,BestSubset,1,false"`
- RLA: `"MIN,ReverseBest,1,false"`
- RLA+RF: `"MIN,ReverseBestSubset,1,false"`

Their output is a comma-separated line containing the following information, in order:
- instance: filename of the instance
- maxRuntime: maximum runtime considered, in seconds
- variableSelection: variable selection considered
- valueSelection: value selection considered
- restarts: restart strategy considered
- solutionsOverTime: list of solutions over time. Each solution is a tuple in the form of `(obj:826;t:0.589;nodes:227;fails:0;restarts:0)` showing the objective value, the time (in seconds) at which the solution was found, the number of search nodes, the number of failures and the number of restarts
- isOptimal: boolean telling if the solution found has been proven to be optimal
- runtime: runtime in seconds at which the search ended
- nodes: number of nodes considered in the search tree
- fails: number of failures encountered in the search tree
- restarts: number of restarts performed in the search tree
- memory: memory consumption by the end of the solving, in MB
- vars: number of variables created
- varsWithoutView: number of variables that are not view created
- constraints: number of constraints generated
- args: arguments used when calling the executable

## Full fledge example

The command 

```
java -cp .:examples/target/examples-4.10.15-SNAPSHOT-jar-with-dependencies.jar org.chocosolver.examples.integer.QAPBench -f -varh DOMWDEG -lc 1 -valsel MIN,ReverseBestSubset,1,false -restarts NONE,0,1.0,0,false -limit 00h30m00s data/qap/qapdata/chr12b.dat
```

Gives an output similar to

```
data/qap/qapdata/chr12b.dat,1800,(varsel=DOMWDEG;flushRate=2147483647),(valsel=MIN;best=REVERSEBESTSUBSET;bestFreq=1;last=false),(pol=NONE;cutoff=0;offset=0;geo=1.0;resetOnSolution=false),(obj:51766;t:0.080;nodes:12;fails:0;restarts:0)(obj:39046;t:0.081;nodes:13;fails:0;restarts:0)(obj:38854;t:0.084;nodes:16;fails:1;restarts:0)(obj:37622;t:0.090;nodes:22;fails:5;restarts:0)(obj:34278;t:0.096;nodes:28;fails:9;restarts:0)(obj:34114;t:0.097;nodes:29;fails:10;restarts:0)(obj:32732;t:0.106;nodes:37;fails:13;restarts:0)(obj:30828;t:0.107;nodes:40;fails:14;restarts:0)(obj:30402;t:0.116;nodes:49;fails:22;restarts:0)(obj:30056;t:0.119;nodes:55;fails:25;restarts:0)(obj:29804;t:0.119;nodes:56;fails:25;restarts:0)(obj:28124;t:0.121;nodes:60;fails:27;restarts:0)(obj:27824;t:0.133;nodes:71;fails:38;restarts:0)(obj:27232;t:0.145;nodes:83;fails:45;restarts:0)(obj:25524;t:0.145;nodes:84;fails:45;restarts:0)(obj:23296;t:0.208;nodes:127;fails:85;restarts:0)(obj:23072;t:0.209;nodes:128;fails:87;restarts:0)(obj:23004;t:0.210;nodes:129;fails:88;restarts:0)(obj:21034;t:0.339;nodes:283;fails:235;restarts:0)(obj:20966;t:0.346;nodes:292;fails:245;restarts:0)(obj:20710;t:0.366;nodes:307;fails:258;restarts:0)(obj:20664;t:0.392;nodes:334;fails:281;restarts:0)(obj:20492;t:0.393;nodes:340;fails:287;restarts:0)(obj:20284;t:0.394;nodes:345;fails:289;restarts:0)(obj:20244;t:0.396;nodes:353;fails:296;restarts:0)(obj:18322;t:0.405;nodes:377;fails:315;restarts:0)(obj:18114;t:0.405;nodes:379;fails:319;restarts:0)(obj:18078;t:0.407;nodes:385;fails:323;restarts:0)(obj:17240;t:0.411;nodes:394;fails:328;restarts:0)(obj:16912;t:0.446;nodes:448;fails:380;restarts:0)(obj:16832;t:0.447;nodes:452;fails:383;restarts:0)(obj:16796;t:0.449;nodes:460;fails:388;restarts:0)(obj:16386;t:0.522;nodes:596;fails:520;restarts:0)(obj:16162;t:0.524;nodes:602;fails:525;restarts:0)(obj:16130;t:0.526;nodes:613;fails:536;restarts:0)(obj:14808;t:0.588;nodes:763;fails:681;restarts:0)(obj:14488;t:0.588;nodes:764;fails:683;restarts:0)(obj:14328;t:0.589;nodes:770;fails:686;restarts:0)(obj:14296;t:0.592;nodes:783;fails:697;restarts:0)(obj:13150;t:0.694;nodes:953;fails:863;restarts:0)(obj:13126;t:0.697;nodes:965;fails:875;restarts:0)(obj:13062;t:0.727;nodes:1065;fails:972;restarts:0)(obj:12982;t:0.727;nodes:1069;fails:975;restarts:0)(obj:12532;t:0.728;nodes:1078;fails:980;restarts:0)(obj:12228;t:0.728;nodes:1081;fails:982;restarts:0)(obj:12092;t:0.729;nodes:1084;fails:984;restarts:0)(obj:11788;t:0.729;nodes:1086;fails:984;restarts:0)(obj:11768;t:0.732;nodes:1097;fails:995;restarts:0)(obj:11662;t:0.753;nodes:1154;fails:1047;restarts:0)(obj:11638;t:0.756;nodes:1168;fails:1062;restarts:0)(obj:11306;t:0.775;nodes:1218;fails:1107;restarts:0)(obj:10786;t:0.780;nodes:1241;fails:1129;restarts:0)(obj:10766;t:0.782;nodes:1249;fails:1136;restarts:0)(obj:9742;t:0.805;nodes:1320;fails:1202;restarts:0),true,0.964,1588,1481,0,250.00,202,169,158,-f -varh DOMWDEG -lc 1 -valsel MIN;ReverseBestSubset;1;false -restarts NONE;0;1.0;0;false -limit 00h30m00s data/qap/qapdata/chr12b.dat
```