package org.evomaster.core.problem.rest.individual

import com.google.inject.*
import com.netflix.governator.guice.LifecycleInjector
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.evomaster.client.java.controller.api.dto.*
import org.evomaster.client.java.controller.api.dto.database.operations.*
import org.evomaster.client.java.controller.api.dto.problem.RestProblemDto
import org.evomaster.client.java.sql.SqlScriptRunner
import org.evomaster.client.java.sql.SchemaExtractor
import org.evomaster.core.BaseModule
import org.evomaster.core.EMConfig
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.sql.SqlInsertBuilder
import org.evomaster.core.sql.schema.ColumnDataType
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.problem.rest.resource.RestResourceCalls
import org.evomaster.core.problem.rest.service.AbstractRestFitness
import org.evomaster.core.problem.rest.service.AbstractRestSampler
import org.evomaster.core.remote.service.RemoteController
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.impact.impactinfocollection.ImpactsOfIndividual
import org.evomaster.core.search.service.Archive
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.SearchTimeController
import org.evomaster.core.search.service.mutator.StandardMutator
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.testcontainers.containers.MockServerContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.util.stream.Stream
import kotlin.math.max


abstract class RestIndividualTestBase {

    protected lateinit var config : EMConfig
    protected lateinit var archive : Archive<RestIndividual>
    protected lateinit var searchTimeController: SearchTimeController

    companion object {
        private lateinit var connection: Connection

        /**
         * randomness used in tests
         */
        val randomness : Randomness = Randomness()
        var sqlInsertBuilder : SqlInsertBuilder? = null

        /**
         * this is used to configure whether to employ fake
         * database heuristics results, such as setup
         * fake extraHeuristic in test execution results,
         * provide fake existing data
         *
         * this setting will be reset to true after each test
         */
        var employFakeDbHeuristicResult = true


        private val MOCKSERVER_IMAGE = DockerImageName.parse(
            "jamesdbloom/mockserver:mockserver-5.13.2"
        )

        /**
         *
         * need to employ fitness function for checking mutated individual,
         * then mock the sut with
         *
         * https://www.testcontainers.org/modules/mockserver/
         */
        private val mockServer: MockServerContainer = MockServerContainer(MOCKSERVER_IMAGE)
        lateinit var sutAddress: String

        @BeforeAll
        @JvmStatic
        fun initClass() {
            connection = DriverManager.getConnection("jdbc:h2:mem:db_test", "sa", "")
            mockServer.start()

            /*
                need to set a fixed status code,
                otherwise 999 status code is returned sometime
                that would lead to a crash in function `setStatusCode` of [HttpWsCallResult]
                ie, "Invalid HTTP code $code"

                https://www.mock-server.com/mock_server/creating_expectations.html
             */
            MockServerClient(mockServer.host, mockServer.serverPort)
                .`when`(
                    request().withPath("/api.*")
                )
                .respond(
                    response()
                        .withHeader("Content-Type", "plain/text")
                        .withStatusCode(500)
                        .withBody("Mocked-response")
                )
            sutAddress = "http://${mockServer.host}:${mockServer.serverPort}"
        }

        private val budget = arrayOf(10, 100, 1000)

        @JvmStatic
        fun getBudgetAndNumOfResourceForSampler(): Stream<Arguments> {
            val range = (1..30)
            return range.map {r-> budget.map { Arguments.of(it, r) } }.flatten().stream()
        }

        @JvmStatic
        fun getBudgetAndNumOfResourceForMutator(): Stream<Arguments> {
            /*
                employ less resources for mutator
                since the current failwhere are sampled at random
                it would lead to at most 3 * number of resources insertions
                with parameterizedtest,
                more than 60 insertions would lead to java.lang.OutOfMemoryError: Java heap space

                https://github.com/junit-team/junit5/issues/1445

                might need to upgrade the junit 5 version
             */
            val range = (1..10)
            return range.map {r-> budget.map { Arguments.of(it, r) } }.flatten().stream()
        }

        @JvmStatic
        @AfterAll
        fun clean(): Unit {
            mockServer.close()
        }
    }

