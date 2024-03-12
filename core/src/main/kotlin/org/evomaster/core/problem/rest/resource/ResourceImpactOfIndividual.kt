package org.evomaster.core.problem.rest.resource

import org.evomaster.core.sql.SqlAction
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.impact.impactinfocollection.ActionStructureImpact
import org.evomaster.core.search.impact.impactinfocollection.ImpactsOfAction
import org.evomaster.core.search.impact.impactinfocollection.ImpactsOfIndividual
import org.evomaster.core.search.impact.impactinfocollection.InitializationGroupedActionsImpacts
import org.evomaster.core.search.impact.impactinfocollection.value.numeric.IntegerGeneImpact

/**
 * created by manzhang on 2021/10/21
 */
class ResourceImpactOfIndividual : ImpactsOfIndividual {

    /**
     * impact of changing size of the resource in the individual
     * key - a name of resource, i.e., path
     * value - impact
     */
    val resourceSizeImpact: MutableMap<String, IntegerGeneImpact>

    /**
     * impact of changing size of any resource in the individual
     */
    val anyResourceSizeImpact : IntegerGeneImpact
    /**
     * impact of changing size of the table in the initialization of individual.
     *        note that here we do not consider the table in resource handling,
     *        since those could be handled by [resourceSizeImpact]
     * key - a name of table
     * value - impact
     */
    val sqlTableSizeImpact: MutableMap<String, IntegerGeneImpact>

    /**
     * impact of changing size of any sql in the individual
     */
    val anySqlTableSizeImpact : IntegerGeneImpact

    constructor(
        initActionImpacts: MutableMap<String, InitializationGroupedActionsImpacts>,
        fixedMainActionImpacts: MutableList<ImpactsOfAction>,
        dynamicMainActionImpacts: MutableList<ImpactsOfAction>,
        impactsOfStructure: ActionStructureImpact = ActionStructureImpact("StructureSize"),
        resourceSizeImpact: MutableMap<String, IntegerGeneImpact>,
        sqlTableImpact: MutableMap<String, IntegerGeneImpact>,
        anyResourceSizeImpact: IntegerGeneImpact,
        anySqlTableSizeImpact: IntegerGeneImpact

    ) : super(initActionImpacts, fixedMainActionImpacts, dynamicMainActionImpacts, impactsOfStructure) {
        this.resourceSizeImpact = resourceSizeImpact
        this.sqlTableSizeImpact = sqlTableImpact
        this.anyResourceSizeImpact = anyResourceSizeImpact
        this.anySqlTableSizeImpact = anySqlTableSizeImpact
    }

    constructor(individual: RestIndividual, initActionTypes: List<String>, abstractInitializationGeneToMutate: Boolean, fitnessValue: FitnessValue?)
            : super(individual, initActionTypes, abstractInitializationGeneToMutate, fitnessValue) {
        resourceSizeImpact = mutableMapOf<String, IntegerGeneImpact>().apply {
            individual.seeResource(RestIndividual.ResourceFilter.ALL).forEach { r->
                putIfAbsent(r, IntegerGeneImpact("size"))
            }
        }
        sqlTableSizeImpact = mutableMapOf<String, IntegerGeneImpact>().apply {
            individual.seeInitializingActions().filterIsInstance<SqlAction>().filterNot { it.representExistingData }.forEach { d->
                putIfAbsent(d.table.name, IntegerGeneImpact("size"))
            }
        }
        anyResourceSizeImpact = IntegerGeneImpact("anyResourceSizeImpact")
        anySqlTableSizeImpact = IntegerGeneImpact("anySqlTableSizeImpact")
    }

    /**
     * @return a copy of [this]
     */
    override fun copy(): ResourceImpactOfIndividual {
        return ResourceImpactOfIndividual(
                initActionImpacts.map { it.key to it.value.copy() }.toMap().toMutableMap(),
                fixedMainActionImpacts.map { it.copy() }.toMutableList(),
                dynamicMainActionImpacts.map { it.copy() }.toMutableList(),
                impactsOfStructure.copy(),
                mutableMapOf<String, IntegerGeneImpact>().apply {
                    putAll(resourceSizeImpact.map { it.key to it.value.copy() })
                },
                mutableMapOf<String, IntegerGeneImpact>().apply {
                    putAll(sqlTableSizeImpact.map { it.key to it.value.copy() })
                },
                anyResourceSizeImpact.copy(),
                anySqlTableSizeImpact.copy()
        )
    }

    /**
     * @return a clone of [this]
     */
    override fun clone(): ResourceImpactOfIndividual {
        return ResourceImpactOfIndividual(
                initActionImpacts.map { it.key to it.value.clone() }.toMap().toMutableMap(),
                fixedMainActionImpacts.map { it.clone() }.toMutableList(),
                dynamicMainActionImpacts.map { it.clone() }.toMutableList(),
                impactsOfStructure.clone(),
                mutableMapOf<String, IntegerGeneImpact>().apply {
                    putAll(resourceSizeImpact.map { it.key to it.value.clone() })
                },
                mutableMapOf<String, IntegerGeneImpact>().apply {
                    putAll(sqlTableSizeImpact.map { it.key to it.value.clone() })
                },
                anyResourceSizeImpact.clone(),
                anySqlTableSizeImpact.clone()
        )
    }

    /**
     * count an impact of changing resource size
     */
    fun countResourceSizeImpact(previous: RestIndividual, current: RestIndividual, noImpactTargets: Set<Int>, impactTargets: Set<Int>, improvedTargets: Set<Int>, onlyManipulation: Boolean = false) {
        val currentRs = current.seeResource(RestIndividual.ResourceFilter.ALL)
        val previousRs = previous.seeResource(RestIndividual.ResourceFilter.ALL)
        var anyResourceChange = false
        currentRs.toSet().forEach { cr ->
            val rImpact = resourceSizeImpact.getOrPut(cr){IntegerGeneImpact("size")}
            if (currentRs.count { it == cr } != previousRs.count { it == cr }) {
                if (!anyResourceChange){
                    anyResourceSizeImpact.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)
                    anyResourceChange = true
                }
                rImpact.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)
            }
        }

        val currentTs = current.seeInitializingActions().filterIsInstance<SqlAction>().filterNot { it.representExistingData }.map { it.table.name }
        val previousTs = previous.seeInitializingActions().filterIsInstance<SqlAction>().filterNot { it.representExistingData }.map { it.table.name }
        var anySqlChange = false
        currentTs.toSet().forEach { cr ->
            val tImpact = sqlTableSizeImpact.getOrPut(cr){IntegerGeneImpact("size")}
            if (currentTs.count { it == cr } != previousTs.count { it == cr }) {
                if (!anySqlChange){
                    anySqlTableSizeImpact.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)
                    anySqlChange = true
                }
                tImpact.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)
            }
        }

        // shall I remove impacts of deleted resources or table?
    }
}