
#!/bin/sh
# set -euf -o pipefail

EVOMASTER_TIME="30m"

for SEED in {1..5}
do
    for APP in 'ncs' 'scs' 'news'
    do
        for LEVEL in 0 1 2
        do
            # Prepare workdirs
            DIR=$(pwd)
            GENERATED_DIR="$DIR/generated/$APP/$LEVEL"
            mkdir -p $GENERATED_DIR

            cd src
            nohup python -m evomaster_client.cli run-em-handler -m "evomaster_benchmark.em_handlers.$APP" -c 'EMHandler' -i $LEVEL > "$GENERATED_DIR/tmp.log" 2>&1 &
            cd -

            echo $! > save_pid.txt
            PID="$(cat save_pid.txt)"

            SEED=$(python -c 'import random; rng = random.SystemRandom(); print(rng.randint(1, 2**31-1))');
            echo "Using randomly generated seed: $SEED"

            TESTS_DIR="$GENERATED_DIR/$SEED/tests"
            LOGS_DIR="$GENERATED_DIR/$SEED/logs"
            COVERAGE_DIR="$GENERATED_DIR/$SEED/coverage"
            mkdir -p $TESTS_DIR
            mkdir -p $LOGS_DIR
            mkdir -p $COVERAGE_DIR

            echo "Running EvoMaster handler for $APP (level $LEVEL) in $PID..."
            java -jar ../core/target/evomaster.jar --maxTime $EVOMASTER_TIME --writeStatistics false --outputFolder $TESTS_DIR --outputFormat PYTHON_UNITTEST

            echo "Shutting down EvoMaster handler for $APP (level $LEVEL) in $PID..."
            kill $PID
            rm save_pid.txt

            # Move logs from tmp file
            mv "$GENERATED_DIR/tmp.log"  "$LOGS_DIR/emhandler.log"

            # Generate coverage reports
            cd src
            python -m pytest --cov-report html:$COVERAGE_DIR/cov_html --cov-report xml:$COVERAGE_DIR/cov.xml --cov-report annotate:$COVERAGE_DIR/cov_annotate \
                            --cov=evomaster_benchmark/$APP "$TESTS_DIR/EvoMasterTest.py"
            cd -
        done
    done
done
