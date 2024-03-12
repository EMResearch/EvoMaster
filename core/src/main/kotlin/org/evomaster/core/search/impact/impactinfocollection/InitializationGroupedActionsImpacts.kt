package org.evomaster.core.search.impact.impactinfocollection

import org.evomaster.core.Lazy
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.mongo.MongoDbAction
import org.evomaster.core.search.action.Action
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * impacts of actions for initialization of a test.
 * Currently, the initialization is composed of a sequence of SQL actions, and
 * there exist some duplicated sub-sequence.
 * @property abstract indicates whether extract the actions in order to identify unique sub-sequence.
 * @property enableImpactOnDuplicatedTimes indicates whether collect impacts on duplicated times.
 */
class InitializationGroupedActionsImpacts(val abstract: Boolean, val enableImpactOnDuplicatedTimes: Boolean = false) : ImpactOfDuplicatedArtifact<ImpactsOfAction>() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InitializationGroupedActionsImpacts::class.java)
    }

    /**
     * index conforms with completeSequence
     * first of pair is name of template
     * second of pair is index of actions in this template
     */
    val indexMap = mutableListOf<Pair<String, Int>>()

    private var existingSQLData = 0

    private fun getGroupedSequence() : List<List<ImpactsOfAction>>{
        if (indexMap.size != completeSequence.size)
            throw IllegalStateException("indexMap is out of sync ${indexMap.size} vs. ${completeSequence.size}")

        return getGroupedSequenceWithIndexMap(completeSequence)
    }

    private fun getGroupedSequenceWithIndexMap(list : List<ImpactsOfAction>) : List<List<ImpactsOfAction>>{
        if (indexMap.size < list.size)
            throw IllegalStateException("list contains more elements than IndexMap")

        val group = list.mapIndexed { index, impactsOfAction ->
            impactsOfAction to indexMap[index].second
        }.groupBy {
            it.second
        }
        val result = mutableListOf<List<ImpactsOfAction>>()

        group.forEach { (t, u) ->
            result.add(t, u.map { it.first })
        }
        return  result
    }


    private fun initPreCheck() {
        if (completeSequence.isNotEmpty() || template.isNotEmpty() || indexMap.isNotEmpty())
            throw IllegalStateException("duplicated initialization")
    }

    fun updateSizeOfExistingData(size : Int){
        existingSQLData = size
    }

    fun getExistingData() = existingSQLData

    /**
     * @param groupedActions should be insertions
     */
    fun initInitializationActions(groupedActions: List<List<Action>>, existingDataSize : Int) {
        initPreCheck()

        updateSizeOfExistingData(existingDataSize)

        /*
        there might exist duplicated db actions with EvoMaster that
            1) ensures that required resources are created; 2) support collection request, e.g., GET Collection
        However in a view of mutation, we only concern unique ones
        therefore we abstract dbactions in Initialization in order to identify unique dbactions,
        then further mutate values with abstracted ones, e.g.,
            a sequence of dbaction is abababc, then its abstraction is ab-c
        */
        groupedActions.forEach { t->
            addedInitialization(t, completeSequence, indexMap)
        }
    }

    /**
     * append impacts of newly added insertions
     */
    fun appendInitialization(addedInsertions: List<List<Action>>){
        addedInsertions.forEach { t->
            addedInitialization(t, completeSequence, indexMap)
        }
    }

    /**
     * remove impacts of actions in initialization of the individual
     * @param removed to be removed, a list of Dbaction to its index
     */
    fun removeInitialization(removed: List<Pair<SqlAction, Int>>){
        val removedIndex = removed.map { it.second - existingSQLData}.sorted()
        val removedImpacts = removedIndex.map { completeSequence[it] }

        val keep = mutableListOf<Int>()
        var anyRemove = false
        (removedIndex.first() until indexMap.size).forEach { i->
            val last = (i == indexMap.size -1 ) || indexMap[i+1].second == 0
            if (removedIndex.contains(i)){
                anyRemove = true
            }else{
                keep.add(i)
            }
            if (last){
                if (anyRemove && keep.isNotEmpty()){
                    val newTemplate = generateTemplateKey(keep.map { completeSequence[it].actionName?:""})
                    keep.forEachIndexed { index, j ->
                        indexMap[j] = newTemplate to index
                    }
                    template.putIfAbsent(newTemplate, keep.map { k->
//                        ImpactsOfAction(removed.find { it.second == i }?.first
//                                ?: throw IllegalStateException("cannot find removed dbactions at $i"))
                        completeSequence[k]
                    })
                }
                anyRemove = false
                keep.clear()
            }
        }

        completeSequence.removeAll(removedImpacts)
        removedIndex.reversed().forEach {
            indexMap.removeAt(it)
        }

        //handle template
        val removeTemplates = template.filter {
            indexMap.none { i-> i.first == it.key }
        }.keys
        removeTemplates.forEach {
            template.remove(it)
            if(enableImpactOnDuplicatedTimes){
                templateDuplicateTimes.remove(it)
            }
        }
    }

    private fun addedInitialization(
        insertions: List<Action>,
        completeSequence : MutableList<ImpactsOfAction>,
        indexMap: MutableList<Pair<String, Int>>
    ){
        val group = insertions.map { a-> ImpactsOfAction(a) }
        val key = generateTemplateKey(group.map { i-> i.actionName!! })
        completeSequence.addAll(group)
        insertions.forEachIndexed { i, _ ->
            indexMap.add(Pair(key, i))
        }
        template.putIfAbsent(key, insertions.map { a-> ImpactsOfAction(a) })
        if (enableImpactOnDuplicatedTimes)
            templateDuplicateTimes.putIfAbsent(key, Impact(id = key))
    }

    /**
     * add impacts of newly added insertions at the beginning of the initalization (ie index is 0)
     */
    fun updateInitializationImpactsAtEnd(addedInsertions: List<List<Action>>, existingDataSize : Int){
        updateSizeOfExistingData(existingDataSize)

        val newCompleteSequence =  mutableListOf<ImpactsOfAction>()
        val newIndex = mutableListOf<Pair<String, Int>>()
        addedInsertions.forEach { t->
            addedInitialization(t, newCompleteSequence, newIndex)
        }
        indexMap.addAll(newIndex)
        completeSequence.addAll(newCompleteSequence)
    }

    /**
     * init current initializationImpact based on the specified [impact]
     */
    fun initInitializationActions(impact: InitializationGroupedActionsImpacts) {
        //initPreCheck()
        clone(impact)
    }

    /**
     * @param list actions after truncation
     */
    fun truncation(list: List<Action>) {
        val ignoreExisting = list.filterIsInstance<SqlAction>().count{it.representExistingData }
        if (ignoreExisting != getExistingData()){
            log.warn("mismatched existing data")
        }

        val original = completeSequence.size
        val seq = list.filter{(it is SqlAction && !it.representExistingData) || it is MongoDbAction }
        if (seq.size > original) {
            log.warn("there are more db actions after the truncation")
            return
        }
        if (seq.size == original) return

        val newCompleteSequence = seq.mapIndexed { index, db ->
            val name = db.getName()
            //FIXME Man: further check null case
            getImpactOfAction(name, index + ignoreExisting) ?: ImpactsOfAction(db)
        }

        completeSequence.clear()
        completeSequence.addAll(newCompleteSequence)

        if (!abstract) return


        val middle = indexMap[seq.size - 1].second == indexMap[seq.size].second
        if (middle){
            val starting = indexMap.indexOfFirst {
                it.second == indexMap[seq.size].second
            }
            val newkey = generateTemplateKey(seq.subList(starting, seq.size).map { it.getName() })
            indexMap.removeAll(indexMap.subList(starting, original))
            (starting until seq.size).forEach {
                indexMap.add(
                        newkey to it
                )
            }
            template.putIfAbsent(newkey, newCompleteSequence.subList(starting, original))
            if (enableImpactOnDuplicatedTimes)
                templateDuplicateTimes.putIfAbsent(newkey, Impact(id = newkey))
        }else{
            while(indexMap.size > seq.size){
                indexMap.removeAt(indexMap.size - 1)
            }
        }

        Lazy.assert{
            ignoreExisting == indexMap.size
        }
        val extracted = indexMap.map { it.first }.toSet()
        template.filterKeys { !extracted.contains(it) }.keys.forEach {
            template.remove(it)
            templateDuplicateTimes.remove(it)
        }
    }

    private fun generateTemplateKey(actionNames: List<String>) = actionNames.joinToString("$$")

    fun getTemplateValue(group: List<Action>): List<ImpactsOfAction>? {
        return template[generateTemplateKey(group.map { it.getName() })]
    }

    fun copy(): InitializationGroupedActionsImpacts {
        val new = InitializationGroupedActionsImpacts(abstract, enableImpactOnDuplicatedTimes)
        new.completeSequence.addAll(completeSequence.map { it.copy() })
        new.template.putAll(template.mapValues { it.value.map { v -> v.copy() } })
        new.indexMap.addAll(indexMap.map { Pair(it.first, it.second) })
        if (enableImpactOnDuplicatedTimes)
            new.templateDuplicateTimes.putAll(templateDuplicateTimes.mapValues { it.value.copy() })

        new.existingSQLData = existingSQLData
        return new
    }


    fun clone(): InitializationGroupedActionsImpacts {
        val new = InitializationGroupedActionsImpacts(abstract, enableImpactOnDuplicatedTimes)
        new.completeSequence.addAll(completeSequence.map { it.clone() })
        new.template.putAll(template.mapValues { it.value.map { v -> v.clone() } })
        new.indexMap.addAll(indexMap.map { Pair(it.first, it.second) })

        if (enableImpactOnDuplicatedTimes)
            new.templateDuplicateTimes.putAll(templateDuplicateTimes.mapValues { it.value.clone() })

        new.existingSQLData = existingSQLData
        return new
    }

    /**
     * clone this based on [other]
     */
    fun clone(other: InitializationGroupedActionsImpacts) {
        reset()
        existingSQLData = other.getExistingData()
        completeSequence.addAll(other.completeSequence.map { it.clone() })
        template.putAll(other.template.mapValues { it.value.map { v -> v.clone() } })
        indexMap.addAll(other.indexMap.map { Pair(it.first, it.second) })
        if (enableImpactOnDuplicatedTimes != other.enableImpactOnDuplicatedTimes)
            throw IllegalStateException("different setting on enableImpactOnDuplicatedTimes")
        if (enableImpactOnDuplicatedTimes)
            templateDuplicateTimes.putAll(other.templateDuplicateTimes.mapValues { it.value.clone() })
    }

    fun reset() {
        completeSequence.clear()
        template.clear()
        indexMap.clear()
        templateDuplicateTimes.clear()
        existingSQLData = 0
    }

    /**
     * @return impact of action by [actionName] or [actionIndex]
     * @param actionName is a name of action
     * @param actionIndex index of action in a test
     */
    fun getImpactOfAction(actionName: String?, actionIndex: Int): ImpactsOfAction? {
        val mIndex = actionIndex - existingSQLData
        if (mIndex >= completeSequence.size)
            throw IllegalArgumentException("exceed the boundary of impacts regarding actions, i.e., size of actions is ${completeSequence.size}, but asking index is $actionIndex")
        val name = completeSequence[mIndex].actionName
        if (actionName != null && name != actionName)
            throw IllegalArgumentException("mismatched action name, i.e., current is $name, but $actionName")
        if (!abstract) {
            return completeSequence[mIndex]
        }
        val templateInfo = if(indexMap.size > mIndex)
            indexMap[mIndex]
        else
            throw IllegalArgumentException()
        return template[templateInfo.first]?.get(templateInfo.second)
    }

    /**
     * @return the original size of the impacts
     */
    fun getOriginalSize(includeExistingSQLData : Boolean = true) = completeSequence.size + if (includeExistingSQLData) existingSQLData else 0

    /**
     * @return the size of the impacts
     * Note that if the init action are abstracted one, it could be less than the value obtained by [getOriginalSize]
     */
    fun getSize(): Int {
        if (abstract) return template.size
        return completeSequence.size
    }

    /**
     * @return all impacts of actions
     */
    fun getAll(): List<ImpactsOfAction> {
        if (abstract) return template.values.flatten()
        return completeSequence
    }
}
