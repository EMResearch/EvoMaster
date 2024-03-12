package org.evomaster.core.problem.rest.individual

import com.google.inject.*
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.problem.rest.service.*
import org.evomaster.core.search.action.ActionFilter
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.impact.impactinfocollection.ImpactUtils
import org.evomaster.core.search.impact.impactinfocollection.ImpactsOfIndividual
import org.evomaster.core.search.service.mutator.EvaluatedMutation
import org.evomaster.core.search.service.mutator.StandardMutator
import org.junit.jupiter.api.Assertions.*
import kotlin.math.min

/**
 * tests are for checking resource-based solution with enabled hypermutation
 */
class RestIndividualResourceTest : RestIndividualTestBase() {

    private lateinit var sampler: ResourceSampler
    private lateinit var mutator: ResourceRestMutator
    private lateinit var rm: ResourceManageService
    private lateinit var ff: RestResourceFitness

    override fun getProblemModule() = ResourceRestModule(false)
    override fun getMutator(): StandardMutator<RestIndividual> = mutator
    override fun getFitnessFunction(): AbstractRestFitness<RestIndividual> = ff
    override fun getSampler(): AbstractRestSampler = sampler


    override fun initService(injector: Injector) {

        sampler = injector.getInstance(ResourceSampler::class.java)
        mutator = injector.getInstance(ResourceRestMutator::class.java)

        rm = injector.getInstance(ResourceManageService::class.java)
        ff = injector.getInstance(RestResourceFitness::class.java)

    }

    override fun extraSampledIndividualCheck(index: Int, individual: RestIndividual) {
        assertTrue(individual.isInitialized())
    }


    override fun extraMutatedIndividualCheck(
        evaluated: Int, copyOfImpact: ImpactsOfIndividual?,
        original: EvaluatedIndividual<RestIndividual>, mutated: EvaluatedIndividual<RestIndividual>
    ) {
        assertTrue(mutated.individual.isInitialized())

        checkTracking(evaluated + 1, mutated)

        checkImpactUpdate(evaluated, copyOfImpact, original, mutated)
    }

    private fun checkTracking(evaluated: Int, mutated: EvaluatedIndividual<RestIndividual>) {
        mutated.tracking.apply {
            assertNotNull(this)
            assertEquals(min(evaluated, config.maxLengthOfTraces), this!!.history.size)
            assertNotNull(this.history.last().evaluatedResult)
            // with faked remote controller, it should always return better results
            assertEquals(EvaluatedMutation.BETTER_THAN, this.history.last().evaluatedResult)
        }
    }

    private fun checkImpactUpdate(
        evaluated: Int, copyOfImpact: ImpactsOfIndividual?,
        original: EvaluatedIndividual<RestIndividual>, mutated: EvaluatedIndividual<RestIndividual>
    ) {

        if (evaluated <= 1) return

        assertNotNull(mutated)
        assertNotNull(mutated.impactInfo)

        val mutatedImpact = mutated.impactInfo!!

        val existingData = mutatedImpact.getSQLExistingData()
        assertEquals(
            existingData,
            mutated.individual.seeInitializingActions().filterIsInstance<SqlAction>().count { it.representExistingData })

        val currentInit =
            mutatedImpact.initActionImpacts.values.sumOf { it.getOriginalSize(includeExistingSQLData = true) }

        val origInit = original.individual.seeInitializingActions()
        val mutatedInit = mutated.individual.seeInitializingActions()

        assertEquals(mutatedInit.size, currentInit)

        // check whether impact info is consistent with individual after mutation
        mutated.individual.seeInitializingActions().filterIsInstance<SqlAction>().forEachIndexed { index, dbAction ->
            if (!dbAction.representExistingData) {
                val impact = mutatedImpact.initActionImpacts[dbAction::class.java.name]!!.getImpactOfAction(dbAction.getName(), index)
                assertNotNull(impact)
            }
        }

        mutated.individual.seeActions(ActionFilter.NO_INIT).forEachIndexed { index, action ->
            val impact = mutatedImpact.fixedMainActionImpacts[index]
            assertEquals(action.getName(), impact.actionName)
            action.seeTopGenes().forEach { g ->
                val geneId = ImpactUtils.generateGeneId(mutated.individual, g)
                val geneImpact = impact.get(geneId, action.getName())
                assertNotNull(geneImpact)
            }
        }

        val anyNewDbActions = mutatedInit.size - origInit.size
        assertFalse(anyNewDbActions < 0, "DbAction should not be removed with the current strategy for REST problem")

        if (anyNewDbActions == 0) {

            if (mutated.trackOperator?.operatorTag() == RestResourceStructureMutator::class.java.simpleName) {
                //TODO might check the structure impact
            } else if (mutated.trackOperator?.operatorTag() == ResourceRestMutator::class.java.simpleName) {
                var improved = 0
                var anyMutated = 0
                mutated.individual.seeActions(ActionFilter.ALL).forEachIndexed { index, action ->
                    if (action !is SqlAction || !action.representExistingData) {
                        action.seeTopGenes().filter { it.isMutable() }.forEach { g ->
                            val impactId = ImpactUtils.generateGeneId(mutated.individual, g)
                            val fromInit = mutatedInit.contains(action)
                            val actionIndex = if (fromInit) index else (index - mutatedInit.size)
                            val fixed = mutated.individual.seeFixedMainActions().contains(action)
                            val ogeneImpact = copyOfImpact!!.getGene(
                                actionName = action.getName(),
                                initActionClassName = action::class.java.name,
                                geneId = impactId,
                                actionIndex = actionIndex,
                                localId = action.getLocalId(),
                                fixedIndexedAction = fixed,
                                fromInitialization = fromInit
                            )
                            assertNotNull(ogeneImpact)
                            val mgeneImpact = mutatedImpact.getGene(
                                actionName = action.getName(),
                                initActionClassName = action::class.java.name,
                                geneId = impactId,
                                actionIndex = actionIndex,
                                localId = action.getLocalId(),
                                fixedIndexedAction = fixed,
                                fromInitialization = fromInit
                            )
                            assertNotNull(impactId)
                            val mutatedTimes =
                                mgeneImpact!!.shared.timesToManipulate - ogeneImpact!!.shared.timesToManipulate
                            val impactTimes =
                                mgeneImpact.shared.timesOfImpact.size - ogeneImpact.shared.timesOfImpact.size
                            assertTrue(mutatedTimes == 1 || mutatedTimes == 0)
                            assertTrue(impactTimes >= 0)
                            improved += impactTimes
                            anyMutated += mutatedTimes
                        }
                    }
                }
                if (anyMutated > 0)
                    assertTrue(improved > 0)

            } else {
                fail("the operator (${mutated.trackOperator?.operatorTag() ?: "null"}) is not expected")
            }
        }

        if (searchTimeController.evaluatedActions > 20 || searchTimeController.percentageUsedBudget() >= 0.1) {
            /*
                newly additional dbaction would affect the impact collections
                then disable after 10% used budget or after 20 rest action evaluations
             */
            employFakeDbHeuristicResult = false
        }


    }
}