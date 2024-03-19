package org.evomaster.core.problem.rest.service

import com.google.inject.Inject
import javax.annotation.PostConstruct

import org.apache.http.HttpStatus

import org.evomaster.client.java.controller.api.dto.SutInfoDto
import org.evomaster.client.java.controller.api.dto.auth.AuthenticationDto

import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.enterprise.auth.AuthSettings
import org.evomaster.core.problem.httpws.auth.HttpWsAuthenticationInfo
import org.evomaster.core.problem.rest.*
import org.evomaster.core.problem.rest.param.PathParam
import org.evomaster.core.remote.service.RemoteController
import org.evomaster.core.remote.service.RemoteControllerImplementation

import org.evomaster.core.search.*
import org.evomaster.core.search.action.ActionResult
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.service.Archive
import org.evomaster.core.search.service.FitnessFunction
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.Sampler

//FIXME this needs to be cleaned-up

/**
 * Service class used to do security testing after the search phase
 */
class SecurityRest {

    /**
     * Archive including test cases
     */
    @Inject
    private lateinit var archive: Archive<RestIndividual>

    @Inject
    private lateinit var sampler: AbstractRestSampler

    @Inject
    private lateinit var randomness: Randomness


    /**
     * All actions that can be defined from the OpenAPI schema
     */
    private lateinit var actionDefinitions : List<RestCallAction>


    /**
     * Individuals in the solution
     */
    private lateinit var individualsInSolution : List<EvaluatedIndividual<RestIndividual>>

    private lateinit var authSettings: AuthSettings

    /**
     * Function called after init. This function initializes REST sampler definitions
     * and authentication settings.
     */
    @PostConstruct
    private fun postInit(){


        // get action definitions
        actionDefinitions = sampler.getActionDefinitions() as List<RestCallAction>

        authSettings = sampler.authentications
    }

    /**
     * Apply a set rule of generating new test cases, which will be added to the current archive.
     * Extract a new test suite(s) from the archive.
     */
    fun applySecurityPhase() : Solution<RestIndividual>{

        // extract individuals from the archive
        val archivedSolution : Solution<RestIndividual> = this.archive.extractSolution()
        individualsInSolution =  archivedSolution.individuals


        // we can see what is available from the schema, and then check if already existing a test for it in archive
        addForAccessControl()

        // just return the archive for solutions including the security test.
        return archive.extractSolution()
    }

    private fun addForAccessControl() {

        /*
            for black-box testing, we can only rely on REST-style guidelines to infer relations between operations
            and resources.
            for white-box, we can rely on what actually accessed in databases.
            however, there can be different kinds of databases (SQL and NoSQL, like Mongo and Neo4J).
            As we might not be able to support all of them (and other resources that are not stored in databases),
            even in white-box testing we still want to consider REST-style
         */

        accessControlBasedOnRESTGuidelines()
        //TODO implement access control based on database monitoring
        //accessControlBasedOnDatabaseMonitoring()
    }

    private fun accessControlBasedOnDatabaseMonitoring() {
        TODO("Not yet implemented")
    }

    private fun accessControlBasedOnRESTGuidelines() {

        // quite a few rules here that can be defined
        handleForbiddenDeleteButOkPutOrPatch()
        //TODO other rules
    }



