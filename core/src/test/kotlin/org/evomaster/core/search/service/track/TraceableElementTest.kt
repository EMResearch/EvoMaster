package org.evomaster.core.search.service.track

import com.google.inject.*
import com.netflix.governator.guice.LifecycleInjector
import org.evomaster.core.BaseModule
import org.evomaster.core.EMConfig
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.algorithms.onemax.*
import org.evomaster.core.search.service.Archive
import org.evomaster.core.search.service.mutator.EvaluatedMutation
import org.evomaster.core.search.tracer.ArchiveMutationTrackService

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach


/**
 * created by manzh on 2020-06-21
 */
class TraceableElementTest {

    private lateinit var config: EMConfig
    private lateinit var sampler : OneMaxSampler
    private lateinit var ff : OneMaxFitness
    private lateinit var mutator : ManipulatedOneMaxMutator

    private lateinit var archive : Archive<OneMaxIndividual>
    private lateinit var tracker : ArchiveMutationTrackService

    @BeforeEach
    fun init(){
        val injector: Injector = LifecycleInjector.builder()
                .withModules(* arrayOf<Module>(ManipulatedOneMaxModule(), BaseModule()))
                .build().createInjector()

        config = injector.getInstance(EMConfig::class.java)
        sampler = injector.getInstance(OneMaxSampler::class.java)
        ff = injector.getInstance(OneMaxFitness::class.java)
        mutator = injector.getInstance(ManipulatedOneMaxMutator::class.java)

        archive = injector.getInstance(
                Key.get(
                        object : TypeLiteral<Archive<OneMaxIndividual>>(){}
                )
        )

        tracker = injector.getInstance(ArchiveMutationTrackService::class.java)

    }

    // disable tracking individual and evaluated individual
    @Test
    fun testOneMaxIndividualWithFF(){
        config.enableTrackIndividual = false
        config.enableTrackEvaluatedIndividual = false

        val inds10 = (0 until 10).map { sampler.sample()}
        assert(inds10.all { it.trackOperator == null })

        val evalInds10 = inds10.map { ff.calculateCoverage(it, modifiedSpec = null)!! }
        assert(evalInds10.all { it.trackOperator == null && it.tracking == null })
    }

    // enable tracking evaluated individual but disable individual
    @Test
    fun testOneMaxIndividualWithFT(){
        config.enableTrackIndividual = false
        config.enableTrackEvaluatedIndividual = true
        config.maxActionEvaluations = 100
        config.stoppingCriterion = EMConfig.StoppingCriterion.FITNESS_EVALUATIONS
        config.maxLengthOfTraces = 20
        config.probOfArchiveMutation = 0.0
        config.weightBasedMutationRate = false
        config.testSuiteSplitType = EMConfig.TestSuiteSplitType.NONE
        config.seed = 42
        config.useTimeInFeedbackSampling = false

        val inds10 = (0 until 10).map { ff.calculateCoverage(sampler.sample(), modifiedSpec = null)!!.also { archive.addIfNeeded(it) }}
        assert(inds10.all { it.trackOperator != null })


        val eval = mutator.mutateAndSave(10, inds10[1], archive)
        assertNotNull(eval.trackOperator)
        assertNotNull(eval.tracking)
        assertEquals(11, eval.tracking!!.history.size)

        val eval2 = mutator.mutateAndSave(5, eval, archive)

        assertNotNull(eval2.trackOperator)
        assertNotNull(eval2.tracking)
        assertEquals(16, eval2.tracking!!.history.size)

        assertEquals(eval.tracking, eval2.tracking)

        // with worsening mutator, all 16 history should be -1..0
        assertEquals(16, eval2.getLast<EvaluatedIndividual<OneMaxIndividual>>(16, -1..0).size)

        val first = eval2.tracking!!.history.first()

        mutator.improve = true
        val eval3 = mutator.mutateAndSave(5, eval2, archive)

        assertNotNull(eval3.trackOperator)
        assertNotNull(eval3.tracking)
        assertEquals(20, eval3.tracking!!.history.size)

        assertFalse(eval3.tracking!!.history.contains(first))
        assertEquals(eval2.tracking, eval3.tracking)

        assert(eval3.getLast<EvaluatedIndividual<OneMaxIndividual>>(5, EvaluatedMutation.range()).none { it.evaluatedResult == null || it.evaluatedResult == EvaluatedMutation.WORSE_THAN })
    }

    // enable tracking individual and but disable evaluated individual
    @Test
    fun testEvaluatedOneMaxIndividualWithFT(){
        config.enableTrackEvaluatedIndividual = false
        config.enableTrackIndividual = true
        config.stoppingCriterion = EMConfig.StoppingCriterion.FITNESS_EVALUATIONS
        config.probOfArchiveMutation = 0.0
        config.weightBasedMutationRate = false

        tracker.postConstruct()

        val inds10 = (0 until 10).map { ff.calculateCoverage(sampler.sample(), modifiedSpec = null)!!.also { archive.addIfNeeded(it) } }

        assert(inds10.all { it.trackOperator != null && it.tracking == null})

        val eval = mutator.mutateAndSave(10, inds10[1], archive)
        assertNull(eval.tracking)
        assertNotNull(eval.individual.tracking)
        assertEquals(10, eval.individual.tracking!!.history.size)
    }

}