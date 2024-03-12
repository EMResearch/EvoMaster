package org.evomaster.core.search.impact.impactinfocollection.value

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.impact.impactinfocollection.*
import org.evomaster.core.search.impact.impactinfocollection.value.numeric.BinaryGeneImpact
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * created by manzh on 2019-09-09
 *
 * @property employBinding is to collect whether to bind values.
 * @property employSpecialization is to collect whether to employ the specialization.
 */
class StringGeneImpact (sharedImpactInfo: SharedImpactInfo,
                        specificImpactInfo: SpecificImpactInfo,
                        val employBinding : BinaryGeneImpact = BinaryGeneImpact("employBinding"),
                        val employSpecialization : BinaryGeneImpact = BinaryGeneImpact("employSpecialization"),
                        /**
                         * impacts on its specific type
                         * it might lead to an issue when the type of gene is dynamic, thus the type of the current might differ from the type of the previous
                         *
                         * ? we might need to copy [specializationGeneImpact] for each Gene instead of clone
                         */
                        //var specializationGeneImpact : MutableList<Impact>
                        var hierarchySpecializationImpactInfo: HierarchySpecializationImpactInfo? = null
) : GeneImpact(sharedImpactInfo, specificImpactInfo){

    companion object{
        private val log: Logger = LoggerFactory.getLogger(StringGeneImpact::class.java)
        private const val NEVER_EMPLOY_SPECIALIZATION = -1
    }

    constructor(id: String, gene : StringGene)
            : this(
            sharedImpactInfo = SharedImpactInfo(id),
            specificImpactInfo = SpecificImpactInfo(),
            hierarchySpecializationImpactInfo =
                if (gene.specializationGenes.isEmpty()) null
                else HierarchySpecializationImpactInfo(null, gene.specializationGenes.map { ImpactUtils.createGeneImpact(it, it.name) }.toMutableList()))

    fun getSpecializationImpacts() = hierarchySpecializationImpactInfo?.flattenImpacts()?: listOf<Impact>()

    override fun copy(): StringGeneImpact {
        return StringGeneImpact(
                shared.copy(),
                specific.copy(),
                employBinding.copy(),
                employSpecialization.copy(),
                hierarchySpecializationImpactInfo?.copy())
    }

    override fun clone(): StringGeneImpact {
        return StringGeneImpact(
                shared.clone(),
                specific.clone(),
                employBinding.clone(),
                employSpecialization.clone(),
                hierarchySpecializationImpactInfo)
    }

    override fun validate(gene: Gene): Boolean = gene is StringGene

    override fun countImpactWithMutatedGeneWithContext(gc: MutatedGeneWithContext, noImpactTargets : Set<Int>, impactTargets: Set<Int>, improvedTargets: Set<Int>, onlyManipulation: Boolean) {
        countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)

        if(gc.current !is StringGene)
            throw IllegalArgumentException("incorrect mutation info for the gene")

        if (gc.previous != null){
            if (gc.previous !is StringGene)
                throw IllegalArgumentException("incorrect mutation info for the gene")
        }

        if (gc.previous == null && impactTargets.isNotEmpty()) return

        val allImpacts = hierarchySpecializationImpactInfo?.flattenImpacts()

        val currentSelect = gc.current.selectedSpecialization
        employSpecialization.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)
        val taintImpact = if (currentSelect == NEVER_EMPLOY_SPECIALIZATION){ employSpecialization.falseValue }else employSpecialization.trueValue
        taintImpact.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)

        if (currentSelect != NEVER_EMPLOY_SPECIALIZATION && allImpacts?.size == gc.current.specializationGenes.size){

            val sImpact = allImpacts[gc.current.selectedSpecialization]
            val previousSelect = (gc.previous as? StringGene)?.selectedSpecialization

            val mutatedGeneWithContext = MutatedGeneWithContext(
                current =  gc.current.specializationGenes[currentSelect],
                actionName = "none",
                position = -1,
                previous = if (previousSelect == currentSelect) gc.previous.specializationGenes[previousSelect] else null,
                numOfMutatedGene = 1,
            )
            if ((sImpact as GeneImpact).validate(mutatedGeneWithContext.current)){
                sImpact.countImpactWithMutatedGeneWithContext(
                        mutatedGeneWithContext, noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation
                )
            }else{
                log.warn("Handling specialization at (${gc.current.selectedSpecialization}) for StringGene (name:${gc.current.name}): Gene (name:${mutatedGeneWithContext.current.name}, type:${mutatedGeneWithContext.current::class.java.simpleName}) and its impact (type:${sImpact::class.java.simpleName}) do not match.")
            }

        }

        employBinding.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)
        val ft = if (gc.current.bindingIds.isEmpty()) employBinding.falseValue else employBinding.trueValue
        ft.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)

    }


    override fun flatViewInnerImpact(): Map<String, Impact> {
        val map = mutableMapOf<String, Impact>()

        listOf(employBinding, employSpecialization).plus((hierarchySpecializationImpactInfo?.flattenImpacts()?: listOf<Impact>())).forEach { s ->
            map.putIfAbsent("${getId()}-${s.getId()}", s)
            if (s is GeneImpact && s.flatViewInnerImpact().isNotEmpty())
                s.flatViewInnerImpact().forEach { (t, u) ->
                    map.putIfAbsent("${getId()}-$t", u)
                }
        }

        return map
    }

    override fun syncImpact(previous: Gene?, current: Gene) {
        check(previous, current)
        if (hierarchySpecializationImpactInfo == null){
            if ((current as StringGene).specializationGenes.isNotEmpty()){
                hierarchySpecializationImpactInfo = HierarchySpecializationImpactInfo(null, current.specializationGenes.map { ImpactUtils.createGeneImpact(it, it.name) }.toMutableList())
                if (current.specializationGenes.size != getSpecializationImpacts().size){
                    log.warn("invalid initialization of specializationGenes of string gene")
                }
            }
        }else{
            val currentImpact = hierarchySpecializationImpactInfo!!.flattenImpacts().size
            if ((current as StringGene).specializationGenes.size > currentImpact){
                val added = current.specializationGenes.subList(currentImpact, current.specializationGenes.size)
                hierarchySpecializationImpactInfo = hierarchySpecializationImpactInfo!!.next(added.toMutableList())
            }else if (previous != null && current.specializationGenes.size < (previous as StringGene).specializationGenes.size){
                log.info("some specializations of StringGene are removed {},{}", current.specializationGenes.size, previous.specializationGenes.size)
            }else if(previous == null){
                log.info("the previous gene is null")
                if (current.specializationGenes.size != getSpecializationImpacts().size){
                    log.warn("invalid initialization of specializationGenes of string gene")
                }
            }
        }
    }

}