    /**
     * Here we are considering this case:
     * - authenticated user A creates a resource X (status 2xx) | or possibly via SQL insertions
     * - authenticated user B gets 403 on DELETE X
     * - authenticated user B gets 200 on PUT/PATCH on X
     */
    private fun handleForbiddenDeleteButOkPutOrPatch() {

        /*
            what needs to be done here:
            - check if at least 2 users. if not, nothing to do
            - from schema, check all DELETE operations
            - from archive, search if there is any test with a DELETE returning a 403
            - do that for every different endpoint. There are 2 options:
            -- (1) there is such call,  then
            ---     make a copy of the individual
            ----    do a "slice" and remove all calls after the DELETE call (if any)
            -- (2) there is not. need to create it based on successful create resources with authenticated user
            ---    search for create resource for endpoint of DELETE
            ---    do slice and remove calls after create resource endpoint call
            ---    add a DELETE with different user (verify get a 403)
            - append new REST Call action (see mutator classes) for PATCH/PUT with different auth, based
              on action copies from action definitions. should be on same endpoint.
            - need to resolve path element parameters to point to same resolved endpoint as DELETE
            - as PUT/PATCH requires payloads and possible valid query parameters, search from archive an
              existing action that returns 2xx, and copy it to use as starting point
            - execute new test case with fitness function
            - create new testing targets based on status code of new actions
            - add to archive (if needed)
         */

        // Check if at least 2 users. if not, nothing to do
        if (authSettings.size(HttpWsAuthenticationInfo::class.java) <= 1) {
            // nothing to test if there are not at least 2 users
            LoggingUtil.getInfoLogger().debug(
                "Security test handleForbiddenDeleteButOkPutOrPatch requires at least 2 authenticated users")
            return
        }

        // From schema, check all DELETE operations, in order to do that
        // obtain DELETE operations in the SUT according to the swagger
        val deleteOperations = getAllActionDefinitions(HttpVerb.DELETE)

        // for each endpoint for which there is a DELETE operation
        deleteOperations.forEach { delete ->

            // from archive, search if there is any test with a DELETE returning a 403
            val existing403 : List<EvaluatedIndividual<RestIndividual>> =
                RestIndividualSelectorUtils.getIndividualsWithActionAndStatus(individualsInSolution, HttpVerb.DELETE, delete.path, 403)

            var individualToChooseForTest : RestIndividual

            // if there is such an individual
            if (existing403.isNotEmpty()) {

                // current individual in the list of existing 403. Since the list is not empty,\
                // we can just get the first item
                val currentIndividualWith403 = existing403[0]

                val deleteAction = RestIndividualSelectorUtils.getActionIndexFromIndividual(currentIndividualWith403.individual, HttpVerb.DELETE,
                    delete.path)

                val deleteActionIndex = RestIndividualSelectorUtils.getActionWithIndex(currentIndividualWith403, deleteAction)

                // slice the individual in a way that delete all calls after the DELETE request
                individualToChooseForTest = RestIndividualSelectorUtils.sliceAllCallsInIndividualAfterAction(currentIndividualWith403, deleteActionIndex)
            } else {
                // there is not. need to create it based on successful create resources with authenticated user
                var verbUsedForCreation : HttpVerb? = null;
                // search for create resource for endpoint of DELETE using PUT
                lateinit var existingEndpointForCreation : EvaluatedIndividual<RestIndividual>

                val existingPutForEndpointOfDelete : List<EvaluatedIndividual<RestIndividual>> =
                    RestIndividualSelectorUtils.getIndividualsWithActionAndStatusGroup(individualsInSolution, HttpVerb.PUT, delete.path,
                        "2xx")

                lateinit var existingPostReqForEndpointOfDelete : List<EvaluatedIndividual<RestIndividual>>

                if (existingPutForEndpointOfDelete.isNotEmpty()) {
                    existingEndpointForCreation = existingPutForEndpointOfDelete[0]
                    verbUsedForCreation = HttpVerb.PUT
                }
                else {
                    // if there is no such, search for an existing POST
                    existingPostReqForEndpointOfDelete = RestIndividualSelectorUtils.getIndividualsWithActionAndStatusGroup(
                        individualsInSolution,
                        HttpVerb.POST, delete.path,
                        "2xx"
                    )

                    if (existingPostReqForEndpointOfDelete.isNotEmpty()) {
                        existingEndpointForCreation = existingPostReqForEndpointOfDelete[0]
                        verbUsedForCreation = HttpVerb.DELETE
                    }

                }

                // if neither POST not PUT exists for the endpoint, we need to handle that case specifically
                if (existingEndpointForCreation == null) {
                    // TODO
                    LoggingUtil.getInfoLogger().debug(
                        "The archive does not contain any successful PUT or POST requests, this case is not handled")
                    return
                }

                var actionIndexForCreation = -1

                if (verbUsedForCreation != null) {

                    // so we found an individual with a successful PUT or POST,  we will slice all calls after PUT or POST
                    actionIndexForCreation = RestIndividualSelectorUtils.getActionIndexFromIndividual(
                        existingEndpointForCreation.individual,
                        verbUsedForCreation,
                        delete.path
                    )

                }

                // create a copy of the existingEndpointForCreation
                val existingEndpointForCreationCopy = existingEndpointForCreation.copy()
                val actionForCreation = RestIndividualSelectorUtils.getActionWithIndex(existingEndpointForCreation, actionIndexForCreation)

                RestIndividualSelectorUtils.sliceAllCallsInIndividualAfterAction(existingEndpointForCreationCopy, actionForCreation)

                // add a DELETE call with another user
                individualToChooseForTest =
                    createIndividualWithAnotherActionAddedDifferentAuthRest(existingEndpointForCreationCopy.individual,
                    actionForCreation, HttpVerb.DELETE )


            }

            // After having a set of requests in which the last one is a DELETE call with another user, add a PUT
            // with another user
            val deleteActionIndex = RestIndividualSelectorUtils.getActionIndexFromIndividual(individualToChooseForTest, HttpVerb.DELETE,
                delete.path)

            val deleteAction = RestIndividualSelectorUtils.getActionWithIndexRestIndividual(individualToChooseForTest, deleteActionIndex)

            var individualToAddToSuite = createIndividualWithAnotherActionAddedDifferentAuthRest(individualToChooseForTest,
                deleteAction, HttpVerb.PUT )

            /*
                FIXME fitness bean must be injected, and not instantiated directly.
                note, issue we have 2 different implementation, need to double-check
             */

            // TODO Fix how to perform fitness function evaluation here.

            // Then evaluate the fitness function to create evaluatedIndividual
            val fitness : FitnessFunction<RestIndividual> = RestResourceFitness()

            val evaluatedIndividual = fitness.computeWholeAchievedCoverageForPostProcessing(individualToAddToSuite)



            val newTarget = this.archive.coveredTargets().max() + 1

            // add the evaluated individual to the archive
            if (evaluatedIndividual != null) {
                archive.addIfNeeded(evaluatedIndividual)
            }
        }

    }

