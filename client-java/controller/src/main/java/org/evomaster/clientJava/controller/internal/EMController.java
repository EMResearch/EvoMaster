package org.evomaster.clientJava.controller.internal;

import org.evomaster.clientJava.clientUtil.SimpleLogger;
import org.evomaster.clientJava.controller.db.SqlScriptRunner;
import org.evomaster.clientJava.controller.problem.ProblemInfo;
import org.evomaster.clientJava.controller.problem.RestProblem;
import org.evomaster.clientJava.controllerApi.ControllerConstants;
import org.evomaster.clientJava.controllerApi.Formats;
import org.evomaster.clientJava.controllerApi.dto.*;
import org.evomaster.clientJava.controllerApi.dto.database.operations.DatabaseCommandDto;
import org.evomaster.clientJava.controllerApi.dto.problem.RestProblemDto;
import org.evomaster.clientJava.instrumentation.AdditionalInfo;
import org.evomaster.clientJava.instrumentation.TargetInfo;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Note: usually a RESTful webservice would be stateless.
 * Here, however, we have state. Reason is that we need it,
 * and the only client is the EvoMaster process. Furthermore,
 * the code of the controller should be as simple as possible,
 * as we might need to re-implement it in different languages.
 */
@Path("")
@Produces(Formats.JSON_V1)
public class EMController {

    private final SutController sutController;
    private String baseUrlOfSUT;

    public EMController(SutController sutController) {
        this.sutController = Objects.requireNonNull(sutController);
    }


    @Path(ControllerConstants.INFO_SUT_PATH)
    @GET
    public Response getSutInfo() {

        SutInfoDto dto = new SutInfoDto();
        dto.isSutRunning = sutController.isSutRunning();
        dto.baseUrlOfSUT = baseUrlOfSUT;
        dto.infoForAuthentication = sutController.getInfoForAuthentication();
        dto.sqlSchemaDto = sutController.getSqlDatabaseSchema();
        dto.defaultOutputFormat = sutController.getPreferredOutputFormat();

        ProblemInfo info = sutController.getProblemInfo();
        if(info == null){
            String msg = "Undefined problem type in the EM Controller";
            SimpleLogger.error(msg);
            return Response.status(500).entity(WrappedResponseDto.withError(msg)).build();

        } else if (info instanceof RestProblem){
            RestProblem rp = (RestProblem) info;
            dto.restProblem = new RestProblemDto();
            dto.restProblem.swaggerJsonUrl = rp.getSwaggerJsonUrl();
            dto.restProblem.endpointsToSkip = rp.getEndpointsToSkip();

        } else {
            String msg = "Unrecognized problem type: " + info.getClass().getName();
            SimpleLogger.error(msg);
            return Response.status(500).entity(WrappedResponseDto.withError(msg)).build();
        }

        return Response.status(200).entity(WrappedResponseDto.withData(dto)).build();
    }

    @Path(ControllerConstants.CONTROLLER_INFO)
    @GET
    public Response getControllerInfoDto() {

        ControllerInfoDto dto = new ControllerInfoDto();
        dto.fullName = sutController.getClass().getName();
        dto.isInstrumentationOn = sutController.isInstrumentationActivated();

        return Response.status(200).entity(WrappedResponseDto.withData(dto)).build();
    }

    @Path(ControllerConstants.NEW_SEARCH)
    @POST
    public Response newSearch() {
        sutController.newSearch();

        return Response.status(201).entity(WrappedResponseDto.withNoData()).build();
    }


    @Path(ControllerConstants.RUN_SUT_PATH)
    @PUT
    @Consumes(Formats.JSON_V1)
    public Response runSut(SutRunDto dto) {

        try {
            if (dto.run == null) {
                String msg = "Invalid JSON: 'run' field is required";
                SimpleLogger.warn(msg);
                return Response.status(400).entity(WrappedResponseDto.withError(msg)).build();
            }

            boolean newlyStarted = false;

            synchronized (this) {
                if (dto.run) {
                    if (!sutController.isSutRunning()) {
                        baseUrlOfSUT = sutController.startSut();
                        if (baseUrlOfSUT == null) {
                            //there has been an internal failure in starting the SUT
                            String msg = "Internal failure: cannot start SUT based on given configuration";
                            SimpleLogger.warn(msg);
                            return Response.status(500).entity(WrappedResponseDto.withError(msg)).build();
                        }
                        sutController.initSqlHandler();
                        sutController.newTest();
                        newlyStarted = true;
                    } else {
                        //TODO as starting should be blocking, need to check
                        //if initialized, and wait if not
                    }
                } else {
                    if (sutController.isSutRunning()) {
                        sutController.stopSut();
                        baseUrlOfSUT = null;
                    }
                }

                if (dto.resetState != null && dto.resetState) {
                    if (!dto.run) {
                        String msg = "Invalid JSON: cannot reset state and stop service at same time";
                        SimpleLogger.warn(msg);
                        return Response.status(400).entity(WrappedResponseDto.withError(msg)).build();
                    }

                    if (!newlyStarted) { //no point resetting if fresh start
                        sutController.resetStateOfSUT();
                        sutController.newTest();
                    }
                }
            }
        } catch (RuntimeException e) {
            /*
                FIXME: ideally, would not need to do a try/catch on each single endpoint,
                as could configure Jetty/Jackson to log all errors.
                But even after spending hours googling it, haven't managed to configure it
             */

            String msg = e.getMessage();
            SimpleLogger.error(msg );
            return Response.status(500).entity(WrappedResponseDto.withError(msg)).build();
        }

        return Response.status(204).entity(WrappedResponseDto.withNoData()).build();
    }