    @BeforeEach
    fun initTest() {
        //custom H2 command
        SqlScriptRunner.execCommand(connection, "DROP ALL OBJECTS;")
        randomness.updateSeed(42)
    }

    fun getConnection() : Connection = connection

    abstract fun initService(injector: Injector)

    open fun config() : Array<String> = emptyArray()

    abstract fun getProblemModule() : Module

    abstract fun getSampler() : AbstractRestSampler
    abstract fun getMutator() : StandardMutator<RestIndividual>
    abstract fun getFitnessFunction() : AbstractRestFitness<RestIndividual>

    @ParameterizedTest
    @MethodSource("getBudgetAndNumOfResourceForSampler")
    fun testSampledIndividual(iteration: Int, numResource: Int){
        initResourceNode(numResource, 5)
        config.maxActionEvaluations = iteration

        (0 until iteration).forEach { i ->
            val ind = getSampler().sample()

            assertEquals(0, ind.seeInitializingActions().size)
            if (ind.seeSqlDbActions().isNotEmpty()){
                // all db actions should be before rest actions
                ind.getResourceCalls().forEach { r->
                    val dbIndexes = r.getIndexedChildren(SqlAction::class.java).keys
                    val restIndexes = r.getIndexedChildren(RestCallAction::class.java).keys
                    assertTrue(restIndexes.all {
                        it > (dbIndexes.maxOrNull() ?: -1)
                    })
                }
            }
            extraSampledIndividualCheck(i, ind)
        }
    }

    open fun extraSampledIndividualCheck(index: Int, individual: RestIndividual){}



    @ParameterizedTest
    @MethodSource("getBudgetAndNumOfResourceForMutator")
    fun testMutatedIndividual(iteration: Int, numResource: Int){
        initResourceNode(numResource, 5)
        config.maxActionEvaluations = iteration

        val ind = getSampler().sample()
        var eval = getFitnessFunction().calculateCoverage(ind, modifiedSpec = null)
        assertNotNull(eval)

        var evaluated = 0
        do {
            val impact = eval?.impactInfo?.copy()
            val original = eval!!.copy()
            val mutated = getMutator().mutateAndSave(1, eval, archive)
            evaluated ++
            checkActionIndex(mutated.individual)
            extraMutatedIndividualCheck(evaluated, impact, original, mutated)
            eval = mutated
        }while (searchTimeController.shouldContinueSearch())

    }

    private fun checkActionIndex(mutated: RestIndividual){
        assertEquals(0, mutated.getIndexedChildren(RestCallAction::class.java).size)

        val indexedDb = mutated.getIndexedChildren(SqlAction::class.java)
        val indexedRest = mutated.getIndexedChildren(RestResourceCalls::class.java)

        val dbBeforeRest = indexedRest.keys.all { it > (indexedDb.keys.maxOrNull() ?: -1) }
        assertTrue(dbBeforeRest)

        val existing = indexedDb.filterValues { it.representExistingData }
        val insert = indexedDb.filterValues { !it.representExistingData }

        val existingBeforeInsert = insert.keys.all { it > (existing.keys.maxOrNull() ?: -1) }
        assertTrue(existingBeforeInsert)
    }

    open fun extraMutatedIndividualCheck(
            evaluated: Int,
            copyOfImpact: ImpactsOfIndividual?,
            original: EvaluatedIndividual<RestIndividual>,
            mutated: EvaluatedIndividual<RestIndividual>){}


    fun registerTable(tableName: String, columns: List<Pair<String, Boolean>>, columnTypes: List<ColumnDataType>){
        SqlScriptRunner.execCommand(
            connection,
            "CREATE TABLE $tableName(ID INT PRIMARY KEY, ${columns.mapIndexed { index, pair -> "${pair.first} ${columnTypes[index].name} ${if (pair.second) "not null" else ""}" }.joinToString(",")});")

    }

