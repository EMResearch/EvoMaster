package org.evomaster.core.search.algorithms

import org.evomaster.core.EMConfig
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual
import org.evomaster.core.search.service.SearchAlgorithm
import org.evomaster.core.logging.LoggingUtil
import java.util.ArrayList



/**
 * Implementation of MOSA from
 * "Automated Test Case Generation as a Many-Objective Optimisation Problem with Dynamic
 *  Selection of the Targets"
 */
class MosaAlgorithm<T> : SearchAlgorithm<T>() where T : Individual {

    private class Data(val ind: EvaluatedIndividual<*>) {

        var rank = -1
        var crowdingDistance = -1
    }

    private var population: MutableList<Data> = mutableListOf()

    override fun getType(): EMConfig.Algorithm {
        return EMConfig.Algorithm.MOSA
    }

    override fun setupBeforeSearch() {

        population.clear()

        initPopulation()
        sortPopulation()

    }

    override fun searchOnce() {


        val n = config.populationSize


            //new generation

            val nextPop: MutableList<Data> = mutableListOf()

            while (nextPop.size < n-1) {

                var ind = selection()

                getMutatator().mutateAndSave(ind, archive)
                        ?.let{nextPop.add(Data(it))}

                if (!time.shouldContinueSearch()) {
                    break
                }
            }
            // generate one random solution
            var ie = sampleIndividual()
            nextPop.add(Data(ie as EvaluatedIndividual))

            population.addAll(nextPop)
            sortPopulation()
    }


    private fun sortPopulation() {

        val notCovered = archive.notCoveredTargets()

        if(notCovered.isEmpty()){
            //Trivial problem: everything covered in first population
            return
        }

        val fronts = preferenceSorting(notCovered, population)

        var remain: Int = config.populationSize
        var index = 0
        population.clear()

        // Obtain the next front
        var front = fronts[index]

        while (front!=null && remain > 0 && remain >= front.size && front.isNotEmpty()) {
            // Assign crowding distance to individuals
            subvectorDominance(notCovered, front)
            // Add the individuals of this front
            for (d in front) {
                population.add(d)
            }

            // Decrement remain
            remain = remain - front.size

            // Obtain the next front
            index += 1
            if (remain > 0) {
                front = fronts[index]
            } // if
        } // while

        // Remain is less than front(index).size, insert only the best one
        if (remain > 0 && front!=null && front.isNotEmpty()) {
            subvectorDominance(notCovered, front)
            var front2 = front.sortedWith(compareBy<Data> { - it.crowdingDistance })
                     .toMutableList()
           for (k in 0..remain - 1) {
                population.add(front2[k])
            } // for

        } // if

    }

    private fun subvectorDominance(notCovered: Set<Int>, list: List<Data>){
        /*
            see:
            Substitute Distance Assignments in NSGA-II for
            Handling Many-Objective Optimization Problems
         */

        list.forEach { i ->
            i.crowdingDistance = 0
            list.filter { j -> j!=i }.forEach { j ->
                val v = svd(notCovered, i, j)
                if(v > i.crowdingDistance){
                    i.crowdingDistance = v
                }
            }
        }
    }


    private fun svd(notCovered: Set<Int>, i: Data, j: Data) : Int{
        var cnt = 0
        for(t in notCovered){
            if(i.ind.fitness.getHeuristic(t) > j.ind.fitness.getHeuristic(t)){
                cnt++
            }
        }
        return cnt
    }


    /*
      See: Preference sorting as discussed in the TSE paper for DynaMOSA
    */
    private fun preferenceSorting(notCovered: Set<Int>, list: List<Data>): HashMap<Int, List<Data>> {

        val fronts = HashMap<Int, List<Data>>()

        // compute the first front using the Preference Criteria
        val frontZero = mosaPreferenceCriterion(notCovered, list)
        fronts.put(0, ArrayList(frontZero))
        LoggingUtil.getInfoLogger().apply {
            debug("First front size : ${frontZero.size}")
        }

        // compute the remaining non-dominated Fronts
        val remaining_solutions: MutableList<Data> = mutableListOf()
        remaining_solutions.addAll(list)
        remaining_solutions.removeAll(frontZero)

        var selected_solutions = frontZero.size
        var front_index = 1

        while (selected_solutions < config.populationSize && remaining_solutions.isNotEmpty()){
            var front: MutableList<Data> = getNonDominatedFront(notCovered, remaining_solutions)
            fronts.put(front_index, front)
            for (sol in front){
                sol.rank = front_index
            }
            remaining_solutions.removeAll(front)

            selected_solutions += front.size

            front_index += 1

            LoggingUtil.getInfoLogger().apply {
                debug("Selected Solutions : ${selected_solutions}")
            }
        }
        return fronts
    }

