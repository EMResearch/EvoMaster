package org.evomaster.core.search.matchproblem

import org.evomaster.client.java.distance.heuristics.DistanceHelper
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.gene.numeric.DoubleGene
import org.evomaster.core.search.gene.numeric.FloatGene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.numeric.LongGene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.service.FitnessFunction

/**
 * created by manzh on 2020-06-16
 */
class PrimitiveTypeMatchFitness : FitnessFunction<PrimitiveTypeMatchIndividual>() {

    var type : ONE2M = ONE2M.ONE_EQUAL_WITH_ONE

    override fun doCalculateCoverage(
        individual: PrimitiveTypeMatchIndividual,
        targets: Set<Int>,
        allCovered: Boolean
    ): EvaluatedIndividual<PrimitiveTypeMatchIndividual>? {

        val fv = FitnessValue(individual.size().toDouble())

        calAndUpdate(fv, individual)

        return EvaluatedIndividual(fv, individual.copy() as PrimitiveTypeMatchIndividual, listOf(), trackOperator = individual.trackOperator, index = time.evaluatedIndividuals, config = config)
    }

    private fun calculateHeuristic(target: Any, value : Any) : Double{
        val distance = DistanceHelper.getDistance(target, value)
        return  (1.0 / (1.0 + distance))
    }

    private fun getTargets(individual: PrimitiveTypeMatchIndividual) : List<Any>{
        return when{
            individual.gene is StringGene -> listOf("bar", "", "Y8l4x0WC9qtH5mTN")
            individual.gene is IntegerGene -> listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)
            individual.gene is DoubleGene -> listOf(Double.MIN_VALUE, -1.0, 0.0, 1.0, Double.MAX_VALUE)
            individual.gene is FloatGene -> listOf(Float.MIN_VALUE, -1.0f, 0.0f, 1.0f, Float.MAX_VALUE)
            individual.gene is LongGene -> listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)

            else -> throw IllegalStateException("NOT SUPPORT")
        }
    }

    private fun getValue(individual: PrimitiveTypeMatchIndividual) : Any{
        return when{
            individual.gene is StringGene -> individual.gene.value
            individual.gene is IntegerGene -> individual.gene.value
            individual.gene is DoubleGene -> individual.gene.value
            individual.gene is FloatGene -> individual.gene.value
            individual.gene is LongGene -> individual.gene.value

            else -> throw IllegalStateException("NOT SUPPORT")
        }
    }


    /**
     * one equals with one
     */
    private fun calAndUpdate(fv : FitnessValue, individual: PrimitiveTypeMatchIndividual){
        val all = getTargets(individual)

        val currents = if (archive.isEmpty()) initialTargets() else if (archive.notCoveredTargets().isNotEmpty()) archive.notCoveredTargets()  else return

        val max = currents.maxOrNull()

        currents.forEach { c->
            val value = getValue(individual)

            // simulate target.equals(value)
            val h = calculateHeuristic(all[c], value)

            fv.updateTarget(c, h)

            // reach next target until current is covered
            if (h == 1.0 && max != null && (max + 1) < all.size){
                fv.updateTarget(max+1, calculateHeuristic(all[max+1], value))
            }
        }

    }

    private fun initialTargets() : Set<Int>{
        return when(type){
            ONE2M.ONE_EQUAL_WITH_ONE -> setOf(0)
            ONE2M.ONE_EQUAL_WITH_MANY -> setOf(0,1)
        }
    }
}

enum class ONE2M{
    ONE_EQUAL_WITH_ONE,
    ONE_EQUAL_WITH_MANY
}


