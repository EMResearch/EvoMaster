package org.evomaster.core.search.impact.impactinfocollection

import org.evomaster.core.search.action.Action
import org.evomaster.core.search.Individual
import org.evomaster.core.search.action.ActionFilter
import org.evomaster.core.search.gene.*
import org.evomaster.core.search.gene.sql.*
import org.evomaster.core.search.impact.impactinfocollection.sql.*
import org.evomaster.core.search.impact.impactinfocollection.value.*
import org.evomaster.core.search.impact.impactinfocollection.value.collection.ArrayGeneImpact
import org.evomaster.core.search.impact.impactinfocollection.value.collection.MapGeneImpact
import org.evomaster.core.search.impact.impactinfocollection.value.collection.EnumGeneImpact
import org.evomaster.core.search.impact.impactinfocollection.value.date.DateGeneImpact
import org.evomaster.core.search.impact.impactinfocollection.value.date.DateTimeGeneImpact
import org.evomaster.core.search.impact.impactinfocollection.value.date.TimeGeneImpact
import org.evomaster.core.search.impact.impactinfocollection.value.numeric.*
import org.evomaster.core.Lazy
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.problem.externalservice.ApiExternalServiceAction
import org.evomaster.core.problem.util.ParamUtil
import org.evomaster.core.search.gene.collection.*
import org.evomaster.core.search.gene.datetime.DateGene
import org.evomaster.core.search.gene.datetime.DateTimeGene
import org.evomaster.core.search.gene.datetime.TimeGene
import org.evomaster.core.search.gene.numeric.*
import org.evomaster.core.search.gene.optional.CustomMutationRateGene
import org.evomaster.core.search.gene.optional.OptionalGene
import org.evomaster.core.search.gene.optional.NullableGene
import org.evomaster.core.search.gene.regex.*
import org.evomaster.core.search.gene.root.CompositeFixedGene
import org.evomaster.core.search.gene.string.NumericStringGene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.impact.impactinfocollection.regex.*
import org.evomaster.core.search.impact.impactinfocollection.value.collection.SqlMultidimensionalArrayGeneImpact
import org.evomaster.core.search.service.mutator.MutatedGeneSpecification
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * created by manzh on 2019-09-09
 *
 * this utility can be used to e.g.,
 * 1) create new impact
 * 2) generate gene id for linking with its impact
 */
class ImpactUtils {

