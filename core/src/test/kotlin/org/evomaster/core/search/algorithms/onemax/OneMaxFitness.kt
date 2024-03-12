package org.evomaster.core.search.algorithms.onemax

import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.service.FitnessFunction
import org.evomaster.core.search.tracer.Traceable


class OneMaxFitness : FitnessFunction<OneMaxIndividual>() {


    override fun doCalculateCoverage(
        individual: OneMaxIndividual,
        targets: Set<Int>,
        allCovered: Boolean
    )
            : EvaluatedIndividual<OneMaxIndividual>? {

        val fv = FitnessValue(individual.size().toDouble())

        (if(allCovered) (0 until individual.n).toSet() else targetsToEvaluate(targets, individual))
                .forEach { fv.updateTarget(it, individual.getValue(it)) }

        return EvaluatedIndividual(
                fv, individual.copy() as OneMaxIndividual,
                listOf(), config = config, trackOperator = individual.trackOperator, index = if (config.trackingEnabled()) time.evaluatedIndividuals else Traceable.DEFAULT_INDEX)
    }


    // max 100 targets to evaluate
    override fun targetsToEvaluate(targets: Set<Int>, individual: OneMaxIndividual): Set<Int> {
        val sets = (0 until individual.n).filter { !targets.contains(it) }.toSet()
        return when {
            targets.size > 100 -> randomness.choose(targets, 100)
            sets.isEmpty() -> targets
            else -> targets.plus(randomness.choose(sets, 100 - targets.size))
        }
    }
}