    fun initResourceNode(numResource: Int, maxPropertySize: Int){
        // sample actioncluster and dbactions
        val spec = sampleResourceSpec(numResource, maxPropertySize)
        spec.forEach {
            registerTable(it.resourceName, it.properties, it.columnTypes)
        }
        val openAPI = openApiSchema(spec)

        val schema = SchemaExtractor.extract(getConnection())

        val defaultConfigs : Array<String> = listOf(
            "--useTimeInFeedbackSampling=false",
            "--seed=42",
            "--stoppingCriterion=FITNESS_EVALUATIONS"
        ).plus(config()).toTypedArray()

        val modules = listOf(BaseModule(defaultConfigs)).plus(getProblemModule()).plus(FakeModule(
            SutInfoDto().apply {
                restProblem = RestProblemDto().apply {
                    openApiSchema = openAPI
                    defaultOutputFormat = SutInfoDto.OutputFormat.KOTLIN_JUNIT_4
                    baseUrlOfSUT = sutAddress
                }
                sqlSchemaDto = schema
            },
            ControllerInfoDto()
        ))

        val injector: Injector = LifecycleInjector.builder()
            .withModules(modules)
            .build().createInjector()

       // randomness = injector.getInstance(Randomness::class.java)
        config = injector.getInstance(EMConfig::class.java)
        archive = injector.getInstance(Key.get(
            object : TypeLiteral<Archive<RestIndividual>>() {}))
        searchTimeController = injector.getInstance(SearchTimeController::class.java)

        initService(injector)

        sqlInsertBuilder = getSampler().sqlInsertBuilder

    }

    data class ResourceSpec(
        val resourceName: String,
        val properties: List<Pair<String, Boolean>>,
        val propertyTypes: List<String>,
        val propertyFormats: List<String?>,
        val columnTypes: List<ColumnDataType>)

    private fun generatePropertyName(size: Int) = "${randomness.nextLetter()}${(0 until size).map { randomness.nextLetter() }.joinToString("")}"

    private fun generateResourceName() = "${randomness.nextLetter().uppercaseChar()}${generatePropertyName(5)}"

    private fun sampleResourceSpec(numResource: Int, maxPropertySize: Int) : List<ResourceSpec>{
        return (0 until numResource).map {
            val size = randomness.nextInt(1, max(2, maxPropertySize))
            val columnTypes = (0 until size).map { randomness.choose(typeCluster()) }
            ResourceSpec(
                resourceName = generateResourceName(),
                properties = (0 until size).map { generatePropertyName(4) to randomness.nextBoolean()},
                columnTypes = columnTypes,
                propertyTypes = columnTypes.map {
                    when(it){
                        ColumnDataType.INT -> "integer"
                        ColumnDataType.VARCHAR -> "string"
                        ColumnDataType.DATE -> "string"
                        else -> throw IllegalArgumentException("not support $it in current test")
                    }
                },
                propertyFormats = columnTypes.map {
                    when(it){
                        ColumnDataType.INT -> "int32"
                        ColumnDataType.DATE -> "date"
                        else -> null
                    }
                }
            )
        }
    }

    /*
        now only involve these types in tests, might expand it when needed
        might need to add ArrayGene and MapGene
     */
    private fun typeCluster() = listOf(ColumnDataType.INT, ColumnDataType.VARCHAR, ColumnDataType.DATE)


