instance="data/xcsp/cop23/AircraftAssemblyLine-1-178-00-0_c23.xml.lzma"
echo "compiling"
mvn clean package -DskipTests -q
echo "compilation done"
# path of the executable
launch_solver=" java -cp .:parsers/target/choco-parsers-4.10.15-SNAPSHOT-jar-with-dependencies.jar org.chocosolver.parser.xcsp.ChocoXCSP"
currentDate=$(date +%Y-%m-%d_%H-%M-%S);  #
commitId=$(git rev-parse HEAD)
outFileOpt="results/xcsp/xcsp-opt-${commitId}-${currentDate}"  # filename of the results (with the date at the end of the file)

declare -a valueSelection=("Best" "ReverseBest")  # each value selection to try

mkdir -p "results/xcsp"  # where the results will be written
rm -f $outFileOpt  # delete filename of the results if it already existed (does not delete past results, unless their datetime is the same)
# the solver must print only one line when it is finished, otherwise we won't get a CSV at the end
# this is the header of the csv. This header needs to change depending on the solver / type of experiment that is being run
# all rows need to be printed by the solver itself
# the column "solutionsOverTime" is in the format (time,objective,nodes,failures,restarts)
echo "instance,max_runtime,valueSelection,solutionsOverTime,isOptimal,foundOptimalAt,args" >> $outFileOpt
timeout="00h00m10s"  # timeout in seconds
iter=1   # number of iterations, for lns only
echo "writing inputs"
# write all the configs into a temporary file
inputFile="inputFileValueSel"
rm -f $inputFile  # delete previous temporary file if it existed
for (( i=1; i<=$iter; i++ ))  # for each iteration
do
  for val in "${valueSelection[@]}"  # for each relaxation to perform
  do
    # extracts the instances from the data folder
    # write one line per instance containing its filename, along with the relaxation to perform
    find data/xcsp/cop23/ -type f | sed "s/$/,${val}/"  >> $inputFile
  done
done
# add a comma and a random seed at the end of each line
awk -v seed=$RANDOM '{
    srand(seed + NR); # seed the random number generator with a combination of $RANDOM and the current record number
    rand_num = int(rand() * 10000); # generate a random number. Adjust the multiplier for the desired range.
    print $0 "," rand_num;
}' $inputFile | sponge $inputFile
# at this point, the input file contains rows in the format
# instance_filename,relaxation,seed
echo "launching experiments in parallel"
# the command line arguments for launching the solver. In this case, the solver is run using
# ./executable -f instance_filename -r relaxation -t timeout -s seed -m mode_for_optimisation -v verbosity
# change this depending on your solver
# the number ({1}, {2}) corresponds to the rows present in the inputFile, beginning at index 1 (i.e. in this case 3 columns, so 1, 2 and 3 are valid columns)

# search with
# - variable selection: DOMWDEG and last conflict
# - value selection input + phase saving
cat $inputFile | parallel --colsep ',' $launch_solver -f -varh DOMWDEG -best {2} -last -lc 1 -limit ${timeout} -seed {3} {1} >> $outFileOpt
# delete the temporary file
rm -f $inputFile