    @Path(ControllerConstants.TEST_RESULTS)
    @GET
    public Response getTestResults(
            @QueryParam("ids")
            @DefaultValue("")
                    String idList) {

        TestResultsDto dto = new TestResultsDto();

        Set<Integer> ids;

        try {
            ids = Arrays.stream(idList.split(","))
                    .filter(s -> !s.trim().isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());
        } catch (NumberFormatException e) {
            String msg = "Invalid parameter 'ids': " + e.getMessage();
            SimpleLogger.warn(msg);
            return Response.status(400).entity(WrappedResponseDto.withError(msg)).build();
        }

        List<TargetInfo> list = sutController.getTargetInfos(ids);
        if (list == null) {
            String msg = "Failed to collect target information for " + ids.size() + " ids";
            SimpleLogger.error(msg);
            return Response.status(500).entity(WrappedResponseDto.withError(msg)).build();
        }

        list.forEach(t -> {
            TargetInfoDto info = new TargetInfoDto();
            info.id = t.mappedId;
            info.value = t.value;
            info.descriptiveId = t.descriptiveId;
            info.actionIndex = t.actionIndex;

            dto.targets.add(info);
        });

        sutController.getAdditionalInfoList().forEach(a -> {
            AdditionalInfoDto info = new AdditionalInfoDto();
            info.queryParameters = new HashSet<>(a.getQueryParametersView());
            info.headers = new HashSet<>(a.getHeadersView());

            dto.additionalInfoList.add(info);
        });

        return Response.status(200).entity(WrappedResponseDto.withData(dto)).build();
    }


    @Path(ControllerConstants.EXTRA_HEURISTICS)
    @GET
    public Response getExtra() {

        ExtraHeuristicDto dto = sutController.getExtraHeuristics();

        return Response.status(200).entity(WrappedResponseDto.withData(dto)).build();
    }

    @Path(ControllerConstants.EXTRA_HEURISTICS)
    @DELETE
    public Response deleteExtra() {

        sutController.resetExtraHeuristics();

        return Response.status(204).entity(WrappedResponseDto.withNoData()).build();
    }

    @Path(ControllerConstants.NEW_ACTION)
    @Consumes(MediaType.APPLICATION_JSON)
    @PUT
    public Response newAction(int index) {

        sutController.newAction(index);

        return Response.status(204).entity(WrappedResponseDto.withNoData()).build();
    }


    @Path(ControllerConstants.DATABASE_COMMAND)
    @Consumes(Formats.JSON_V1)
    @POST
    public Response executeDatabaseCommand(DatabaseCommandDto dto) {

        Connection connection = sutController.getConnection();
        if (connection == null) {
            String msg = "No active database connection";
            SimpleLogger.warn(msg);
            return Response.status(400).entity(WrappedResponseDto.withError(msg)).build();
        }

        if (dto.command == null && (dto.insertions == null || dto.insertions.isEmpty())) {
            String msg = "No input command";
            SimpleLogger.warn(msg);
            return Response.status(400).entity(WrappedResponseDto.withError(msg)).build();
        }

        if (dto.command != null && dto.insertions != null && !dto.insertions.isEmpty()) {
            String msg = "Only 1 command can be specified";
            SimpleLogger.warn(msg);
            return Response.status(400).entity(WrappedResponseDto.withError(msg)).build();
        }


        try {
            if (dto.command != null) {
                SqlScriptRunner.execCommand(connection, dto.command);
            } else {
                SqlScriptRunner.execInsert(connection, dto.insertions);
            }
        } catch (Exception e) {
            String msg = "Failed to execute database command: " + e.getMessage();
            SimpleLogger.warn(msg);
            return Response.status(400).entity(WrappedResponseDto.withError(msg)).build();
        }

        return Response.status(204).entity(WrappedResponseDto.withNoData()).build();
    }
}
