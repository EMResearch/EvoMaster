package org.evomaster.core.search.service

import org.evomaster.client.java.instrumentation.shared.ObjectiveNaming
import java.util.concurrent.atomic.AtomicInteger


/**
 * To represent and identify a coverage target, we use numeric ids.
 * But those are not very "descriptive" of what the targets actually are.
 * So, we need a mapping to a String description (which itself would be
 * a unique id).
 * Note: we do not use these strings directly as it would be too inefficient.
 * Furthermore, as the ids are passed as query parameters in HTTP GET requests,
 * there are limits on length
 */
class IdMapper {

    companion object {

        private const val FAULT_OBJECTIVE_PREFIX = "PotentialFault"

        /**
         * local objective prefix might depend on problems, eg, HTTP_SUCCESS and HTTP_FAULT for REST
         * it can be identified with its numeric id, ie, less than 0
         * however we need this key to specify whether to consider such objectives in impact collections
         */
        const val LOCAL_OBJECTIVE_KEY = "Local"

        private const val FAULT_DESCRIPTIVE_ID_PREFIX = "${FAULT_OBJECTIVE_PREFIX}_"

        /**
         * all prefixes used for defining testing objectives
         */
        val ALL_ACCEPTED_OBJECTIVE_PREFIXES : List<String> = ObjectiveNaming.getAllObjectivePrefixes().plus(LOCAL_OBJECTIVE_KEY).plus(
            FAULT_OBJECTIVE_PREFIX)

        private const val FAULT_500 = "500_"

        // RPC internal error, eg thrift application internal error exception
        private const val RPC_INTERNAL_ERROR = "INTERNAL_ERROR_"

        // RPC service error which is customized by user
        private const val RPC_SERVICE_ERROR = "SERVICE_ERROR_"

        // exception for RPC
        private const val DECLARED_EXCEPTION = "DECLARED_EXCEPTION_"

        // unexpected exception for RPC
        private const val UNEXPECTED_EXCEPTION = "UNEXPECTED_EXCEPTION_"

        // an RPC call which is handled as defined
        private const val RPC_HANDLED = "RPC_HANDLED_"

        // an RPC call which achieves a successful business logic
        private const val RPC_HANDLED_SUCCESS = "RPC_HANDLED_SUCCESS_"

        // an RPC call which fails to achieve a successful business logic
        private const val RPC_HANDLED_ERROR = "RPC_HANDLED_ERROR_"

        private const val FAULT_PARTIAL_ORACLE = "PartialOracle_"

        private const val GQL_ERRORS_PREFIX = "GQL_ERRORS_ACTION"

        private const val GQL_ERRORS_LINE_PREFIX = "GQL_ERRORS_LINE"

        private const val GQL_NO_ERRORS = "GQL_NO_ERRORS"

        private const val WEB_FAULT = "WebFault_"

        private const val MALFORMED_HTML_ERROR = "MALFORMED_HTML_ERROR_"

        private const val MALFORMED_URI = "MALFORMED_URI_"

        private const val BROKEN_LINK = "BROKEN_LINK_"

        fun isFault(descriptiveId: String) =
            descriptiveId.startsWith(FAULT_DESCRIPTIVE_ID_PREFIX)
                    || isGQLErrors(descriptiveId, true)
                    || descriptiveId.startsWith(WEB_FAULT)

        fun isFault500(descriptiveId: String) = descriptiveId.startsWith(FAULT_DESCRIPTIVE_ID_PREFIX+ FAULT_500)

        /**
         * @return if [descriptiveId] represents an RPC call which leads to an internal error
         */
        fun isRPCInternalError(descriptiveId: String) = descriptiveId.startsWith(FAULT_DESCRIPTIVE_ID_PREFIX + RPC_INTERNAL_ERROR)

        /**
         * @return if [descriptiveId] represents an RPC call with thrown unexpected exception
         */
        fun isUnexpectedException(descriptiveId: String) = descriptiveId.startsWith(FAULT_DESCRIPTIVE_ID_PREFIX + UNEXPECTED_EXCEPTION)

        /**
         * @return if [descriptiveId] represents an RPC call with an exception which is declared
         */
        fun isRPCDeclaredException(descriptiveId: String) = descriptiveId.startsWith(DECLARED_EXCEPTION)

        /**
         * @return if [descriptiveId] represents an RPC call with thrown exception
         */
        fun isRPCException(descriptiveId: String) = isRPCDeclaredException(descriptiveId) || isUnexpectedException(descriptiveId)

        /**
         * @return if [descriptiveId] represents an RPC call which is handled as defined
         */
        fun isRPCHandled(descriptiveId: String) = descriptiveId.startsWith(RPC_HANDLED)

        /**
         * @return if [descriptiveId] represents an RPC call which achieves a successful scenario
         */
        fun isRPCHandledAndSuccess(descriptiveId: String) = descriptiveId.startsWith(RPC_HANDLED_SUCCESS)

        /**
         * @return if [descriptiveId] represents an RPC call which achieves an error scenario
         */
        fun isRPCHandledButError(descriptiveId: String) = descriptiveId.startsWith(RPC_HANDLED_ERROR)

        /**
         * @return if [descriptiveId] represents an RPC call which results in a service error
         */
        fun isRPCServiceError(descriptiveId: String) = descriptiveId.startsWith(FAULT_DESCRIPTIVE_ID_PREFIX + RPC_SERVICE_ERROR)

        fun isFaultPartialOracle(descriptiveId: String) = descriptiveId.startsWith(FAULT_DESCRIPTIVE_ID_PREFIX+ FAULT_PARTIAL_ORACLE)

        fun isGQLErrors(descriptiveId: String, withLine: Boolean = false) =
                if (!withLine) descriptiveId.startsWith(GQL_ERRORS_PREFIX)
                else descriptiveId.startsWith(GQL_ERRORS_LINE_PREFIX)

        fun isGQLNoErrors(descriptiveId: String) = descriptiveId.startsWith(GQL_NO_ERRORS)

        fun faultInfo(descriptiveId: String) : String{
            if(! isFault(descriptiveId)){
                throw IllegalArgumentException("Invalid non-fault input id: $descriptiveId")
            }
            return descriptiveId.substring(FAULT_DESCRIPTIVE_ID_PREFIX.length)
        }

        fun isLocal(id: Int): Boolean = id < 0

        fun isMethodReplacementTarget(descriptiveId: String) = descriptiveId.startsWith(ObjectiveNaming.METHOD_REPLACEMENT)

        fun getFaultDescriptiveIdSecurity(postfix: String): String {
            return FAULT_DESCRIPTIVE_ID_PREFIX + "SECURITY_" + postfix
        }
    }