    /**
     * Create another individual from the individual by adding a new action with a new verb and with a different
     * authentication.
     * @param individual - REST indovidual which is the starting point
     * @param currentAction - REST action for the new individual
     * @param newActionVerb - verb for the new action, such as GET, POST
     */
    private fun createIndividualWithAnotherActionAddedDifferentAuthRest(individual: RestIndividual,
                                                                    currentAction : RestCallAction,
                                                                    newActionVerb : HttpVerb,
    ) : RestIndividual {

        var actionList = mutableListOf<RestCallAction>()

        for (act in individual.seeMainExecutableActions()) {
            actionList.add(act)
        }

        // create a new action with the authentication not used in current individual
        val authenticationOfOther = sampler.authentications.getDifferentOne(currentAction.auth.name, HttpWsAuthenticationInfo::class.java, randomness)

        var newRestCallAction :RestCallAction? = null;

        if (authenticationOfOther != null) {
            newRestCallAction = RestCallAction("newDelete", newActionVerb, currentAction.path,
                currentAction.parameters.toMutableList(), authenticationOfOther  )
        }

        if (newRestCallAction != null) {
            actionList.add(newRestCallAction)
        }


        val newIndividual = RestIndividual(actionList, SampleType.SECURITY)

        return newIndividual

    }

    private fun getAllActionDefinitions(verb: HttpVerb): List<RestCallAction> {
        return actionDefinitions.filter { it.verb == verb }
    }

}