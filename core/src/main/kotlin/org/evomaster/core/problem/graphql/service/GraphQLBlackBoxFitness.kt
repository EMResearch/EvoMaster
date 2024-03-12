package org.evomaster.core.problem.graphql.service

import org.evomaster.client.java.controller.api.dto.AdditionalInfoDto
import org.evomaster.core.problem.graphql.GraphQLAction
import org.evomaster.core.problem.graphql.GraphQLIndividual
import org.evomaster.core.problem.graphql.GraphQlCallResult
import org.evomaster.core.problem.httpws.HttpWsCallResult
import org.evomaster.core.search.action.ActionResult
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.FitnessValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class GraphQLBlackBoxFitness : GraphQLFitness() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GraphQLBlackBoxFitness::class.java)
    }

    override fun doCalculateCoverage(
        individual: GraphQLIndividual,
        targets: Set<Int>,
        allCovered: Boolean
    ): EvaluatedIndividual<GraphQLIndividual>? {

        if(config.bbExperiments){
            /*
                If we have a controller, we MUST reset the SUT at each test execution.
                This is to avoid memory leaks in the dat structures used by EM.
                Eg, this was a huge problem for features-service with AdditionalInfo having a
                memory leak
             */
            rc.resetSUT()
        }

        val fv = FitnessValue(individual.size().toDouble())

        val actionResults: MutableList<ActionResult> = mutableListOf()

        val actions = individual.seeMainExecutableActions() as List<GraphQLAction>

        //run the test, one action at a time
        for (i in actions.indices) {

            val a = actions[i]

            //TODO handle WM here

            val ok = handleGraphQLCall(a, actionResults, mapOf(), mapOf())
            actionResults[i].stopping = !ok

            if (!ok) {
                break
            }
        }

        handleResponseTargets(fv, actions, actionResults, listOf())

        return EvaluatedIndividual(fv, individual.copy() as GraphQLIndividual, actionResults, trackOperator = individual.trackOperator, index = time.evaluatedIndividuals, config = config)
    }

    override fun getlocation5xx(status: Int, additionalInfoList: List<AdditionalInfoDto>, indexOfAction: Int, result: HttpWsCallResult, name: String): String? {
        /*
            In Black-Box testing, there is no info from the source/bytecode
         */
        return null
    }

    override fun getGraphQLErrorWithLineInfo(
        additionalInfoList: List<AdditionalInfoDto>,
        indexOfAction: Int,
        result: GraphQlCallResult,
        name: String
    ): String? {
        /*
            In Black-Box testing, there is no info from the source/bytecode
         */
        return null
    }
}