    private val mapping: MutableMap<Int, String> = mutableMapOf()

    private val reverseMapping: MutableMap<String, Int> = mutableMapOf()

    /**
     * Counter used to create local id, based on the return values
     * of the interactions with the SUT.
     * The counter has to be negative to avoid collisions with
     * ids generated on the SUT (eg, via bytecode instrumentation
     * monitoring)
     */
    private val localCounter = AtomicInteger(-1)

    fun addMapping(id: Int, descriptiveId: String) {
        mapping[id] = descriptiveId
        reverseMapping[descriptiveId] = id
    }

    fun getDescriptiveId(id: Int) = mapping[id] ?: "undefined"

    fun handleLocalTarget(descriptiveId: String): Int {
        return reverseMapping.getOrPut(descriptiveId, {
            val k = localCounter.decrementAndGet()
            mapping[k] = descriptiveId
            k
        })
    }

    fun getFaultDescriptiveIdForMalformedHtml(postfix: String): String{
        return WEB_FAULT + MALFORMED_HTML_ERROR + postfix
    }

    fun getFaultDescriptiveIdForMalformedURI(postfix: String) : String {
        return WEB_FAULT + MALFORMED_URI + postfix
    }

    fun getFaultDescriptiveIdForBrokenLink(postfix: String) : String {
        return WEB_FAULT + BROKEN_LINK + postfix
    }

    fun getFaultDescriptiveIdFor500(postfix: String): String {
        return FAULT_DESCRIPTIVE_ID_PREFIX + FAULT_500 + postfix
    }