    /**
     * It retrieves the front of non-dominated solutions from a list
     */
    private fun getNonDominatedFront(notCovered: Set<Int>, remaining_sols: List<Data>): MutableList<Data>{
        var front: MutableList<Data> = mutableListOf()
        var isDominated: Boolean

        for (p in remaining_sols) {
            isDominated = false
            val dominatedSolutions = ArrayList<Data>(remaining_sols.size)
            for (best in front) {
                val flag = compare(p, best, notCovered)
                if (flag == -1) {
                    dominatedSolutions.add(best)
                }
                if (flag == +1) {
                    isDominated = true
                }
            }

            if (isDominated)
                continue

            front.removeAll(dominatedSolutions)
            front.add(p)

        }
        return front
    }

    /**
     * Fast routine based on the Dominance Comparator discussed in
     * "Automated Test Case Generation as a Many-Objective Optimisation Problem with Dynamic
     *  Selection of the Targets"
     */
    private fun compare(x: Data, y: Data, notCovered: Set<Int>): Int {
        var dominatesX = false
        var dominatesY = false

        for (index in 1..notCovered.size) {
            if (x.ind.fitness.getHeuristic(index) > y.ind.fitness.getHeuristic(index))
                dominatesX = true
            if (y.ind.fitness.getHeuristic(index) > x.ind.fitness.getHeuristic(index))
                dominatesY = true

            // if the both do not dominates each other, we don't
            // need to iterate over all the other targets
            if (dominatesX && dominatesY)
                return 0
        }

        if (dominatesX == dominatesY)
            return 0

        else if (dominatesX)
            return -1

        else (dominatesY)
            return +1
    }

    private fun mosaPreferenceCriterion(notCovered: Set<Int>, list: List<Data>): HashSet<Data> {
        var frontZero: HashSet<Data> = HashSet<Data>()

        notCovered.forEach { t ->
            var chosen = list[0]
            list.forEach { data ->
                if (data.ind.fitness.getHeuristic(t) > chosen.ind.fitness.getHeuristic(t)) {
                    // recall: maximization problem
                    chosen = data
                } else if (data.ind.fitness.getHeuristic(t) == chosen.ind.fitness.getHeuristic(t)
                            && data.ind.individual.size() < chosen.ind.individual.size()){
                    // Secondary criterion based on tests lengths
                    chosen = data
                }
            }
            // MOSA preference criterion: the best for a target gets Rank 0
            chosen.rank = 0
            frontZero.add(chosen)
        }
        return frontZero
    }

    private fun selection(): EvaluatedIndividual<T> {

        // the population is not fully sorted
        var min = randomness.nextInt(population.size)

        (0 until config.tournamentSize-1).forEach {
            val sel = randomness.nextInt(population.size)
            if (population[sel].rank < population[min].rank) {
                min = sel
            } else if (population[sel].rank == population[min].rank){
                if (population[sel].crowdingDistance < population[min].crowdingDistance)
                    min = sel
            }
        }

        return (population[min].ind as EvaluatedIndividual<T>).copy()
    }


    private fun initPopulation() {

        val n = config.populationSize

        for (i in 1..n) {
            sampleIndividual()?.run { population.add(Data(this)) }

            if (!time.shouldContinueSearch()) {
                break
            }
        }
    }

    private fun sampleIndividual(): EvaluatedIndividual<T>? {

        return ff.calculateCoverage(sampler.sample(), modifiedSpec = null)
                ?.also { archive.addIfNeeded(it) }
    }
}