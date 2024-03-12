package org.evomaster.core.search.impact.genemutationupdate

import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Module
import com.google.inject.TypeLiteral
import com.netflix.governator.guice.LifecycleInjector
import org.evomaster.core.BaseModule
import org.evomaster.core.EMConfig
import org.evomaster.core.search.algorithms.MioAlgorithm
import org.evomaster.core.search.matchproblem.*
import org.evomaster.core.search.service.Archive
import org.evomaster.core.search.service.mutator.EvaluatedMutation
import org.evomaster.core.search.service.mutator.MutatedGeneSpecification
import org.evomaster.core.search.service.mutator.StandardMutator
import org.evomaster.core.search.service.mutator.genemutation.ArchiveGeneMutator
import org.evomaster.core.search.service.mutator.genemutation.mutationupdate.LongMutationUpdate
import org.evomaster.core.search.tracer.ArchiveMutationTrackService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * created by manzh on 2020-09-10
 */
class IntegerGeneMutationUpdateTest {

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
        sampler.template = PrimitiveTypeMatchIndividual.intTemplate()

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
        val specified = listOf(Int.MAX_VALUE/2, Int.MAX_VALUE/4, 0, Int.MIN_VALUE/4, Int.MIN_VALUE/2)

        val mutationinfo = LongMutationUpdate(false, Int.MIN_VALUE, Int.MAX_VALUE)

        mutationinfo.updateOrRestBoundary(specified.map {
            it.toLong() to EvaluatedMutation.BETTER_THAN.value
        })

        assertEquals(Int.MIN_VALUE, mutationinfo.preferMin.toInt())
        assertEquals(specified.size-1, mutationinfo.updateTimes)
        assertEquals(0, mutationinfo.resetTimes)
        assertEquals(specified.subList(3, specified.size).average().toInt(), mutationinfo.preferMax.toInt())
    }

}