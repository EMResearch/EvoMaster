package org.evomaster.core.problem.rpc.service

import com.google.inject.Inject
import org.evomaster.client.java.controller.api.dto.AdditionalInfoDto
import org.evomaster.client.java.controller.api.dto.problem.rpc.exception.RPCExceptionType
import org.evomaster.core.Lazy
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.problem.api.service.ApiWsFitness
import org.evomaster.core.problem.enterprise.EnterpriseActionGroup
import org.evomaster.core.problem.rpc.RPCCallAction
import org.evomaster.core.problem.rpc.RPCCallResult
import org.evomaster.core.problem.rpc.RPCCallResultCategory
import org.evomaster.core.problem.rpc.RPCIndividual
import org.evomaster.core.problem.rpc.param.RPCParam
import org.evomaster.core.problem.util.ParamUtil
import org.evomaster.core.search.action.ActionResult
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.GroupsOfChildren
import org.evomaster.core.search.gene.interfaces.CollectionGene
import org.evomaster.core.taint.TaintAnalysis
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * created by manzhang on 2021/11/26
 */
class RPCFitness : ApiWsFitness<RPCIndividual>() {

    companion object{
        private val log: Logger = LoggerFactory.getLogger(RPCFitness::class.java)

        const val RPC_DEFAULT_FAULT_CODE = "RPC_framework_code"
    }

    @Inject lateinit var rpcHandler: RPCEndpointsHandler

    override fun doCalculateCoverage(
        individual: RPCIndividual,
        targets: Set<Int>,
        allCovered: Boolean
    ): EvaluatedIndividual<RPCIndividual>? {

        rc.resetSUT()

        // TODO handle auth
        val actionResults: MutableList<ActionResult> = mutableListOf()

        doDbCalls(individual.seeInitializingActions().filterIsInstance<SqlAction>(), actionResults = actionResults)

        val fv = FitnessValue(individual.size().toDouble())

        run loop@{
            individual.seeAllActions().filterIsInstance<RPCCallAction>().forEachIndexed { index, action->
                val ok = executeNewAction(action, index, actionResults)
                if (!ok) return@loop
            }
        }

        val dto = updateFitnessAfterEvaluation(targets, allCovered, individual, fv)
                ?: return null
        handleExtra(dto, fv)

//        expandIndividual(individual)
        /*
            TODO Man handle targets regarding info in responses,
            eg, exception
                there is no specific status info in response for thrift,
                    then we might support customized property as we mentioned in industry paper (optional)
                status info in GRPC, see https://grpc.github.io/grpc/core/md_doc_statuscodes.html
         */
        val rpcActionResults = actionResults.filterIsInstance<RPCCallResult>()
        handleResponseTargets(fv, individual.seeAllActions().filterIsInstance<RPCCallAction>(), rpcActionResults, dto.additionalInfoList)

        if (config.isEnabledTaintAnalysis()) {
            Lazy.assert { rpcActionResults.size == dto.additionalInfoList.size }
            TaintAnalysis.doTaintAnalysis(individual, dto.additionalInfoList, randomness, config.enableSchemaConstraintHandling)
        }

        return EvaluatedIndividual(fv, individual.copy() as RPCIndividual, actionResults, trackOperator = individual.trackOperator, index = time.evaluatedIndividuals, config = config)

    }

    // Man: comment this for the comment, the expand is now handled after each action execution
//    private fun expandIndividual(
//        individual: RPCIndividual
//    ){
//
//        /*
//            might later need to handle missing class when mocking database
//         */
//        val exMissingDto = individual.seeExternalServiceActions()
//            .filterIsInstance<RPCExternalServiceAction>()
//            .filterNot {
//                ((it.response as? ClassResponseParam)?:throw IllegalStateException("response of RPCExternalServiceAction should be RPCResponseParam, but it is ${it.response::class.java.simpleName}")).fromClass
//            }
//
//        if (exMissingDto.isEmpty()) {
//            return
//        }
//        val missingDtoClass = exMissingDto.map { (it.response as ClassResponseParam).className }
//        rpcHandler.getJVMSchemaForDto(missingDtoClass.toSet()).forEach { expandResponse->
//            exMissingDto.filter { (it.response as ClassResponseParam).run { !this.fromClass && expandResponse.key == this.className} }.forEach { a->
//                val gene = wrapWithOptionalGene(expandResponse.value, true) as OptionalGene
//                val updatedParam = (a.response as ClassResponseParam).copyWithSpecifiedResponseBody(gene)
//                updatedParam.responseParsedWithClass()
//                val update = UpdateForRPCResponseParam(updatedParam)
//                a.addUpdateForParam(update)
//            }
//        }
//    }

