package org.evomaster.core.search.impact.genemutationupdate

import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Module
import com.google.inject.TypeLiteral
import com.netflix.governator.guice.LifecycleInjector
import org.evomaster.core.BaseModule
import org.evomaster.core.EMConfig
import org.evomaster.core.TestUtils
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.algorithms.MioAlgorithm
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.matchproblem.*
import org.evomaster.core.search.service.Archive
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.mutator.EvaluatedMutation
import org.evomaster.core.search.service.mutator.MutatedGeneSpecification
import org.evomaster.core.search.service.mutator.StandardMutator
import org.evomaster.core.search.service.mutator.genemutation.ArchiveGeneMutator
import org.evomaster.core.search.tracer.ArchiveMutationTrackService
import org.evomaster.core.search.tracer.TrackingHistory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * created by manzh on 2020-09-10
 */
class StringGeneMutationUpdateTest {

    private lateinit var config: EMConfig
    private lateinit var mio: MioAlgorithm<PrimitiveTypeMatchIndividual>
    private lateinit var mutator : StandardMutator<PrimitiveTypeMatchIndividual>
    private lateinit var sampler : PrimitiveTypeMatchSampler
    private lateinit var ff: PrimitiveTypeMatchFitness

    private lateinit var archive : Archive<PrimitiveTypeMatchIndividual>
    private lateinit var agm : ArchiveGeneMutator
    private lateinit var tracker : ArchiveMutationTrackService

    private val budget = 300

    @BeforeEach
    fun init(){
        val injector: Injector = LifecycleInjector.builder()
                .withModules(* arrayOf<Module>(PrimitiveTypeMatchModule(), BaseModule()))
                .build().createInjector()

        mio = injector.getInstance(Key.get(
                object : TypeLiteral<MioAlgorithm<PrimitiveTypeMatchIndividual>>() {}))

        config = injector.getInstance(EMConfig::class.java)
        config.maxActionEvaluations = budget
        config.stoppingCriterion = EMConfig.StoppingCriterion.FITNESS_EVALUATIONS
        config.probOfRandomSampling = 0.0

        sampler = injector.getInstance(PrimitiveTypeMatchSampler::class.java)
        sampler.template = PrimitiveTypeMatchIndividual.stringTemplate()

        ff = injector.getInstance(PrimitiveTypeMatchFitness::class.java)
        ff.type = ONE2M.ONE_EQUAL_WITH_ONE
        archive = injector.getInstance(Key.get(object : TypeLiteral<Archive<PrimitiveTypeMatchIndividual>>(){}))
        mutator = injector.getInstance( Key.get(object : TypeLiteral<StandardMutator<PrimitiveTypeMatchIndividual>>(){}))
        tracker = injector.getInstance(ArchiveMutationTrackService::class.java)

        agm = injector.getInstance(ArchiveGeneMutator::class.java)

        config.enableTrackEvaluatedIndividual = true
        config.probOfArchiveMutation = 1.0
        config.weightBasedMutationRate = true

        config.maxLengthOfTraces = 10
        config.maxlengthOfHistoryForAGM = 10
    }

    @Test
    fun testHistoryExtraction(){
        config.archiveGeneMutation = EMConfig.ArchiveGeneMutation.NONE

        val first = ff.calculateCoverage(sampler.sample(), modifiedSpec = null)!!.also { archive.addIfNeeded(it) }

        val mutated = mutator.mutateAndSave(10, first, archive)

        assertNotNull(mutated.tracking)
        assertEquals(10, mutated.tracking!!.history.size)

        val copy = mutated.copy(tracker.getCopyFilterForEvalInd(mutated))
        val ind = copy.individual.copy() as PrimitiveTypeMatchIndividual

        assertEquals(1, ind.seeGenes().size)
        val geneToMutate = ind.seeGenes().first()

        val mutationInfo = MutatedGeneSpecification()

        val additionalInfo = mutator.mutationConfiguration(
                gene = geneToMutate, individual = ind,
                eval = copy, enableAGS = true, enableAGM = true,
                targets = archive.notCoveredTargets(),  mutatedGene = mutationInfo, includeSameValue = true)

        assertNotNull(additionalInfo)
        assertEquals(10, additionalInfo!!.history.size)
    }


    @Test
    fun testMutationUpdate(){
        val template = PrimitiveTypeMatchIndividual.stringTemplate()
        val specified = listOf("","a","ax","bx","bg","be","bc","ba","bax","baq")
        val history = mutableListOf<EvaluatedIndividual<PrimitiveTypeMatchIndividual>>()
        specified.forEach {
            val ind = template.copy() as PrimitiveTypeMatchIndividual
            TestUtils.doInitializeIndividualForTesting(ind, Randomness().apply { updateSeed(42) })
            (ind.gene as StringGene).value = it
            val eval = ff.calculateCoverage(ind, archive.notCoveredTargets(), null)
            assertNotNull(eval)
            val er = if(history.isNotEmpty()){
                mutator.evaluateMutation(
                        mutated = history.last(),
                        current = eval!!,
                        archive = archive,
                        targets = archive.notCoveredTargets()
                ).apply {
                    assertEquals(EvaluatedMutation.BETTER_THAN, this)
                }
            }else{
                EvaluatedMutation.UNSURE
            }

            eval!!.wrapWithEvaluatedResults(er)
            history.add(eval)
        }

        val current = (history.last().copy(tracker.getCopyFilterForEvalInd(history.last())))
        val th = TrackingHistory<EvaluatedIndividual<PrimitiveTypeMatchIndividual>>(config.maxLengthOfTraces)
        th.history.addAll(history)
        current.wrapWithEvaluatedResults(null)
        current.wrapWithTracking(history.last().evaluatedResult, th)

        val msp = MutatedGeneSpecification()

        val aminfo = mutator.mutationConfiguration(
                gene = current.individual.gene, individual = current.individual,
                eval = current, enableAGS = false,
                enableAGM = true, targets = setOf(0), mutatedGene = msp, includeSameValue =false)
        assertNotNull(aminfo)
        agm.historyBasedValueMutation(aminfo!!, current.individual.gene, listOf(current.individual.gene))
        val mt = (current.individual.gene as StringGene).value
        assert(
                (mt.length == 3 && mt[2].toInt() <= 'x'.toInt()/2.0 + 'q'.toInt()/2.0) || mt.length == 4 || mt.length == 2
        )

    }

}