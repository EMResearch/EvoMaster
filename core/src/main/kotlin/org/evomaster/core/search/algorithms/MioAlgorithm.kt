package org.evomaster.core.search.algorithms

import org.evomaster.core.EMConfig
import org.evomaster.core.Lazy
import org.evomaster.core.search.Individual
import org.evomaster.core.search.service.SearchAlgorithm

/**
 * Many Independent Objective (MIO) Algorithm
 */
class MioAlgorithm<T> : SearchAlgorithm<T>() where T : Individual {

    override fun getType(): EMConfig.Algorithm {
        return EMConfig.Algorithm.MIO
    }

    override fun setupBeforeSearch() {
        // Nothing needs to be done before starting the search
    }

    override fun searchOnce() {

            val randomP = apc.getProbRandomSampling()

            if(archive.isEmpty()
                    || sampler.hasSpecialInit()
                    || randomness.nextBoolean(randomP)) {

                val ind = sampler.sample()

                Lazy.assert { ind.isInitialized() && ind.searchGlobalState!=null }

                ff.calculateCoverage(ind, modifiedSpec = null)?.run {

                    archive.addIfNeeded(this)
                    sampler.feedback(this)
                    if (sampler.isLastSeededIndividual())
                        archive.archiveCoveredStatisticsBySeededTests()
                }

                return
            }

            val ei = archive.sampleIndividual()

            val nMutations = apc.getNumberOfMutations()

            getMutatator().mutateAndSave(nMutations, ei, archive)


    }
}