    companion object{

        private val log: Logger = LoggerFactory.getLogger(ImpactUtils::class.java)

        fun createGeneImpact(gene : Gene, id : String) : GeneImpact{
            return when(gene){
                is CustomMutationRateGene<*> -> DisruptiveGeneImpact(id, gene)
                is OptionalGene -> OptionalGeneImpact(id, gene)
                is BooleanGene -> BinaryGeneImpact(id)
                is EnumGene<*> -> EnumGeneImpact(id, gene)
                is IntegerGene -> IntegerGeneImpact(id)
                is LongGene -> LongGeneImpact(id)
                is DoubleGene -> DoubleGeneImpact(id)
                is FloatGene -> FloatGeneImpact(id)
                is StringGene -> StringGeneImpact(id, gene)
                //is Base64StringGene -> StringGeneImpact(id, gene.data)
                is ObjectGene -> ObjectGeneImpact(id, gene)
                is TupleGene -> TupleGeneImpact(id, gene)
                is MapGene<*, *> -> MapGeneImpact(id)
                is PairGene<*, *> -> throw IllegalStateException("do not count impacts for PairGene yet")
                is ArrayGene<*> -> ArrayGeneImpact(id)
                is DateGene -> DateGeneImpact(id, gene)
                is DateTimeGene -> DateTimeGeneImpact(id, gene)
                is TimeGene -> TimeGeneImpact(id, gene)
                is SeededGene<*> -> SeededGeneImpact(id, gene)
                // math
                is BigDecimalGene -> BigDecimalGeneImpact(id)
                is BigIntegerGene -> BigIntegerGeneImpact(id)
                is NumericStringGene -> NumericStringGeneImpact(id, gene)
                //sql
                is NullableGene -> NullableImpact(id, gene)
                is SqlJSONGene -> SqlJsonGeneImpact(id, gene)
                is SqlXMLGene -> SqlXmlGeneImpact(id, gene)
                is UUIDGene -> SqlUUIDGeneImpact(id, gene)
                is SqlPrimaryKeyGene -> SqlPrimaryKeyGeneImpact(id, gene)
                is SqlForeignKeyGene -> SqlForeignKeyGeneImpact(id)
                is SqlAutoIncrementGene -> GeneImpact(id)
                is SqlMultidimensionalArrayGene<*> -> SqlMultidimensionalArrayGeneImpact(id)
                // regex
                is RegexGene -> RegexGeneImpact(id, gene)
                is DisjunctionListRxGene -> DisjunctionListRxGeneImpact(id, gene)
                is DisjunctionRxGene -> DisjunctionRxGeneImpact(id, gene)
                is QuantifierRxGene -> QuantifierRxGeneImpact(id, gene)
                is RxAtom -> RxAtomImpact(id)
                is RxTerm -> RxTermImpact(id)
                // general for composite fixed gene
                is CompositeFixedGene -> CompositeFixedGeneImpact(id, gene)
                else ->{
                    LoggingUtil.uniqueWarn(log, "the impact of {} was collected in a general manner, i.e., GeneImpact", gene::class.java.simpleName)
                    GeneImpact(id)
                }
            }
        }

        private const val SEPARATOR_ACTION_TO_GENE = "::"
        private const val SEPARATOR_GENE = ";"
        private const val SEPARATOR_GENE_WITH_TYPE = ">"

        /**
         * TODO
         * Man: might employ local id of the action, check it later
         */
        fun generateGeneId(action: Action, gene : Gene) : String = "${action.getName()}$SEPARATOR_ACTION_TO_GENE${generateGeneId(gene)}$SEPARATOR_ACTION_TO_GENE${action.seeTopGenes().indexOf(gene)}"

        fun extractActionName(geneId : String) : String?{
            if (!geneId.contains(SEPARATOR_ACTION_TO_GENE)) return null
            return geneId.split(SEPARATOR_ACTION_TO_GENE).first()
        }

        fun <T : Individual> generateGeneId(individual: T, gene: Gene) : String{
            if (!individual.seeGenes().contains(gene)){
                log.warn("cannot find this gene ${gene.name} ($gene) in this individual")
                return generateGeneId(gene)
            }
            individual.seeInitializingActions().find { da->
                da.seeTopGenes().contains(gene)
            }?.let {
                return generateGeneId(it, gene)
            }
            individual.seeActions(ActionFilter.NO_INIT).find { a-> a.seeTopGenes().contains(gene) }?.let {
                return generateGeneId(action = it, gene = gene)
            }
            return generateGeneId(gene)
        }

        /**
         * extract info regarding a gene (on a action of an individual if it has) before mutated and the gene after mutated
         *
         * @param mutatedGenes genes were mutated
         * @param individual a mutated individual with [mutatedGenes]
         * @param previousIndividual mutating [previousIndividual] becomes [individual]
         */
        private fun extractMutatedGeneWithContext(
                mutatedGenes : MutableList<Gene>,
                individual: Individual,
                previousIndividual: Individual
        ) : Map<String, MutableList<MutatedGeneWithContext>>{
            val mutatedGenesWithContext = mutableMapOf<String, MutableList<MutatedGeneWithContext>>()

            if (individual.seeAllActions().isEmpty()){
                individual.seeGenes().filter { mutatedGenes.contains(it) }.forEach { g->
                    val id = generateGeneId(individual, g)
                    val contexts = mutatedGenesWithContext.getOrPut(id){ mutableListOf()}
                    val previous = findGeneById(previousIndividual, id)?: throw IllegalArgumentException("mismatched previous individual")
                    contexts.add(MutatedGeneWithContext(g, previous = previous, numOfMutatedGene = mutatedGenes.size, actionTypeClass = null))
                }
            }else{
                individual.seeAllActions().forEachIndexed { index, action ->
                    action.seeTopGenes().filter { mutatedGenes.contains(it) }.forEach { g->
                        val id = generateGeneId(action, g)
                        val contexts = mutatedGenesWithContext.getOrPut(id){ mutableListOf()}
                        val previous = findGeneById(previousIndividual, id, action.getName(), index, action.getLocalId(), action is ApiExternalServiceAction, false)?: throw IllegalArgumentException("mismatched previous individual")
                        contexts.add(MutatedGeneWithContext(
                            g,
                            action.getName(),
                            index,
                            action.getLocalId(),
                            action is ApiExternalServiceAction,
                            previous,
                            mutatedGenes.size,
                            actionTypeClass = action::class.java.name
                        ))
                    }
                }
            }


            Lazy.assert{
                mutatedGenesWithContext.values.sumOf { it.size } == mutatedGenes.size
            }
            return mutatedGenesWithContext
        }

        private fun extractMutatedGeneWithContext(mutatedGeneSpecification: MutatedGeneSpecification,
                                                  actions: List<Pair<Action, Int?>>,
                                                  previousIndividual: Individual,
                                                  isInit : Boolean, list: MutableList<MutatedGeneWithContext>, num : Int) {

            actions.forEach { indexedAction ->
                val a = indexedAction.first
                val index = indexedAction.second

                val manipulated = mutatedGeneSpecification.isActionMutated(index, a.getLocalId(), isInit)
                if (manipulated){
                    a.seeTopGenes().filter {
                        if (isInit)
                            mutatedGeneSpecification.mutatedInitGeneInfo().contains(it)
                        else
                            mutatedGeneSpecification.mutatedGeneInfo().contains(it) || mutatedGeneSpecification.mutatedDbGeneInfo().contains(it)
                    }.forEach { mutatedg->
                        val id = generateGeneId(a, mutatedg)

                        /*
                           index for db gene might be changed if new insertions are added.
                           then there is a need to update the index in previous based on the number of added
                         */
                        val indexInPrevious = if (index == null) null else index - (if (isInit && mutatedGeneSpecification.addedExistingDataInInitialization[a::class.java.name]?.contains(a) == false) mutatedGeneSpecification.addedExistingDataInInitialization[a::class.java.name]?.size?:0 else 0)
                        val previous = findGeneById(
                                individual=previousIndividual,
                                id = id,
                                actionName = a.getName(),
                                indexOfAction = indexInPrevious,
                                localId = a.getLocalId(),
                                isDynamic = a is ApiExternalServiceAction,
                                isDb = isInit
                        )
                        list.add(MutatedGeneWithContext(
                            current = mutatedg,
                            actionName = a.getName(),
                            position = index,
                            actionLocalId = a.getLocalId(),
                            isDynamicAction = index == null,
                            previous = previous,
                            numOfMutatedGene = num,
                            actionTypeClass = a::class.java.name
                        ))
                    }
                }
            }
        }

        fun extractMutatedGeneWithContext(mutatedGeneSpecification: MutatedGeneSpecification,
                                          individual: Individual,
                                          previousIndividual: Individual,
                                          isInit : Boolean) : MutableList<MutatedGeneWithContext>{
            val num = mutatedGeneSpecification.numOfMutatedGeneInfo()

            val list = mutableListOf<MutatedGeneWithContext>()

            if (individual.hasAnyAction()){
                if (isInit){
                    Lazy.assert { mutatedGeneSpecification.mutatedInitGenes.isEmpty() || individual.seeInitializingActions().isNotEmpty() }
                    extractMutatedGeneWithContext(mutatedGeneSpecification, individual.getRelativeIndexedInitActions(), previousIndividual, isInit, list, num)
                }else{
                    extractMutatedGeneWithContext(mutatedGeneSpecification, individual.getRelativeIndexedNonInitAction(), previousIndividual, isInit, list, num)
                }
            }else{
                Lazy.assert { !isInit }

                individual.seeGenes().filter { mutatedGeneSpecification.mutatedGeneInfo().contains(it) }.forEach { g->
                    val id = generateGeneId(individual, g)
                    val previous = findGeneById(previousIndividual, id)?: throw IllegalArgumentException("mismatched previous individual")
                    list.add(MutatedGeneWithContext(g, previous = previous, numOfMutatedGene = num, actionTypeClass = null))
                }
            }

            return list
        }


        private fun findGeneById(individual: Individual, id : String, actionName : String, indexOfAction : Int?, localId: String?, isDynamic : Boolean, isDb : Boolean):Gene?{
            if (!isDynamic && indexOfAction == null)
                throw IllegalArgumentException("indexOfAction must be specified if the action is not dynamic, ie, external service action")

            if (isDynamic && localId == null)
                throw IllegalArgumentException("localId must be specified if the action is dynamic, ie, external service action")

            val action = if (indexOfAction!=null && !isDynamic){
                if (indexOfAction >= (if (isDb) individual.seeInitializingActions() else individual.seeFixedMainActions()).size) return null
                if (isDb) individual.seeInitializingActions()[indexOfAction] else individual.seeFixedMainActions()[indexOfAction]
            }else {
                individual.findActionByLocalId(localId!!)?:return null
            }
            if (action.getName() != actionName)
                throw IllegalArgumentException("mismatched gene mutated info ${action.getName()} vs. $actionName")
            return action.seeTopGenes().find { generateGeneId(action, it) == id }

        }

        private fun findGeneById(individual: Individual, id : String):Gene?{
            return individual.seeGenes().find { generateGeneId(individual, it) == id }
        }

        fun extractGeneById(actions: List<Action>, id: String) : MutableList<Gene>{
            if (actions.isEmpty() || id.contains(SEPARATOR_ACTION_TO_GENE)) return mutableListOf()

            val names = id.split(SEPARATOR_ACTION_TO_GENE)

            Lazy.assert{names.size == 2}
            return actions.filter { it.getName() == names[0] }.flatMap { it.seeTopGenes() }.filter { it.name == names[1] }.toMutableList()
        }

        fun isAnyChange(geneA : Gene, geneB : Gene) : Boolean{
            Lazy.assert{geneA::class.java.simpleName == geneB::class.java.simpleName}
            return geneA.getValueAsRawString() == geneB.getValueAsRawString()
        }

        /**
         * TODO: should handle SQL related genes separately?
         */
        fun generateGeneId(gene: Gene):String{
            return when(gene){
                is CustomMutationRateGene<*> -> "DisruptiveGene$SEPARATOR_GENE_WITH_TYPE${generateGeneId(gene.gene)}"
                is OptionalGene -> "OptionalGene$SEPARATOR_GENE_WITH_TYPE${generateGeneId(gene.gene)}"
                is ObjectGene -> if (gene.refType.isNullOrBlank()) gene.name else "${gene.refType}$SEPARATOR_GENE_WITH_TYPE${gene.name}"
                else -> gene.name
            }
        }

        /**
         * find a gene that has the same with [gene], but different value
         * @param gene is one of root genes of [action]
         */
        fun findMutatedGene(action: Action, gene : Gene, includeSameValue : Boolean = false) : Gene?{
            return findMutatedGene(action.seeTopGenes(), gene, includeSameValue)
        }


        fun findMutatedGene(genes: List<Gene>, gene : Gene, includeSameValue : Boolean = false) : Gene?{
            val template = ParamUtil.getValueGene(gene)
            val found = genes.filter { it.isMutable() }.filter {o->
                val g = ParamUtil.getValueGene(o)
                g.name == template.name && g::class.java.simpleName == template::class.java.simpleName
                        && (includeSameValue
                            || !try { g.containsSameValueAs(template) }catch (e: Exception){
                                if (g !is ArrayGene<*> && g !is FixedMapGene<*,*>) throw e else false
                            }
                        )
            }
            if (found.size == 1) return found.first()
            return found.firstOrNull { it.getLocalId() == gene.getLocalId() }?: found.also {
                if (it.size > 1) log.warn("{} genes have been mutated with the name {} and localId {}",it.size, gene.name, gene.getLocalId())
            }.firstOrNull()
        }
    }
}