    fun getFaultDescriptiveIdForInternalError(postfix: String): String {
        return FAULT_DESCRIPTIVE_ID_PREFIX + RPC_INTERNAL_ERROR + postfix
    }

    fun getFaultDescriptiveIdForPartialOracle(postfix: String): String {
        return FAULT_DESCRIPTIVE_ID_PREFIX + FAULT_PARTIAL_ORACLE + postfix
    }

    /*
        TODO double-check
        Is just using "method" name enough to identify a Query/Mutation in GQL?
        or could we get issue when there is name overloading? ie, queries with same
        name but different input signature.
        if so, should use a description of the input signatures to get unique ids...
        although likely this is really loooow priority
     */

    fun getGQLErrorsDescriptiveWithMethodName(method: String) = "$GQL_ERRORS_PREFIX:$method"

    fun getGQLErrorsDescriptiveWithMethodNameAndLine(line : String, method: String) = "${GQL_ERRORS_LINE_PREFIX}:${method}_$line"

    fun getGQLNoErrors(method: String) = "$GQL_NO_ERRORS:$method"


    fun getFaultDescriptiveIdForUnexpectedException(postfix: String): String {
        return FAULT_DESCRIPTIVE_ID_PREFIX + UNEXPECTED_EXCEPTION + postfix
    }

    fun getRPCServiceError(postfix: String): String {
        return FAULT_DESCRIPTIVE_ID_PREFIX + RPC_SERVICE_ERROR + postfix
    }

    fun getFaultDescriptiveIdForRPCDeclaredException(postfix: String): String {
        return FAULT_DESCRIPTIVE_ID_PREFIX + DECLARED_EXCEPTION + postfix
    }

    fun getHandledRPC(postfix: String): String {
        return RPC_HANDLED + postfix
    }

    fun getHandledRPCAndSuccess(postfix: String): String {
        return RPC_HANDLED_SUCCESS + postfix
    }

    fun getHandledRPCButError(postfix: String): String {
        return RPC_HANDLED_ERROR + postfix
    }

    fun isFault(id: Int) : Boolean = mapping[id]?.let{ isFault(it)} ?: false

    fun isFault500(id: Int): Boolean = mapping[id]?.let {isFault500(it)} ?: false

    fun isFaultExpectation(id: Int): Boolean = mapping[id]?.let{ isFaultPartialOracle(it) } ?:false

    fun isGQLErrors(id : Int, withLine: Boolean) : Boolean = mapping[id]?.let { isGQLErrors(it, withLine) } == true

    fun isGQLNoErrors(id : Int) : Boolean = mapping[id]?.let { isGQLNoErrors(it) } == true


    /**
     * @return if [id] refers to an RPC call with thrown unexpected exception
     */
    fun isRPCInternalError(id: Int) : Boolean = mapping[id]?.let { isRPCInternalError(it) } == true

    /**
     * @return if [id] refers to an RPC call with thrown unexpected exception
     */
    fun isUnexpectedException(id: Int) : Boolean = mapping[id]?.let { isUnexpectedException(it) } == true

    /**
     * @return if [id] refers to an RPC call with thrown exception which is declared
     */
    fun isRPCDeclaredException(id: Int) = mapping[id]?.let { isRPCDeclaredException(it) } == true

    /**
     * @return if [id] refers to an RPC call with thrown exception
     */
    fun isRPCException(id: Int) = mapping[id]?.let { isRPCException(it) } == true

    /**
     * @return if [id] refers to an RPC call which is handled as defined
     */
    fun isRPCHandled(id: Int) = mapping[id]?.let { isRPCHandled(it) } == true

    /**
     * @return if [id] refers to an RPC call which achieves a successful scenario
     */
    fun isRPCHandledAndSuccess(id: Int) = mapping[id]?.let { isRPCHandledAndSuccess(it) } == true

    /**
     * @return if [id] refers to an RPC call which achieves an error scenario
     */
    fun isRPCHandledButError(id: Int) = mapping[id]?.let { isRPCHandledButError(it) } == true

    /**
     * @return if [id] refers to an RPC call which results in a service error specified by user
     */
    fun isRPCServiceError(id: Int) : Boolean = mapping[id]?.let { isRPCServiceError(it) } == true
}