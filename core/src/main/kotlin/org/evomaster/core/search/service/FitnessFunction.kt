package org.evomaster.core.search.service

import com.google.inject.Inject
import org.evomaster.core.EMConfig
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual
import org.evomaster.core.search.service.monitor.SearchProcessMonitor
import org.evomaster.core.search.service.mutator.MutatedGeneSpecification
import org.slf4j.Logger
import org.slf4j.LoggerFactory


abstract class FitnessFunction<T>  where T : Individual {

    @Inject
    protected lateinit var configuration: EMConfig

    @Inject
    protected lateinit var archive: Archive<T>

    @Inject
    protected lateinit var idMapper: IdMapper

    @Inject
    protected lateinit var randomness : Randomness

    @Inject
    protected lateinit var time: SearchTimeController

    @Inject
    protected lateinit var statistics: Statistics

    @Inject
    protected lateinit var processMonitor: SearchProcessMonitor

    @Inject
    protected lateinit var config: EMConfig

    @Inject
    private lateinit var executionInfoReporter: ExecutionInfoReporter

    companion object{
        private val log : Logger = LoggerFactory.getLogger(FitnessFunction::class.java)
    }

    /**
     * @param modifiedSpec uses to collect modified info, e.g., HostnameResolutionAction might be added during this evaluation phase
     * @return null if there were problems in calculating the coverage
     */
    fun calculateCoverage(individual: T, targets: Set<Int> = setOf(), modifiedSpec: MutatedGeneSpecification?) : EvaluatedIndividual<T>?{

        val a = individual.seeMainExecutableActions().count()

//        val calculatedBefore = individual.copy()

        if(time.averageOverheadMsBetweenTests.isRecordingTimer()){
            val computation = time.averageOverheadMsBetweenTests.addElapsedTime()
            executionInfoReporter.addLatestComputationOverhead(computation, time.evaluatedIndividuals)
        }

        var ei = calculateIndividualCoverageWithStats(individual, targets, a)

        if(ei == null){
            /*
                try again, once. Working with TCP connections and remote servers,
                it is not impossible that sometimes things fail
             */
            log.warn("Failed to evaluate individual. Restarting the SUT before trying again")
            reinitialize()

            //let's wait a little, just in case...
            Thread.sleep(5_000)

            ei = calculateIndividualCoverageWithStats(individual, targets, a)


            if(ei == null){
                //give up, but record it
                log.warn("Failed twice in a row to evaluate individual. Giving up on it.")
                statistics.reportCoverageFailure()
            }
        }

        time.averageOverheadMsBetweenTests.doStartTimer()

        processMonitor.eval = ei

        time.newActionEvaluation(maxOf(1, a))
        time.newIndividualEvaluation()

//        if (config.isEnabledImpactCollection()){
//            ei?.updateInitImpactAfterDoCalculateCoverage(calculatedBefore, null, config)
//        }

        return ei
    }


    /**
     * calculated coverage with specified targets.
     *
     * if [allCovered] is true, then ids are ignored, and info on all fully-covered targets
     * are returned. Also, in such case, we do not compute any extra info needed for the search
     *
     * @return null if there were problems in calculating the coverage
     */
    protected abstract fun doCalculateCoverage(
        individual: T,
        targets: Set<Int>,
        allCovered: Boolean
    ) : EvaluatedIndividual<T>?

    /**
     * Compute the fitness function, but only for the covered targets (ie partial heuristics are ignored),
     * and without collecting any general stats.
     *
     * This is an expensive operations, used for post-processing phases, eg test case minimization.
     * Note that during the search we use heuristics to minimize the number of data to retrieve,
     * so there the fitness value is just partial
     */
    fun computeWholeAchievedCoverageForPostProcessing(individual: T) : EvaluatedIndividual<T>?{
        return doCalculateCoverage(individual, setOf(), true)
    }

    private fun calculateIndividualCoverageWithStats(
        individual: T,
        targets: Set<Int>,
        actionsSize: Int
    ) : EvaluatedIndividual<T>?{

        val ei = SearchTimeController.measureTimeMillis(
                { t, ind ->
                    time.reportExecutedIndividualTime(t, actionsSize)
                    ind?.executionTimeMs = t
                },
                {doCalculateCoverage(individual, targets, false)}
        )
        // plugin execution info reporter here, to avoid the time spent by execution reporter
        handleExecutionInfo(ei)
        return ei
    }
    /**
     * Try to reinitialize the SUT. This is done when there are issues
     * in calculating coverage
     */
    protected open fun reinitialize() = false

    /**
     * decide what targets to evaluate during fitness evaluation
     * @param targets indicates prioritized targets if there exists
     */
    open fun targetsToEvaluate(targets: Set<Int>, individual: T) : Set<Int>{
        return targets.plus(archive.notCoveredTargets()).filter { !IdMapper.isLocal(it) }.toSet()
    }

    private fun handleExecutionInfo(ei: EvaluatedIndividual<T>?) {
        ei?:return
        executionInfoReporter.sqlExecutionInfo(ei.individual.seeAllActions(), ei.fitness.databaseExecutions)
        executionInfoReporter.actionExecutionInfo(ei.individual, ei.executionTimeMs, time.evaluatedIndividuals)
    }
}