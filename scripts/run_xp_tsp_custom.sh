echo "compiling"
mvn clean package -DskipTests -q
echo "compilation done"
# path of the executable
launch_solver=" java -cp .:examples/target/examples-4.10.15-SNAPSHOT-jar-with-dependencies.jar org.chocosolver.examples.integer.TSPBench"
currentDate=$(date +%Y-%m-%d_%H-%M-%S);  #
commitId=$(git rev-parse HEAD)
outFileOpt="results/tsp/tsp-opt-${commitId}-${currentDate}.csv"  # filename of the results (with the date at the end of the file)

declare -a valueSelection=("GREEDY,None,1,false"
"MIN,None,1,false"
"MIN,Best,1,false"
"MIN,BestSubset,1,false"
"MIN,ReverseBest,1,false"
"MIN,ReverseBestSubset,1,false")  # each value selection to try
timeout="00h30m00s"  # timeout in seconds
iter=1   # number of iterations to account for randomness

mkdir -p "results/tsp"  # where the results will be written
rm -f $outFileOpt  # delete filename of the results if it already existed (does not delete past results, unless their datetime is the same)
# the solver must print only one line when it is finished, otherwise we won't get a CSV at the end
# this is the header of the csv. This header needs to change depending on the solver / type of experiment that is being run
# all rows need to be printed by the solver itself
# the column "solutionsOverTime" is in the format (time;objective;nodes;failures;restarts)
echo "instance,maxRuntime,variableSelection,valueSelection,restarts,solutionsOverTime,isOptimal,runtime,nodes,fails,restarts,memory,vars,varsWithoutView,constraints,args" >> $outFileOpt
echo "writing inputs"
# write all the configs into a temporary file
inputFile="inputFileTSP"
rm -f $inputFile  # delete previous temporary file if it existed
for (( i=1; i<=$iter; i++ ))  # for each iteration
do
  for val in "${valueSelection[@]}"  # for each relaxation to perform
  do
    # extracts the instances from the data folder
    # write one line per instance containing its filename, along with the relaxation to perform
    find data/tsp/custom -type f | sed "s/$/-${val}/"  >> $inputFile
  done
done
# at this point, the input file contains rows in the format
# instance_filename,value_selection
echo "launching experiments in parallel"
# search with
# - variable selection: DOMWDEG and last conflict
# - value selection input
cat $inputFile | parallel --colsep '-' $launch_solver -f -varh DOMWDEG -lc 1 -valsel {2} -restarts NONE,0,1.0,0,false -limit ${timeout} {1} >> $outFileOpt
# delete the temporary file
echo "experiments have been run"
rm -f $inputFile