    private fun executeNewAction(action: RPCCallAction, index: Int, actionResults: MutableList<ActionResult>) : Boolean{

        // need for RPC as well
        searchTimeController.waitForRateLimiter()

        val actionResult = RPCCallResult(action.getLocalId())
        actionResults.add(actionResult)
        val dto = getActionDto(action, index)
        val externalActions = if (action.parent is EnterpriseActionGroup<*>){
            (action.parent as EnterpriseActionGroup<*>)
                .groupsView()!!.getAllInGroup(GroupsOfChildren.EXTERNAL_SERVICES)
        }else null

        val rpc = rpcHandler.transformActionDto(action, index, externalActions)
        dto.rpcCall = rpc

        val response =  rc.executeNewRPCActionAndGetResponse(dto)


        if (response != null){
            if (config.enablePureRPCTestGeneration){
                if (response.testScript == null)
                    log.warn("empty test script")
                else{
                    actionResult.setTestScript(response.testScript)
                    actionResult.setResponseVariableName(rpc.responseVariable)
                }
            }

            if (response.error500Msg != null){
                actionResult.setFailedCall(response.error500Msg)
            }else{
                // check exception
                if (response.exceptionInfoDto != null){
                    actionResult.setRPCException(response.exceptionInfoDto)
                    if (response.exceptionInfoDto.type == RPCExceptionType.CUSTOMIZED_EXCEPTION){
                        if (response.exceptionInfoDto.exceptionDto!=null){
                            actionResult.setCustomizedExceptionBody(rpcHandler.getJsonStringFromDto(response.exceptionInfoDto.exceptionDto))
                        } else
                            log.warn("ERROR: missing customized exception dto")
                    }
                } else{
                    actionResult.setSuccess()
                    if (response.customizedCallResultCode != null){
                        actionResult.setCustomizedBusinessLogicCode(response.customizedCallResultCode)
                    }

                    // check response
                    if (response.rpcResponse !=null){
                        Lazy.assert { action.responseTemplate != null }
                        val responseParam = action.responseTemplate!!.copy() as RPCParam
                        rpcHandler.setGeneBasedOnParamDto(responseParam.gene, response.rpcResponse)
                        action.response = responseParam

                        //extract response type
                        actionResult.setHandledResponse(false)
                        val valueGene = ParamUtil.getValueGene(responseParam.gene)
                        if (valueGene is CollectionGene){
                            actionResult.setHandledCollectionResponse(valueGene.isEmpty())
                        }

                        if (config.enableRPCAssertionWithInstance){
                            if (response.assertionScript == null){
                                log.warn("empty test assertions")
                            }else{
                                actionResult.setAssertionScript(response.assertionScript)
                            }
                        }
                    } else {
                        actionResult.setHandledResponse(true)
                    }

                }
            }

            // expand
            if (externalActions != null && response.expandInfo != null){
                rpcHandler.expandSchema(action, response.expandInfo);
                rpcHandler.expandRPCAction(externalActions)
            }
        }else{
            actionResult.setFailedCall()
        }

        actionResult.stopping = response == null

        return response != null
    }

    private fun handleResponseTargets(fv: FitnessValue,
                                      actions: List<RPCCallAction>,
                                      actionResults: List<RPCCallResult>,
                                      additionalInfoList: List<AdditionalInfoDto>
    ){
        actionResults.indices.forEach { i->
            val last = additionalInfoList[i].lastExecutedStatement?:RPC_DEFAULT_FAULT_CODE
            actionResults[i].getInvocationCode()
                ?:throw IllegalStateException("INVOCATION CODE is not assigned on the RPC call result")

            handleAdditionalTargetsDescription(fv, actionResults[i], actions[i].getName(), i, last)

        }
    }


