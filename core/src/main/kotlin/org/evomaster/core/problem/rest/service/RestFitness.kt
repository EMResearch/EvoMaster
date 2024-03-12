package org.evomaster.core.problem.rest.service

import org.evomaster.core.sql.SqlAction
import org.evomaster.core.mongo.MongoDbAction
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestCallResult
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.search.action.ActionFilter
import org.evomaster.core.search.action.ActionResult
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.FitnessValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class RestFitness : AbstractRestFitness<RestIndividual>() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RestFitness::class.java)
    }


    override fun doCalculateCoverage(
        individual: RestIndividual,
        targets: Set<Int>,
        allCovered: Boolean
    ): EvaluatedIndividual<RestIndividual>? {

        rc.resetSUT()

        val cookies = getCookies(individual)
        val tokens = getTokens(individual)

        if (log.isTraceEnabled){
            log.trace("do evaluate the individual, which contains {} dbactions and {} rest actions",
                individual.seeInitializingActions().size,
                individual.seeAllActions().size)
        }

        val actionResults: MutableList<ActionResult> = mutableListOf()

        doDbCalls(individual.seeInitializingActions().filterIsInstance<SqlAction>(), actionResults = actionResults)
        doMongoDbCalls(individual.seeInitializingActions().filterIsInstance<MongoDbAction>(), actionResults = actionResults)


        val fv = FitnessValue(individual.size().toDouble())


        //used for things like chaining "location" paths
        val chainState = mutableMapOf<String, String>()

        //run the test, one action at a time
        for (i in 0 until individual.seeMainExecutableActions().size) {

            val a = individual.seeMainExecutableActions()[i] as RestCallAction

            if (log.isTraceEnabled){
                log.trace("handle rest action at index {}, and the action is {}, and the genes are",
                    i,
                    "${a.verb}:${a.resolvedPath()}",
                    a.seeTopGenes().joinToString(","){
                        "${it::class.java.simpleName}:${
                            try {
                                it.getValueAsRawString()
                            }catch (e: Exception){
                                "null"
                            }
                        }"
                    }
                )
            }

            registerNewAction(a, i)

            var ok = false

            if (a is RestCallAction) {
                ok = handleRestCall(a, actionResults, chainState, cookies, tokens)
                /*
                    the action might be stopped due to e.g., timeout (see [handleRestCall]),
                    but the property of [stopping] is not handle.
                    we can also handle the property inside [handleRestCall]
                 */
                actionResults.filterIsInstance<RestCallResult>()[i].stopping = !ok
            } else {
                throw IllegalStateException("Cannot handle: ${a.javaClass}")
            }

            if (!ok) {
                break
            }
        }

        if (log.isTraceEnabled){
            log.trace("evaluation ends")
        }

        val restActionResults = actionResults.filterIsInstance<RestCallResult>()
        restActionResultHandling(individual, targets, allCovered, restActionResults, fv)?:return null

        if (log.isTraceEnabled){
            log.trace("restActionResult are handled")
        }

        if (actionResults.size > individual.seeActions(ActionFilter.ALL).size)
            log.warn("initialize invalid evaluated individual")

        return EvaluatedIndividual(fv, individual.copy() as RestIndividual, actionResults, trackOperator = individual.trackOperator, index = time.evaluatedIndividuals, config = config)
    }

}