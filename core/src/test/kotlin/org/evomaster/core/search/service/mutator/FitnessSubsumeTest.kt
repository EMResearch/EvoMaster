package org.evomaster.core.search.service.mutator

import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.netflix.governator.guice.LifecycleInjector
import org.evomaster.core.BaseModule
import org.evomaster.core.EMConfig
import org.evomaster.core.TestUtils
import org.evomaster.core.search.algorithms.onemax.ManipulatedOneMaxModule
import org.evomaster.core.search.algorithms.onemax.ManipulatedOneMaxMutator
import org.evomaster.core.search.algorithms.onemax.OneMaxFitness
import org.evomaster.core.search.algorithms.onemax.OneMaxIndividual
import org.evomaster.core.search.service.Archive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * test subsume fun of fitness
 */
class FitnessSubsumeTest {

    private val injector = LifecycleInjector.builder()
            .withModules(ManipulatedOneMaxModule(), BaseModule())
            .build().createInjector()

    private val ff : OneMaxFitness = injector.getInstance(OneMaxFitness::class.java)

    private val config : EMConfig = injector.getInstance(EMConfig::class.java)

    private val mutator : ManipulatedOneMaxMutator = injector.getInstance(ManipulatedOneMaxMutator::class.java)
    private val archive : Archive<OneMaxIndividual> = injector.getInstance(
            Key.get(
                    object : TypeLiteral<Archive<OneMaxIndividual>> (){}
            )
    )

    @Test
    fun currentCoverOneMoreTargets(){
        val n = 10
        val current = OneMaxIndividual(n)
        TestUtils.doInitializeIndividualForTesting(current)
        (0 until n).forEach {
            current.setValue(it, 0.25)
        }

        val mutated = current.copy() as OneMaxIndividual
        mutated.setValue(0, 0.0)

        assert(ff.calculateCoverage(current, modifiedSpec = null)!!.fitness.subsumes(ff.calculateCoverage(
            mutated,
            modifiedSpec = null
        )!!.fitness, (0 until n).toSet(),config))
    }

    @Test
    fun currentReachBetter(){
        val n = 10
        val current = OneMaxIndividual(n)
        TestUtils.doInitializeIndividualForTesting(current)
        (0 until n).forEach {
            current.setValue(it, 0.75)
        }

        val mutated = current.copy() as OneMaxIndividual
        mutated.setValue(0, 0.5)

        assert(ff.calculateCoverage(current, modifiedSpec = null)!!.fitness.subsumes(ff.calculateCoverage(
            mutated,
            modifiedSpec = null
        )!!.fitness, (0 until n).toSet(),config))

    }

    @Test
    fun mutatedReachBetter(){
        val n = 10
        val current = OneMaxIndividual(n)
        TestUtils.doInitializeIndividualForTesting(current)
        (0 until n).forEach {
            current.setValue(it, 0.5)
        }

        val mutated = current.copy() as OneMaxIndividual
        mutated.setValue(0, 0.75)

        val evaluatedCurrent = ff.calculateCoverage(current, modifiedSpec = null)!!
        val evaluatedMutated = ff.calculateCoverage(mutated, modifiedSpec = null)!!
        assertFalse(evaluatedCurrent.fitness.subsumes(evaluatedMutated.fitness, (0 until n).toSet(),config))
        assert(evaluatedMutated.fitness.subsumes(evaluatedCurrent.fitness, (0 until n).toSet(), config))

        assertEquals(EvaluatedMutation.BETTER_THAN, mutator.evaluateMutation(
                mutated = evaluatedMutated,
                current = evaluatedCurrent,
                targets = (0 until n).toSet(),
                archive = archive
        ))
    }

    @Test
    fun mutatedCoverOneMoreTarget(){
        val n = 10
        val current = OneMaxIndividual(n)
        TestUtils.doInitializeIndividualForTesting(current)
        (1 until n).forEach {
            current.setValue(it, 0.5)
        }

        val mutated = current.copy() as OneMaxIndividual
        mutated.setValue(0, 0.75)

        val evaluatedCurrent = ff.calculateCoverage(current, modifiedSpec = null)!!
        val evaluatedMutated = ff.calculateCoverage(mutated, modifiedSpec = null)!!
        assertFalse(evaluatedCurrent.fitness.subsumes(evaluatedMutated.fitness, (0 until n).toSet(),config))
        assert(evaluatedMutated.fitness.subsumes(evaluatedCurrent.fitness, (0 until n).toSet(), config))

        assertEquals(EvaluatedMutation.BETTER_THAN, mutator.evaluateMutation(
                mutated = evaluatedMutated,
                current = evaluatedCurrent,
                targets = (0 until n).toSet(),
                archive = archive
        ))
    }
}