    private fun openApiSchema(spec: List<ResourceSpec>) : String {

        val swagger = OpenAPI()
            .info(
                Info()
                .version("1.0.0")
                .title("Test")
                .apply {
                    contact = Contact()
                        .name("Test")
                        .email("foo@test")
                        .url("http://test")
                })

        val errorSchema = Schema<Any>().apply {
            type = "object"
            setProperties(mapOf("errorMessage" to StringSchema()))
        }

        swagger.schema("Error", errorSchema)

        val errorResponse = ApiResponse()
            .description("error response")
            .content(
                Content()
                    .addMediaType(
                        "application/json", MediaType()
                            .schema(Schema<Any?>().`$ref`("Error"))
                    )
            )

        val okResponse = ApiResponse()
            .description("ok response")
            .content(
                Content()
                    .addMediaType(
                        "*/*", MediaType()
                            .schema(StringSchema())
                    )
            )

        val schemas = spec.associate {
            it.resourceName to Schema<Any>().apply {
                type = "object"
                val properties = it.properties.mapIndexed { index, p ->
                    p.first to Schema<Any>().apply {
                        type = it.propertyTypes[index]
                        format = it.propertyFormats[index]
                    }
                }.toMap()
                setProperties(properties)
            }
        }


        val paths = Paths()

        schemas.forEach {
            swagger.schema(it.key, it.value)

            val bodyRequestItem = PathItem().apply {
                post(
                    Operation()
                    .summary("adds a ${it.key}")
                    .operationId("add ${it.key}")
                    .responses(
                        ApiResponses()
                            .addApiResponse("default", errorResponse)
                    )
                    .requestBody(
                        RequestBody()
                            .description("the ${it.key} to add")
                            .content(
                                Content().addMediaType(
                                    "application/json", MediaType()
                                        .schema(Schema<Any?>().`$ref`(it.key))
                                )
                            )
                    ))
                /*
                    test fails due to
                    javax.ws.rs.ProcessingException: java.net.ProtocolException: Invalid HTTP method: PATCH
                    Caused by: java.net.ProtocolException: Invalid HTTP method: PATCH

                    remove patch method for the moment
                 */
//                patch(
//                    Operation()
//                    .summary("update a ${it.key}")
//                    .operationId("update ${it.key}")
//                    .responses(
//                        ApiResponses()
//                            .addApiResponse("default", errorResponse)
//                    )
//                    .requestBody(
//                        RequestBody()
//                            .description("the ${it.key} to update")
//                            .content(
//                                Content().addMediaType(
//                                    "application/json", MediaType()
//                                        .schema(Schema<Any?>().`$ref`(it.key))
//                                )
//                            )
//                    ))
                put(
                    Operation()
                    .summary("update a ${it.key}")
                    .operationId("update ${it.key}")
                    .responses(
                        ApiResponses()
                            .addApiResponse("default", errorResponse)
                    )
                    .requestBody(
                        RequestBody()
                            .description("the ${it.key} to update")
                            .content(
                                Content().addMediaType(
                                    "application/json", MediaType()
                                        .schema(Schema<Any?>().`$ref`(it.key))
                                )
                            )
                    ))

            }

            paths.addPathItem("/api/${it.key}",bodyRequestItem)

            val firstParameter = it.value.properties.toList().first()

            val pathRequestItem = PathItem().apply {
                get(
                    Operation()
                        .summary("get a ${it.key}")
                        .operationId("get ${it.key}")
                        .responses(
                            ApiResponses()
                                .addApiResponse("default", errorResponse)
                        )
                        .responses(
                            ApiResponses()
                                .addApiResponse("200", okResponse)
                                .addApiResponse("default", errorResponse)
                        )
                        .apply {
                            addParametersItem(
                                Parameter()
                                    .`in`("path")
                                    .name(firstParameter.first)
                                    .description("${it.key} to fetch")
                                    .schema(firstParameter.second)
                            )
                        }
                )

                delete(
                    Operation()
                        .summary("delete a ${it.key}")
                        .operationId("delete ${it.key}")
                        .responses(
                            ApiResponses()
                                .addApiResponse("default", errorResponse)
                        )
                        .responses(
                            ApiResponses()
                                .addApiResponse("200", okResponse)
                                .addApiResponse("default", errorResponse)
                        )
                        .apply {
                            addParametersItem(
                                Parameter()
                                    .`in`("path")
                                    .name(firstParameter.first)
                                    .description("${it.key} to delete")
                                    .schema(firstParameter.second)
                            )
                        }
                )
            }

            paths.addPathItem("/api/${it.key}/{${firstParameter.first}}",pathRequestItem)

        }

        swagger.paths(paths)
        return Json.mapper().writeValueAsString(swagger)
    }

