package org.evomaster.core.search.service.mutator

import com.google.inject.Inject
import org.evomaster.core.EMConfig
import org.evomaster.core.Lazy
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.problem.externalservice.httpws.service.HarvestActualHttpWsResponseHandler
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.service.*
import org.evomaster.core.search.service.mutator.genemutation.ArchiveGeneMutator
import org.evomaster.core.search.service.mutator.genemutation.ArchiveImpactSelector
import org.evomaster.core.search.tracer.ArchiveMutationTrackService
import org.evomaster.core.search.tracer.TraceableElementCopyFilter
import org.evomaster.core.search.tracer.TrackOperator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class Mutator<T> : TrackOperator where T : Individual {

    @Inject
    protected lateinit var randomness: Randomness

    @Inject
    protected lateinit var ff: FitnessFunction<T>

    @Inject
    protected lateinit var time: SearchTimeController

    @Inject
    protected lateinit var apc: AdaptiveParameterControl

    @Inject
    protected lateinit var structureMutator: StructureMutator

    @Inject
    protected lateinit var config: EMConfig

    @Inject
    private lateinit var tracker : ArchiveMutationTrackService

    @Inject
    protected lateinit var archiveGeneSelector : ArchiveImpactSelector

    @Inject
    protected lateinit var archiveGeneMutator : ArchiveGeneMutator

    @Inject
    protected lateinit var mwc : MutationWeightControl

    @Inject
    protected lateinit var harvestResponseHandler: HarvestActualHttpWsResponseHandler

    /**
     * @param mutatedGenes is used to record what genes are mutated within [mutate], which can be further used to analyze impacts of genes.
     * @return a mutated copy
     */
    abstract fun mutate(individual: EvaluatedIndividual<T>, targets: Set<Int> = setOf(), mutatedGenes: MutatedGeneSpecification? = null): T

    /**
     * @param individual an individual to mutate
     * @param evi a reference of the individual to mutate
     * @return a list of genes that are allowed to mutate
     */
    abstract fun genesToMutation(individual: T, evi: EvaluatedIndividual<T>, targets: Set<Int>) : List<Gene>

    /**
     * @param individual an individual to mutate
     * @param evi a reference of the individual to mutate
     * @param targets to cover with this mutation
     * @return a list of genes that are selected to mutate
     */
    abstract fun selectGenesToMutate(individual: T, evi: EvaluatedIndividual<T>, targets: Set<Int> = setOf(), mutatedGenes: MutatedGeneSpecification?) : List<Gene>

    /**
     * @return whether you do a structure mutation
     */
    abstract fun doesStructureMutation(evaluatedIndividual: EvaluatedIndividual<T>) : Boolean

    /**
     * @return whether you do a structure mutation on initialization if it exists
     */
    open fun doesInitStructureMutation(evaluatedIndividual: EvaluatedIndividual<T>): Boolean {
        return config.initStructureMutationProbability > 0 && ((!structureMutator.canApplyActionStructureMutator(evaluatedIndividual.individual))
                || (structureMutator.canApplyInitStructureMutator() && randomness.nextBoolean(config.initStructureMutationProbability)))
    }

    open fun postActionAfterMutation(individual: T, mutated: MutatedGeneSpecification?){}

    open fun update(previous: EvaluatedIndividual<T>, mutated: EvaluatedIndividual<T>, mutatedGenes: MutatedGeneSpecification?, mutationEvaluated: EvaluatedMutation){}

    companion object{
        private val log: Logger = LoggerFactory.getLogger(Mutator::class.java)
    }

    /**
     * @param upToNTimes how many mutations will be applied. can be less if running out of time
     * @param individual which will be mutated
     * @param archive where to save newly mutated individuals (if needed, eg covering new targets)
     */
    fun mutateAndSave(upToNTimes: Int, individual: EvaluatedIndividual<T>, archive: Archive<T>)
            : EvaluatedIndividual<T> {

        if (log.isTraceEnabled){
            log.trace("mutator will be applied, and the individual contains {} dbactions which are",
                individual.individual.seeInitializingActions().size,
                individual.individual.seeInitializingActions().joinToString(","){
                    if (it is SqlAction) it.getResolvedName() else it.getName()
                } )
        }

        var current = individual

        // tracking might be null if the current is never mutated
        preHandlingTrackedIndividual(current)

        val targets = archive.notCoveredTargets().toMutableSet()

        for (i in 0 until upToNTimes) {

            if (log.isTraceEnabled){
                log.trace("the individual will be mutated {} times, now it is {}th", upToNTimes, i)
            }

            //save ei (i.e., impact and traces) before its individual is mutated, because the impact info might be updated during mutation
            val currentWithTraces = current.copy(tracker.getCopyFilterForEvalInd(current))

            if (!time.shouldContinueSearch()) {
                break
            }

            val mutatedGenes = MutatedGeneSpecification()

            if (log.isTraceEnabled){
                log.trace("now it is {}th, do addInitializingActions starts", i)
            }

            // impact info is updated due to newly added initialization actions
            structureMutator.addInitializingActions(current, mutatedGenes)

            val anyHarvestedExternalServiceActions = structureMutator.addAndHarvestExternalServiceActions(current, mutatedGenes)

            if (log.isTraceEnabled){
                log.trace("now it is {}th, do addInitializingActions ends", i)
            }

            Lazy.assert{current.individual.verifyValidity(); true}

            // skip to mutate the individual if any new harvested external actions are added
            val mutatedInd = if (!anyHarvestedExternalServiceActions)
                mutate(current, targets, mutatedGenes)
            else
                current.individual.copy() as T

            mutatedGenes.setMutatedIndividual(mutatedInd)

            Lazy.assert{mutatedInd.verifyValidity(); true}

            //FIXME: why setOf()??? are we skipping coverage collection here???
            // or always added non-covered from archive? if so, name "targets" is confusing
            //Shall we prioritize the targets based on mutation sampling strategy eg, feedbackDirectedSampling?
            val mutated = ff.calculateCoverage(mutatedInd, setOf(), mutatedGenes)
                    ?: continue

            //evaluated mutated by comparing with current using employed targets
            val result = evaluateMutation(mutated, current, targets, archive)

            if (log.isTraceEnabled){
                log.trace("results of evaluateMutation {}", result.value)
            }

            //enable further actions for extracting
            update(currentWithTraces, mutated, mutatedGenes, result)

            //save mutationInfo which is only used for debugging
            archiveGeneMutator.saveMutatedGene(mutatedGenes, index = time.evaluatedIndividuals, individual = mutatedInd, evaluatedMutation = result, targets = targets)
            archive.saveSnapshot()

            val mutatedWithTraces = when{
                config.enableTrackEvaluatedIndividual-> current.next(
                        next = mutated, copyFilter = TraceableElementCopyFilter.WITH_ONLY_EVALUATED_RESULT, evaluatedResult = result)!!
                config.enableTrackIndividual -> {
                    current.nextForIndividual(next = mutated,  evaluatedResult = result)!!
                }
                else -> mutated
            }

            val targetsInfo =
                evaluateMutationInDetails(mutated = mutated, current = current, targets = targets, archive = archive)

            if (config.isEnabledImpactCollection() ){

                /*
                    update impact info regarding targets.
                    To avoid side-effect to impactful gene, remove covered targets
                 */
                mutatedWithTraces.updateImpactOfGenes(previous = currentWithTraces,
                        mutated = mutatedWithTraces, mutatedGenes = mutatedGenes,
                        targetsInfo = targetsInfo.filter { !archive.isCovered(it.key) && !archive.skipTargetForImpactCollection(it.key)}, config)
            }
            /*
                update archive based on mutated individual
                for next, we use [current] that contains latest updated initialization instead of [currentWithTraces]
             */
            current = saveMutation(result, archive, currentWithTraces, mutatedWithTraces)

            //save impact info which is only used for debugging
            archiveGeneSelector.saveImpactSnapshot(time.evaluatedIndividuals, checkedTargets = targets,targetsInfo = targetsInfo, result = result, evaluatedIndividual = current)

            when(config.mutationTargetsSelectionStrategy){
                EMConfig.MutationTargetsSelectionStrategy.FIRST_NOT_COVERED_TARGET ->{}
                EMConfig.MutationTargetsSelectionStrategy.EXPANDED_UPDATED_NOT_COVERED_TARGET ->{
                    targets.addAll(archive.notCoveredTargets())
                }
                EMConfig.MutationTargetsSelectionStrategy.UPDATED_NOT_COVERED_TARGET ->{
                    targets.clear()
                    targets.addAll(archive.notCoveredTargets())
                }
            }

            if (log.isTraceEnabled){
                log.trace("{}th mutation ends", i)
            }
        }
        return current
    }

    fun mutateAndSave(individual: EvaluatedIndividual<T>, archive: Archive<T>)
            : EvaluatedIndividual<T>? {

        structureMutator.addInitializingActions(individual,null)

        return ff.calculateCoverage(mutate(individual), setOf(), null)
                ?.also { archive.addIfNeeded(it) }
    }

    /**
     * @return a result by comparing mutated individual [mutated] with before [current] regarding [targets].
     */
    fun evaluateMutation(mutated: EvaluatedIndividual<T>, current: EvaluatedIndividual<T>, targets: Set<Int>, archive: Archive<T>): EvaluatedMutation {
        if (log.isTraceEnabled){
            log.trace("evaluateMutation with the targets {}", targets.joinToString(","){it.toString() })
        }

        // global check
        if (archive.wouldReachNewTarget(mutated)){

            if (log.isTraceEnabled){
                log.trace("archive reach new targets")
            }
            return EvaluatedMutation.BETTER_THAN
        }

        /*
            to compare mutated with current,
            targets for this comparision, employ targets to evaluate individual (i.e., targets in their fitness) can lead to different results.

            e.g., A1 is mutated to A2 by manipulating gene [a], and gene [a] affects target Ta
            1) fitness of A1 includes heuristic for Tb, Tc, fitness of A2 includes heuristic for Ta
         */
        return compare(mutated, current, targets)
    }

    /**
     * @return a result by comparing mutated individual [mutated] with before [current] regarding [targets].
     */
    private fun evaluateMutationInDetails(mutated: EvaluatedIndividual<T>, current: EvaluatedIndividual<T>, targets: Set<Int>, archive: Archive<T>): Map<Int, EvaluatedMutation> {

        if (!config.isEnabledImpactCollection() && !config.isEnabledArchiveSolution()) return emptyMap()

        val evaluatedTargets = targets.map { it to EvaluatedMutation.UNSURE }.toMap().toMutableMap()

        // in terms of Archive
        archive.identifyNewTargets(mutated, evaluatedTargets)

        // compare with current
        current.fitness.computeDifference(mutated.fitness, targetSubset = targets, targetInfo = evaluatedTargets, config = config)

        return evaluatedTargets
    }

    private fun compare(mutated: EvaluatedIndividual<T>, current: EvaluatedIndividual<T>, targets: Set<Int>): EvaluatedMutation {

        // current is better than mutated
        val beforeBetter = current.fitness.subsumes(other = mutated.fitness, targetSubset = targets, config = config)
        if (beforeBetter) return EvaluatedMutation.WORSE_THAN
        if (mutated.fitness.subsumes(current.fitness, targets, config)) return EvaluatedMutation.BETTER_THAN
        return EvaluatedMutation.EQUAL_WITH
    }

    private fun preHandlingTrackedIndividual(current: EvaluatedIndividual<T>){
        if (config.trackingEnabled()){
            if (config.enableTrackEvaluatedIndividual && current.tracking == null){
                current.wrapWithTracking(null, config.maxLengthOfTraces, mutableListOf())
                current.pushLatest(current.copy(TraceableElementCopyFilter.WITH_ONLY_EVALUATED_RESULT))
            }
            if (config.enableTrackIndividual && current.individual.tracking == null){
                current.individual.wrapWithTracking(null, config.maxLengthOfTraces, mutableListOf())
                current.individual.pushLatest(current.copy(TraceableElementCopyFilter.WITH_ONLY_EVALUATED_RESULT))
            }
        }
    }


    fun saveMutation(evaluatedMutation: EvaluatedMutation, archive: Archive<T>, current: EvaluatedIndividual<T>, mutated: EvaluatedIndividual<T>) : EvaluatedIndividual<T>{
        // if mutated is not worse than current, we employ the mutated for next mutation
        if (log.isTraceEnabled){
            log.trace("mutation is effective? {}", evaluatedMutation.isEffective())
        }
        if (evaluatedMutation.isEffective()){
            /*
                we only attempt to add individual into archive when it is not worse than current
             */
            archive.addIfNeeded(mutated)
            return mutated
        }
        return current
    }


}