echo "compiling"
mvn clean package -DskipTests -q
echo "compilation done"
# path of the executable
launch_solver=" java -cp .:parsers/target/choco-parsers-4.10.15-SNAPSHOT-jar-with-dependencies.jar org.chocosolver.parser.xcsp.ChocoXCSP"
currentDate=$(date +%Y-%m-%d_%H-%M-%S);  #
commitId=$(git rev-parse HEAD)
outFileOpt="results/xcsp/xcsp-opt-${commitId}-${currentDate}.csv"  # filename of the results (with the date at the end of the file)

#valSel,Best,freqBest,PhaseSaving
declare -a valueSelection=("MIN,None,1,false"
"MIN,Best,1,false"
"MIN,BestSubset,1,false"
"MIN,ReverseBest,1,false"
"MIN,ReverseBestSubset,1,false")  # each value selection to try
timeout="00h30m00s"  # timeout in seconds
iter=1   # number of iterations to account for randomness

# tuples of (memory, threads)
# index i == memory, i+1 == threads
my_tuples=(3000 40 6000 20 12800 10)

# Get the number of elements in the array
num_elements=${#my_tuples[@]}

# Loop through the array two elements at a time
echo "launching experiments in parallel"
mkdir -p "results/xcsp"  # where the results will be written
rm -f $outFileOpt  # delete filename of the results if it already existed (does not delete past results, unless their datetime is the same)
# the solver must print only one line when it is finished, otherwise we won't get a CSV at the end
# this is the header of the csv. This header needs to change depending on the solver / type of experiment that is being run
# all rows need to be printed by the solver itself
# the column "solutionsOverTime" is in the format (time;objective;nodes;failures;restarts)
echo "instance,maxRuntime,variableSelection,valueSelection,restarts,solutionsOverTime,isOptimal,runtime,nodes,fails,restarts,memory,vars,varsWithoutView,constraints,args" >> $outFileOpt

for ((i = 0; i < num_elements; i+=2)); do
  # Access the elements that represent the tuple
  memory=${my_tuples[i]}
  nParallel=${my_tuples[i+1]}
  echo "running ${memory} MB instances"
  inputFile="inputFileXCSP"
  source_file="data/xcsp/attribution/instances_${memory}mb"
  rm -f $inputFile  # delete previous temporary file if it existed
  for (( j=1; j<=$iter; j++ ))  # for each iteration
  do
    for val in "${valueSelection[@]}"  # for each relaxation to perform
    do
      # extracts the instances from the data folder
      # write one line per instance containing its filename, along with the relaxation to perform
      cat $source_file | sed "s/$/-${val}/"  >> $inputFile
    done
  done
  # at this point, the input file contains rows in the format
  # instance_filename,value_selection
  # search with
  # - variable selection: DOMWDEG and last conflict
  # - value selection input
  cat $inputFile | parallel -j $nParallel --colsep '-' $launch_solver -f -varh DOMWDEG -lc 1 -valsel {2} -restarts NONE,0,1.0,0,false -limit ${timeout} {1} >> $outFileOpt
  # delete the temporary file
  rm -f $inputFile
done

echo "experiments have been run"