    private class FakeModule(val sutInfoDto: SutInfoDto?,
                             val controllerInfoDto: ControllerInfoDto?) : AbstractModule() {
        @Provides
        @Singleton
        fun getRemoteController(): RemoteController {
            return FakeRemoteController(sutInfoDto, controllerInfoDto)
        }
    }

    class FakeRemoteController(val sutInfoDto: SutInfoDto?,
                               val controllerInfoDto: ControllerInfoDto?) : RemoteController {

        private var executedActionCounter = 0
        private var targetIdCounter = 0

        private fun resetExecutedActionCounter(){
            executedActionCounter = 0
        }
        private fun executeAction(){
            executedActionCounter++
        }

        private fun newEvaluation(){
            targetIdCounter++
        }

        override fun getSutInfo(): SutInfoDto? {
            return sutInfoDto
        }

        override fun getControllerInfo(): ControllerInfoDto? {
            return controllerInfoDto
        }

        override fun startSUT(): Boolean {
            return true
        }

        override fun stopSUT(): Boolean {
            return true
        }

        override fun resetSUT(): Boolean {
            return true
        }

        override fun checkConnection() {
        }

        override fun startANewSearch(): Boolean {
            resetExecutedActionCounter()
            return true
        }

        override fun getTestResults(ids: Set<Int>, ignoreKillSwitch: Boolean, allCovered: Boolean): TestResultsDto? {
            assertNotNull(sqlInsertBuilder)
            newEvaluation()
            val result = TestResultsDto().apply {
                targets = listOf(TargetInfoDto().apply {
                    id = targetIdCounter
                    value = 1.0
                    actionIndex = randomness.nextInt(executedActionCounter)
                })
                additionalInfoList = (0 until executedActionCounter).map { AdditionalInfoDto() }
                extraHeuristics = (0 until executedActionCounter).map {
                    ExtraHeuristicsDto().apply {
                        if (employFakeDbHeuristicResult && randomness.nextBoolean()){
                            databaseExecutionDto = org.evomaster.client.java.controller.api.dto.database.execution.ExecutionDto()
                                .apply {
                                val table = randomness.choose( sqlInsertBuilder!!.getTableNames())
                                val failed = randomness.choose(sqlInsertBuilder!!.getTable(table,true).columns.map { it.name })
                                failedWhere = mapOf(table to setOf(failed))
                            }
                        }
                    }
                }
            }

            resetExecutedActionCounter()
            return result
        }

        override fun executeNewRPCActionAndGetResponse(actionDto: ActionDto): ActionResponseDto? {
            return null
        }

        override fun registerNewAction(actionDto: ActionDto): Boolean {
            executeAction()
            return true
        }

        override fun address(): String {
            return "localhost:40100"
        }

        override fun close() {

        }

        override fun postSearchAction(postSearchActionDto: PostSearchActionDto): Boolean {
            return true
        }

        override fun executeDatabaseCommand(dto: DatabaseCommandDto): Boolean {
            return true
        }

        override fun executeDatabaseCommandAndGetQueryResults(dto: DatabaseCommandDto): QueryResultDto? {
            if (!employFakeDbHeuristicResult) return null
            /*
                in order to check dbaction representing existing data in rest individual,
                need to simulate existing data here,
                for each table, return fixed 1 pk
             */
            val columnSize = max(1, dto.command.count { it == ',' } + 1)
            val result = QueryResultDto().apply {
                rows = listOf(DataRowDto().apply {
                    columnData = (0 until columnSize).map { "1" }
                })
            }
            return result
        }

        override fun executeDatabaseInsertionsAndGetIdMapping(dto: DatabaseCommandDto): InsertionResultsDto? {
            return null
        }

        override fun executeMongoDatabaseInsertions(dto: MongoDatabaseCommandDto): MongoInsertionResultsDto? {
            return null
        }

    }

    @AfterEach
    fun defaultSetting(){
        employFakeDbHeuristicResult = true
    }

}