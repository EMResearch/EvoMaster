package org.evomaster.core.problem.rest.service

import com.google.inject.Inject
import javax.annotation.PostConstruct

import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.enterprise.auth.AuthSettings
import org.evomaster.core.problem.enterprise.auth.NoAuth
import org.evomaster.core.problem.httpws.auth.HttpWsAuthenticationInfo
import org.evomaster.core.problem.rest.*
import org.evomaster.core.problem.rest.param.PathParam
import org.evomaster.core.problem.rest.resource.RestResourceCalls

import org.evomaster.core.search.*
import org.evomaster.core.search.service.Archive
import org.evomaster.core.search.service.IdMapper
import org.evomaster.core.search.service.Randomness
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * Service class used to do security testing after the search phase
 */
class SecurityRest {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SecurityRest::class.java)
    }

    /**
     * Archive including test cases
     */
    @Inject
    private lateinit var archive: Archive<RestIndividual>

    @Inject
    private lateinit var sampler: AbstractRestSampler

    @Inject
    private lateinit var randomness: Randomness

    @Inject
    private lateinit var fitness: RestFitness

    @Inject
    private lateinit var idMapper: IdMapper

    @Inject
    private lateinit var builder: RestIndividualBuilder

    /**
     * All actions that can be defined from the OpenAPI schema
     */
    private lateinit var actionDefinitions: List<RestCallAction>


    /**
     * Individuals in the solution.
     * Derived from archive.
     */
    private lateinit var individualsInSolution: List<EvaluatedIndividual<RestIndividual>>

    private lateinit var authSettings: AuthSettings

    /**
     * Function called after init. This function initializes REST sampler definitions
     * and authentication settings.
     */
    @PostConstruct
    private fun postInit() {

        actionDefinitions = sampler.getActionDefinitions() as List<RestCallAction>

        authSettings = sampler.authentications
    }

    /**
     * Apply a set rule of generating new test cases, which will be added to the current archive.
     * Extract a new test suite(s) from the archive.
     */
    fun applySecurityPhase(): Solution<RestIndividual> {

        // extract individuals from the archive
        val archivedSolution: Solution<RestIndividual> = this.archive.extractSolution()
        individualsInSolution = archivedSolution.individuals


        // we can see what is available from the schema, and then check if already existing a test for it in archive
        // newly generated tests will be added back to archive
        addForAccessControl()

        //TODO possible other kinds of security tests here

        // just return the archive for solutions including the security tests.
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
        //eg, ok PUT but not DELETE or PATCH
        //eg, ok PATCH but not DELETE or PUT

        // eg 401/403 info leakage
        //etc.
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
            ---    make sure that DELETE is bound on some resource path as the create operation
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
        if (!checkForAtLeastNumberOfAuthenticatedUsers(2)) {
            // nothing to test if there are not at least 2 users
            LoggingUtil.getInfoLogger().debug(
                "Security test handleForbiddenDeleteButOkPutOrPatch requires at least 2 authenticated users"
            )
            return
        }

        // From schema, check all DELETE operations, in order to do that
        // obtain DELETE operations in the SUT according to the swagger
        val deleteOperations = RestIndividualSelectorUtils.getAllActionDefinitions(actionDefinitions, HttpVerb.DELETE)

        // for each endpoint for which there is a DELETE operation
        deleteOperations.forEach { delete ->

            // from archive, search if there is any test with a DELETE returning a 403
            val existing403 = RestIndividualSelectorUtils.findIndividuals(
                individualsInSolution,
                HttpVerb.DELETE,
                delete.path,
                403,
                authenticated = true
            )
            // individual to choose for test, this is the individual we are going to manipulate
            val individualToChooseForTest: RestIndividual

            // if there is such an individual
            if (existing403.isNotEmpty()) {

                // current individual in the list of existing 403. Since the list is not empty,\
                // we can just get the first item
                // TODO add test for existing 403
                val currentIndividualWith403 = existing403[0]

                val deleteActionIndex = RestIndividualSelectorUtils.getIndexOfAction(
                    currentIndividualWith403,
                    HttpVerb.DELETE,
                    delete.path,
                    403
                )

                // slice the individual in a way that delete all calls after the DELETE request
                individualToChooseForTest = builder.sliceAllCallsInIndividualAfterAction(
                    currentIndividualWith403.individual,
                    deleteActionIndex
                )

            } else {
                // there is not. need to create it based on successful create resources with authenticated user
                // but, first let's check if we can have any successfully delete action

                /*
                    We have a DELETE path in form for example
                    /users/{id}
                    and want to get a _resolved_ creation action (either PUT or POST) for it.
                    The new DELETE we are going to create must point to the same resolved action.
                    But DELETE could have query parameters and possibly body payloads... all with
                    constraints that must be satisfied.
                    So we cannot easily just create it from scratch.
                    Need to re-use an existing one, if any.
                */
                var deleteIndividuals = RestIndividualSelectorUtils.findIndividuals(
                    individualsInSolution,
                    HttpVerb.DELETE,
                    delete.path,
                    statusGroup = StatusGroup.G_2xx,
                )
                if(deleteIndividuals.isEmpty()){
                    /*
                        This needs a bit of explanation.
                        We want to get a DELETE that works, with failed constraint validation on
                        query parameters or body payloads.
                        Ideally, a 2xx would do.
                        But what if we could not create any because they all fail to point to an existing
                        resource? if 404, could still be fine, as then we link it to creation operation
                     */
                    deleteIndividuals = RestIndividualSelectorUtils.findIndividuals(
                        individualsInSolution,
                        HttpVerb.DELETE,
                        delete.path,
                        status = 404
                    )
                }
                if(deleteIndividuals.isEmpty()){
                    //no point trying to create a DELETE action directly, if none was evolved at all...
                    //as likely would fail as well here
                    return@forEach
                }

                //start from base test in which resource is created
                val (creationIndividual, creationEndpoint) = RestIndividualSelectorUtils
                    .findIndividualWithEndpointCreationForResource(
                        individualsInSolution,
                        delete.path,
                        true
                    ) ?: return@forEach

                // find the index of the creation action
                val actionIndexForCreation = creationIndividual.individual.getActionIndex(
                    creationEndpoint.verb,
                    creationEndpoint.path
                )
                val creationAction = creationIndividual.individual.seeMainExecutableActions()[actionIndexForCreation]
                assert(creationAction.auth !is NoAuth)

                //we don't need anything after the creation action
                val sliced = builder.sliceAllCallsInIndividualAfterAction(
                    creationIndividual.individual,
                    actionIndexForCreation
                )

                val deleteInd = deleteIndividuals.first().individual
                val deleteActionIndex = deleteInd.getActionIndex(HttpVerb.DELETE, delete.path)
                val deleteAction = deleteInd.seeMainExecutableActions()[deleteActionIndex].copy() as RestCallAction
                assert(deleteAction.verb == HttpVerb.DELETE && deleteAction.path.isEquivalent(delete.path))
                deleteAction.resetLocalId()
                deleteAction.auth = authSettings.getDifferentOne(creationAction.auth.name, HttpWsAuthenticationInfo::class.java, randomness)


                if(creationEndpoint.path.isEquivalent(delete.path)){
                    deleteAction.bindBasedOn(creationAction.path, creationAction.parameters.filterIsInstance<PathParam>(),null)
                } else {
                   builder.linkDynamicCreateResource(creationAction,deleteAction)
                }

                sliced.addResourceCall(restCalls = RestResourceCalls(actions = mutableListOf(deleteAction), sqlActions = listOf()))

                individualToChooseForTest = sliced
            }

            // After having a set of requests in which the last one is a 403 DELETE call with another user, add a PUT
            // or PATCH with same user as 403 DELETE

            //at this point, the individual we are building should end with a 403 DELETE
            val lastAction = individualToChooseForTest.seeMainExecutableActions().last()
            assert(lastAction.verb == HttpVerb.DELETE)
            // but, for checking 403, we need to evaluate it.
            // so, we ll do this check at the end
            val lastActionIndex = individualToChooseForTest.seeMainExecutableActions().size - 1

            val put = RestIndividualSelectorUtils.findAction(
                individualsInSolution,
                HttpVerb.PUT,
                delete.path,
                statusGroup = StatusGroup.G_2xx
            )?.copy() as RestCallAction?


            val patch = RestIndividualSelectorUtils.findAction(
                individualsInSolution,
                HttpVerb.PATCH,
                delete.path,
                statusGroup = StatusGroup.G_2xx
            )?.copy() as RestCallAction?

            listOf(put, patch).forEach {
                if(it != null){
                    it.resetLocalId()
                    it.auth = lastAction.auth

                    val finalIndividual = individualToChooseForTest.copy() as RestIndividual
                    finalIndividual.addResourceCall(restCalls = RestResourceCalls(actions = mutableListOf(it), sqlActions = listOf()))
                    finalIndividual.modifySampleType(SampleType.SECURITY)

                    val evaluatedIndividual = fitness.computeWholeAchievedCoverageForPostProcessing(finalIndividual)
                    if(evaluatedIndividual == null){
                        log.warn("Failed to evaluate constructed individual in security testing phase")
                        return@forEach
                    }

                    //first, let's verify indeed second last action if a 403 DELETE. otherwise, it is a problem
                    val ema = evaluatedIndividual.evaluatedMainActions()
                    val secondLast = ema[ema.size-2]
                    if(!(secondLast.action is RestCallAction && secondLast.action.verb == HttpVerb.DELETE
                            && secondLast.result is RestCallResult && secondLast.result.getStatusCode() == 403)){
                        log.warn("Issue with constructing evaluated individual. Expected a 403 DELETE, but got: $secondLast")
                        return@forEach
                    }

                    /*
                        now we can finally ask, is there a problem with last call?
                        if so, this should had already been handled in the fitness function.
                        regardless, we still need to create a new testing target, regardless of whether a fault
                        is found or not, otherwise this test will be lost.
                        if no fault is found, this kind of test is good for regression.

                        note: we do not compute fault here because, if we do it only here,
                        then we would lose this info if the test case is re-evaluated
                        for any reason... eg, in minimizer
                     */

                    val scenarioId = idMapper.handleLocalTarget("security:forbiddenDelete:${delete.path}")
                    evaluatedIndividual.fitness.updateTarget(scenarioId, 1.0)

                    // add the evaluated individual to the archive
                    val added = archive.addIfNeeded(evaluatedIndividual)
                    //if we arrive here, should always be added, because we are creating a new testing target
                    assert(added)
                }
            }
        }
    }



    /**
     * Information leakage
     * accessing endpoint with id with not authorized should return 403, even if not exists.
     * otherwise, if returning 404, we can find out that the resource does not exist.
     */
    fun handleNotAuthorizedResourceExistenceLeakage() {

        //TODO

        // There has to be at least two authenticated users
        if (!checkForAtLeastNumberOfAuthenticatedUsers(2)) {
            // nothing to test if there are not at least 2 users
            LoggingUtil.getInfoLogger().debug(
                "Security test handleNotAuthorizedInAnyCase requires at least 2 authenticated users"
            )
            return
        }

        // get all endpoints each user has access to (this can be a function since we need this often)

        // among endpoints userA has access, those endpoints should give 2xx as response

        // find an endpoint userA does not have access but another user has access, this should give 403

        // try with an endpoint that does not exist, this should give 403 as well, not 404.


    }


    /**
     * Wrong Authorization
     *
     * User A, access endpoint X, but get 401 (instead of 403).
     * In theory, a bug. But, could be false positive if A is misconfigured.
     * How to check it?
     * See if on any other endpoint Y we get a 2xx with A.
     * But, maybe Y does not need authentication...
     * so, check if there is any test case for which on Y we get a 401 or 403.
     * if yes, then X is buggy, as it should rather have returned 403 for A.
     * This seems to actually happen for familie-ba-sak, new NAV SUT.
     */
    fun handleUnauthorizedInsteadOfForbidden() {

    }

    /**
     *
     */
    private fun checkForAtLeastNumberOfAuthenticatedUsers(numberOfUsers: Int): Boolean {

        // check the number of authenticated users
        if (authSettings.size(HttpWsAuthenticationInfo::class.java) < numberOfUsers) {

            LoggingUtil.getInfoLogger().debug(
                "Security test for this method requires at least $numberOfUsers authenticated users"
            )
            return false
        }

        return true
    }

}