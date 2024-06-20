package org.evomaster.core.problem.rest


import org.evomaster.core.problem.enterprise.auth.NoAuth
import org.evomaster.core.search.EvaluatedAction
import org.evomaster.core.search.EvaluatedIndividual


/**
 * Utility functions to select one or more REST Individual from a group, based on different criteria.
 * Or to select specific actions.
 */
object RestIndividualSelectorUtils {

    /**
     * check a given action for the given conditions
     */
    private fun checkIfActionSatisfiesConditions(act: EvaluatedAction,
                                         verb: HttpVerb? = null,
                                         path: RestPath? = null,
                                         status: Int? = null,
                                         statusGroup: StatusGroup? = null,
                                         mustBeAuthenticated : Boolean = false
                                         ) : Boolean {

        // get actions and results first
        val action = act.action as RestCallAction
        val resultOfAction = act.result as RestCallResult

        // now check all conditions to see if any of them make the action not be included

        // verb
        if (verb != null && action.verb != verb) {
                return false
        }

        // path
        if(path != null && !action.path.isEquivalent(path)) {
            return false
        }

        // status
        if(status!=null && resultOfAction.getStatusCode() != status){
            return false
        }

        if(statusGroup != null && !statusGroup.isInGroup(resultOfAction.getStatusCode())){
            return false
        }

        // authenticated or not
        if(mustBeAuthenticated && action.auth is NoAuth){
            return false
        }

        return true
    }

    fun findAction(
        individualsInSolution: List<EvaluatedIndividual<RestIndividual>>,
        verb: HttpVerb? = null,
        path: RestPath? = null,
        status: Int? = null,
        statusGroup: StatusGroup? = null,
        authenticated: Boolean = false
    ): RestCallAction? {

        return findEvaluatedAction(individualsInSolution,verb,path,status,statusGroup,authenticated)
            ?.action as RestCallAction?
    }


    fun findEvaluatedAction(
        individualsInSolution: List<EvaluatedIndividual<RestIndividual>>,
        verb: HttpVerb? = null,
        path: RestPath? = null,
        status: Int? = null,
        statusGroup: StatusGroup? = null,
        authenticated: Boolean = false
    ): EvaluatedAction? {

        if(status != null && statusGroup!= null){
            throw IllegalArgumentException("Shouldn't specify both status and status group")
        }

        individualsInSolution.forEach {ind ->
            ind.evaluatedMainActions().forEach { a ->
                if(checkIfActionSatisfiesConditions(a, verb, path, status, statusGroup, authenticated)){
                    return a
                }
            }
        }
        return null
    }


    /**
     * Find individuals which contain actions with given parameters, such as verb, path, result.
     * If any of those parameters are not given as null, that parameter is used for filtering individuals.
     */
    fun findIndividuals(
        individualsInSolution: List<EvaluatedIndividual<RestIndividual>>,
        verb: HttpVerb? = null,
        path: RestPath? = null,
        status: Int? = null,
        statusGroup: StatusGroup? = null,
        authenticated: Boolean = false
    ): List<EvaluatedIndividual<RestIndividual>> {

        if(status != null && statusGroup!= null){
            throw IllegalArgumentException("Shouldn't specify both status and status group")
        }

        return individualsInSolution.filter {ind ->
            ind.evaluatedMainActions().any { a ->
                checkIfActionSatisfiesConditions(a, verb, path, status, statusGroup, authenticated)
            }
        }
    }

    /**
     * get all action definitions from the swagger based on the given verb
     */
    fun getAllActionDefinitions(actionDefinitions: List<RestCallAction>, verb: HttpVerb): List<RestCallAction> {
        return actionDefinitions.filter { it.verb == verb }
    }

    /**
     * Given a resource path, we want to find any individual that has a successful create operation for that resource
     *
     * Assume for example the resource "/users/{id}"
     *
     * We could search for a
     * PUT "/users/id"
     * that returns 201
     *
     * or a
     * POST "/users"
     * that returns something in 2xx (not necessarily 201)
     *
     * @return null if none found
     */
    fun findIndividualWithEndpointCreationForResource( individuals: List<EvaluatedIndividual<RestIndividual>>,
                                                       resourcePath: RestPath,
                                                       mustBeAuthenticated: Boolean
    ) : Pair<EvaluatedIndividual<RestIndividual>,Endpoint>?{

        val existingPuts  = findIndividuals(
            individuals,
            HttpVerb.PUT,
            resourcePath,
            201, //if PUT, must be 201
            statusGroup = null,
            mustBeAuthenticated
        )

        if(existingPuts.isNotEmpty()){
            return Pair(
                existingPuts.sortedBy { it.individual.size() }[0],
                Endpoint(HttpVerb.PUT, resourcePath)
            )
        }

        if(resourcePath.isRoot()){
            return null
        }

        val parent = resourcePath.parentPath()

        val existingPosts = findIndividuals(
            individuals,
            HttpVerb.POST, // it is not uncommon to give a 200 even if creating new resource
            parent,
            statusGroup = StatusGroup.G_2xx,
            authenticated = mustBeAuthenticated
        )

        if(existingPosts.isNotEmpty()){
            return Pair(
                existingPosts.sortedBy { it.individual.size() }[0],
                Endpoint(HttpVerb.POST, resourcePath)
            )
        }

        //found nothing
        return null
    }


    fun getIndexOfAction(individual: EvaluatedIndividual<RestIndividual>,
                         verb: HttpVerb,
                         path: RestPath,
                         statusCode: Int
    ) : Int {

        val actions = individual.evaluatedMainActions()

        for(index in actions.indices){
            val a = actions[index].action as RestCallAction
            val r = actions[index].result as RestCallResult

            if(a.verb == verb && a.path.isEquivalent(path) && r.getStatusCode() == statusCode){
                return index
            }
        }

        return -1
    }

    /**
     * Create a copy of individual, where all main actions after index are removed
     */
    @Deprecated("Rather use implementation in RestIndividualBuilder")
    fun sliceAllCallsInIndividualAfterAction(restIndividual: RestIndividual, actionIndex: Int) : RestIndividual {

        // we need to check that the index is within the range
        if (actionIndex < 0 || actionIndex > restIndividual.size() -1) {
            throw IllegalArgumentException("Action index has to be between 0 and ${restIndividual.size()}")
        }

        val ind = restIndividual.copy() as RestIndividual

        val n = ind.seeMainExecutableActions().size

        /*
            We start from last, going backward.
            So, actionIndex stays the same
         */
        for(i in n-1 downTo actionIndex+1){
            ind.removeMainExecutableAction(i)
        }

        ind.fixGeneBindingsIfNeeded()
        ind.removeLocationId()

        return ind
    }
}