    private fun handleAdditionalTargetsDescription(fv: FitnessValue,
                                                   callResult: RPCCallResult,
                                                   name: String,
                                                   indexOfAction : Int,
                                                   locationPotentialBug: String){

        val category = RPCCallResultCategory.valueOf(callResult.getInvocationCode()!!)

        val okId = idMapper.handleLocalTarget(idMapper.getHandledRPC(name))
        val failId = idMapper.handleLocalTarget(idMapper.getFaultDescriptiveIdForRPCDeclaredException(name))

        when(category){
            RPCCallResultCategory.HANDLED->{
                fv.updateTarget(okId, 1.0, indexOfAction)
                fv.updateTarget(failId, 0.5, indexOfAction)

                if (config.enableRPCCustomizedResponseTargets)
                    handleBusinessLogicTarget(fv, callResult, name, indexOfAction, locationPotentialBug)
                if (config.enableRPCExtraResponseTargets)
                    handleHandledResponse(fv, callResult, name, indexOfAction)
            }
            // low reward for protocol error
            RPCCallResultCategory.PROTOCOL_ERROR->{
                fv.updateTarget(okId, 0.1, indexOfAction)
                fv.updateTarget(failId, 0.1, indexOfAction)
            }
            RPCCallResultCategory.OTHERWISE_EXCEPTION,
            RPCCallResultCategory.CUSTOM_EXCEPTION,
            RPCCallResultCategory.UNEXPECTED_EXCEPTION ->{
                fv.updateTarget(okId, 0.5, indexOfAction)
                fv.updateTarget(failId, 1.0, indexOfAction)

                // exception type + last statement + endpoint name
                val postfix = "${callResult.getExceptionTypeName()} $locationPotentialBug $name"
                val descriptiveId = if (category == RPCCallResultCategory.UNEXPECTED_EXCEPTION){
                    idMapper.getFaultDescriptiveIdForUnexpectedException(postfix)
                }else
                    idMapper.getFaultDescriptiveIdForRPCDeclaredException(postfix)

                val exceptionId = idMapper.handleLocalTarget(descriptiveId)
                fv.updateTarget(exceptionId, 1.0, indexOfAction)

                callResult.setLastStatementForInternalError(locationPotentialBug)

            }

            RPCCallResultCategory.INTERNAL_ERROR->{
                fv.updateTarget(okId, 0.5, indexOfAction)
                fv.updateTarget(failId, 1.0, indexOfAction)

                val postfix = "$locationPotentialBug $name"
                val descriptiveId = idMapper.getFaultDescriptiveIdForInternalError(postfix)

                val bugId = idMapper.handleLocalTarget(descriptiveId)
                fv.updateTarget(bugId, 1.0, indexOfAction)

                callResult.setLastStatementForInternalError(locationPotentialBug)
            }
            RPCCallResultCategory.FAILED, RPCCallResultCategory.TRANSPORT_ERROR ->{
                // no reward for failed and transport error
            }
        }
    }

    private fun handleHandledResponse(fv: FitnessValue,
                               callResult: RPCCallResult,
                               name: String,
                               indexOfAction : Int){
        if (!callResult.hasResponse()) return;

        val resNull = idMapper.handleLocalTarget("RESPONSE_NULL:$name")
        val resNotNull = idMapper.handleLocalTarget("RESPONSE_NOTNULL:$name")

        if (callResult.isNotNullHandledResponse()){
            fv.updateTarget(resNull, 0.5, indexOfAction)
            fv.updateTarget(resNotNull, 1.0, indexOfAction)

            if (callResult.hasCollectionResponse()){
                val resEmpty = idMapper.handleLocalTarget("RESPONSE_COLLECTION_EMPTY:$name")
                val resNotEmpty = idMapper.handleLocalTarget("RESPONSE_COLLECTION_NOTEMPTY:$name")

                if (callResult.isNotEmptyHandledResponse()){
                    fv.updateTarget(resEmpty, 0.5, indexOfAction)
                    fv.updateTarget(resNotEmpty, 1.0, indexOfAction)
                } else{
                    fv.updateTarget(resEmpty, 1.0, indexOfAction)
                    fv.updateTarget(resNotEmpty, 0.5, indexOfAction)
                }
            }

        } else{
            fv.updateTarget(resNull, 1.0, indexOfAction)
            fv.updateTarget(resNotNull, 0.5, indexOfAction)
        }
    }

    private fun handleBusinessLogicTarget(fv: FitnessValue,
                                          callResult: RPCCallResult,
                                          name: String,
                                          indexOfAction : Int,
                                          locationPotentialBug: String){

        val okId = idMapper.handleLocalTarget(idMapper.getHandledRPCAndSuccess(name))
        val failId = idMapper.handleLocalTarget(idMapper.getHandledRPCButError(name))

        when{
            callResult.isSuccessfulBusinessLogicCode() ->{
                fv.updateTarget(okId, 1.0, indexOfAction)
                fv.updateTarget(failId, 0.5, indexOfAction)
            }

            callResult.isOtherwiseCustomizedServiceError() ->{
                fv.updateTarget(okId, 0.1, indexOfAction)
                fv.updateTarget(failId, 0.1, indexOfAction)
            }

            callResult.isCustomizedServiceError() -> {
                fv.updateTarget(okId, 0.5, indexOfAction)
                fv.updateTarget(failId, 1.0, indexOfAction)

                val postfix = "$locationPotentialBug $name"
                val descriptiveId = idMapper.getRPCServiceError(postfix)

                val bugId = idMapper.handleLocalTarget(descriptiveId)
                fv.updateTarget(bugId, 1.0, indexOfAction)

                callResult.setLastStatementForInternalError(locationPotentialBug)
            }
        }
    }
}
