# test.sh: a small tool for benchmarking a paintchat server

# run_test: runs a test iteration
run_test() {
  echo "---------TEST $1---------"
  sbt run 
  echo
}

# build benchmark jar (exit if build fails)
echo "building benchmark tool"
(sbt assembly && echo "beginning benchmarks") || ("build failed. exiting." && exit 1)

# create results dir and output file
mkdir -p results
output_file=`date +%F.%H.%M.%S`.testlog
touch results/$output_file
echo "date: $(date)" >> results/$output_file
echo "host: $(hostname)" >> results/$output_file
echo >> results/$output_file

# run tests
for i in `seq 1 5`
do
  echo "running test $i"
  run_test $i >> results/$output_file
  echo "test $i complete"
done
rm -f results/latest.textlog
cp results/$output_file results/latest.testlog
echo "benchmarks complete"
exit 0
