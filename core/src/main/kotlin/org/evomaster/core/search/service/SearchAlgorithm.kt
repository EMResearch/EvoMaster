package org.evomaster.core.search.service

import com.google.inject.Inject
import org.evomaster.core.EMConfig
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.search.Individual
import org.evomaster.core.search.Solution
import org.evomaster.core.search.service.mutator.Mutator


abstract class SearchAlgorithm<T> where T : Individual {

    @Inject
    protected lateinit var sampler : Sampler<T>

    @Inject
    protected lateinit var ff : FitnessFunction<T>

    @Inject
    protected lateinit var randomness : Randomness

    @Inject
    protected lateinit var time : SearchTimeController

    @Inject
    protected lateinit var archive: Archive<T>

    @Inject
    protected lateinit var apc: AdaptiveParameterControl

    @Inject
    protected lateinit var config: EMConfig


    @Inject(optional = true)
    private lateinit var mutator: Mutator<T>

    @Inject
    private lateinit var minimizer: Minimizer<T>

    @Inject
    private lateinit var ssu: SearchStatusUpdater

    private var lastSnapshot = 0

    protected fun getMutatator() : Mutator<T> {
        return mutator
    }

    /**
     * This method does a single step in the search process, each algorithm must implement its own.
     */
    abstract fun searchOnce()

    /**
     * Here goes all the implementation needed for the algorithm to setup before running the search
     */
    abstract fun setupBeforeSearch()

    /**
     * This method does the full search invoking searchOnce() on each iteration.
     * The method writeTestsSnapshot send as parameter is the code that is executed to write the obtained tests as snapshots.
     * If writing snapshots of tests is enabled, then this method will be invoked when configured after running the searchOnce method.
     */
    fun search(writeTestsSnapshot: ((s: Solution<T>, snapshotTimestamp: String) -> Unit)? = null): Solution<T> {

        time.startSearch()

        setupBeforeSearch()

        while (time.shouldContinueSearch()) {

            searchOnce()

            if (needsToSnapshot() && writeTestsSnapshot != null) {
                lastSnapshot = time.getElapsedSeconds()
                val partialSolution = archive.extractSolution()
                writeTestsSnapshot(partialSolution, lastSnapshot.toString())
            }
        }

        handleAfterSearch()

        return archive.extractSolution()
    }

    private fun handleAfterSearch() {

        time.doStopRecording()

        ssu.enabled = false

        if(config.minimize){
            minimizer.doStartTheTimer()
            minimizer.minimizeMainActionsPerCoveredTargetInArchive()
            minimizer.pruneNonNeededDatabaseActions()
            minimizer.simplifyActions()
            val seconds = minimizer.passedTimeInSecond()
            LoggingUtil.getInfoLogger().info("Minimization phase took $seconds seconds")
        }

        if(config.addPreDefinedTests) {
            for (ind in sampler.getPreDefinedIndividuals()) {
                ff.calculateCoverage(ind, modifiedSpec = null)?.run {
                    archive.addIfNeeded(this)
                }
            }
        }
    }

    private fun needsToSnapshot(): Boolean {
        val isSnapshotEnabled = config.enableWriteSnapshotTests
        val snapshotPeriod = config.writeSnapshotTestsIntervalInSeconds

        return isSnapshotEnabled && time.getElapsedSeconds() - lastSnapshot > snapshotPeriod
    }

    abstract fun getType() : EMConfig.Algorithm
}