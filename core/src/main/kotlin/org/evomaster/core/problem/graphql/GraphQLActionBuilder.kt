package org.evomaster.core.problem.graphql

import com.google.gson.Gson
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.problem.graphql.param.GQInputParam
import org.evomaster.core.problem.graphql.param.GQReturnParam
import org.evomaster.core.problem.graphql.schema.*
import org.evomaster.core.problem.graphql.schema.__TypeKind.*
import org.evomaster.core.problem.rest.param.Param
import org.evomaster.core.search.Action
import org.evomaster.core.search.gene.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object GraphQLActionBuilder {

    private val log: Logger = LoggerFactory.getLogger(GraphQLActionBuilder::class.java)
    private val idGenerator = AtomicInteger()

    private val systemTypes = listOf("__Schema", "__Directive", "__DirectiveLocation", "__EnumValue",
            "__Field", "__InputValue", "__Type", "__TypeKind")


    private data class TempState(
            val tables: MutableList<Table> = mutableListOf(),
            val argsTables: MutableList<Table> = mutableListOf(),
            val tempArgsTables: MutableList<Table> = mutableListOf(),
            val tempEnumTables: MutableMap<String, MutableList<String>> = mutableMapOf()
    )

    /**
     * @param schema: the schema extracted from a GraphQL API, as a JSON string
     * @param actionCluster: for each mutation/query in the schema, populate this map with
     *                      new action templates.
     */
    fun addActionsFromSchema(schema: String, actionCluster: MutableMap<String, Action>) {

        val state = TempState()

        val gson = Gson()
        val schemaObj: SchemaObj = gson.fromJson(schema, SchemaObj::class.java)

        initTablesInfo(schemaObj, state)

        if (schemaObj.data.__schema.queryType != null && schemaObj.data.__schema.mutationType != null)
            for (element in state.tables) {
                /*
                In some schemas, "Root" and "QueryType" types define the entry point of the GraphQL query.
                 */
                if (element.tableType == "Mutation" || element.tableType == "Query" || element.tableType == "Root" || element.tableType == "QueryType") {
                    handleOperation(
                            state,
                            actionCluster,
                            element.tableField,
                            element.tableType,
                            element.tableFieldType,
                            element.kindOfTableFieldType.toString(),
                            element.kindOfTableField.toString(),
                            element.tableType.toString(),
                            element.isKindOfTableFieldTypeOptional,
                            element.isKindOfTableFieldOptional,
                            element.tableFieldWithArgs,
                            element.enumValues
                    )
                }
            }
        else if (schemaObj.data.__schema.queryType != null && schemaObj.data.__schema.mutationType == null)
            for (element in state.tables) {
                if (element.tableType == "Query" || element.tableType == "Root" || element.tableType == "QueryType") {
                    handleOperation(
                            state,
                            actionCluster,
                            element.tableField,
                            element.tableType,
                            element.tableFieldType,
                            element.kindOfTableFieldType.toString(),
                            element.kindOfTableField.toString(),
                            element.tableType.toString(),
                            element.isKindOfTableFieldTypeOptional,
                            element.isKindOfTableFieldOptional,
                            element.tableFieldWithArgs,
                            element.enumValues
                    )
                }
            }
        else if (schemaObj.data.__schema.queryType == null && schemaObj.data.__schema.mutationType != null)
            for (element in state.tables) {
                if (element.tableType == "Mutation") {
                    handleOperation(
                            state,
                            actionCluster,
                            element.tableField,
                            element.tableType,
                            element.tableFieldType,
                            element.kindOfTableFieldType.toString(),
                            element.kindOfTableField.toString(),
                            element.tableType.toString(),
                            element.isKindOfTableFieldTypeOptional,
                            element.isKindOfTableFieldOptional,
                            element.tableFieldWithArgs,
                            element.enumValues
                    )
                }
            }
        else if (schemaObj.data.__schema.queryType == null && schemaObj.data.__schema.mutationType == null)
            LoggingUtil.uniqueWarn(log, "No entrance for the schema")
    }

    private fun initTablesInfo(schemaObj: SchemaObj, state: TempState) {

        for (elementIntypes in schemaObj.data.__schema.types) {
            if (systemTypes.contains(elementIntypes.name)) {
                continue
            }

            for (elementInfields in elementIntypes?.fields.orEmpty()) {
                /**
                 * extracting tables
                 */
                val tableElement = Table()
                tableElement.tableField = elementInfields?.name

                if (elementInfields?.type?.kind == NON_NULL) {// non optional list or object or scalar

                    handleNonOptionalInTables(elementInfields, tableElement, elementIntypes, state)

                } else {
                    handleOptionalInTables(elementInfields, tableElement, elementIntypes, state)
                }

                /*
                 * extracting argsTables: 1/2
                 */
                if (elementInfields.args.isNotEmpty()) {
                    tableElement.tableFieldWithArgs = true
                    for (elementInArgs in elementInfields.args) {
                        val inputElement = Table()
                        inputElement.tableType = elementInfields?.name
                        if (elementInArgs?.type?.kind == NON_NULL) //non optional list or object or scalar or enum
                            handleNonOptionalInArgsTables(inputElement, elementInArgs, state)
                        else  //optional list or input object or scalar or enum
                            handleOptionalInArgsTables(inputElement, elementInArgs, state)

                    }
                }
            }
        }
        handleEnumInArgsTables(state, schemaObj)
        handleEnumInTables(state, schemaObj)
        /*
         *extracting tempArgsTables, an intermediate table for extracting argsTables
         */
        extractTempArgsTables(state, schemaObj)
        handleEnumInTempArgsTables(state, schemaObj)
        /*
         * merging argsTables with tempArgsTables: extracting argsTables: 2/2
         */
        state.argsTables.addAll(state.tempArgsTables)
    }

    /*
    This when an entry is optional in Tables
 */
    private fun handleOptionalInTables(elementInfields: __Field, tableElement: Table, elementInTypes: FullType, state: TempState) {

        val kind = elementInfields?.type?.kind
        val kind2 = elementInfields?.type?.ofType?.kind
        val kind3 = elementInfields?.type?.ofType?.ofType?.kind

        if (kind == LIST) {//optional list in the top
            tableElement.kindOfTableField = LIST
            tableElement.isKindOfTableFieldOptional = true
            if (kind2 == NON_NULL) {// non optional object or scalar or enum
                tableElement.isKindOfTableFieldTypeOptional = false
                if (kind3 == OBJECT || kind3 == SCALAR) {
                    tableElement.kindOfTableFieldType = kind3
                    tableElement.tableFieldType = elementInfields?.type?.ofType?.ofType?.name
                    tableElement.tableType = elementInTypes?.name
                    state.tables.add(tableElement)
                }

            } else {
                tableElement.isKindOfTableFieldTypeOptional = true
                if (kind2 == OBJECT || kind2 == SCALAR || kind2 == ENUM) {//optional object or scalar or enum
                    tableElement.kindOfTableFieldType = kind2
                    tableElement.tableFieldType = elementInfields?.type?.ofType?.name
                    tableElement.tableType = elementInTypes?.name
                    state.tables.add(tableElement)
                }
            }

        } else {
            tableElement.isKindOfTableFieldTypeOptional = true
            if (kind == OBJECT || kind == SCALAR || kind == ENUM) {// optional object or scalar or enum in the top
                tableElement.kindOfTableFieldType = kind
                tableElement.tableFieldType = elementInfields?.type?.name
                tableElement.tableType = elementInTypes?.name
                state.tables.add(tableElement)
            }
        }

    }

    /*
        This is to handle entries that are NOT optional, and must be there, ie, they cannot be null
     */
    private fun handleNonOptionalInTables(elementInfields: __Field, tableElement: Table, elementIntypes: FullType, state: TempState) {

        val kind = elementInfields?.type?.ofType?.kind
        val kind2 = elementInfields?.type?.ofType?.ofType?.kind
        val kind3 = elementInfields?.type?.ofType?.ofType?.ofType?.kind
        tableElement.isKindOfTableFieldOptional = false

        if (kind == LIST) {// non optional list
            tableElement.kindOfTableField = LIST

            if (kind2 == NON_NULL) {// non optional object or scalar or enum
                tableElement.isKindOfTableFieldTypeOptional = false
                tableElement.kindOfTableFieldType = kind3
                tableElement.tableFieldType = elementInfields?.type?.ofType?.ofType?.ofType?.name
                tableElement.tableType = elementIntypes?.name
                state.tables.add(tableElement)
            } else {//optional object or scalar or enum
                if (elementInfields?.type?.ofType?.ofType?.name == null) {
                    LoggingUtil.uniqueWarn(log, "Depth not supported yet ${elementIntypes}")
                } else {
                    tableElement.kindOfTableFieldType = kind2
                    tableElement.isKindOfTableFieldTypeOptional = true
                    tableElement.tableFieldType = elementInfields?.type?.ofType?.ofType?.name
                    tableElement.tableType = elementIntypes?.name
                    state.tables.add(tableElement)
                }
            }
        } else if (kind == OBJECT || kind == SCALAR || kind == ENUM) {
            tableElement.kindOfTableFieldType = kind
            tableElement.tableFieldType = elementInfields?.type?.ofType?.name
            tableElement.tableType = elementIntypes?.name
            state.tables.add(tableElement)
        } else {
            LoggingUtil.uniqueWarn(log, "Type not supported yet:  ${elementInfields?.type?.ofType?.kind}")
        }

    }


    /*
      This when an entry is not optional in argsTables
       */
    private fun handleNonOptionalInArgsTables(inputElement: Table, elementInArgs: InputValue, state: TempState) {
        val kind1 = elementInArgs?.type?.ofType?.kind
        val kind2 = elementInArgs?.type?.ofType?.ofType?.kind
        val kind3 = elementInArgs?.type?.ofType?.ofType?.ofType?.kind
        if (kind1 == LIST) {//non optional list
            inputElement.kindOfTableField = LIST
            inputElement.isKindOfTableFieldOptional = false
            if (kind2 == NON_NULL) {// non optional input object or scalar
                if (elementInArgs?.type?.ofType?.ofType?.ofType?.kind == INPUT_OBJECT) {// non optional input object
                    inputElement.kindOfTableFieldType = INPUT_OBJECT
                    inputElement.isKindOfTableFieldTypeOptional = false
                    inputElement.tableFieldType = elementInArgs?.type?.ofType?.ofType?.ofType?.name
                    inputElement.tableField = elementInArgs?.name
                    state.argsTables.add(inputElement)
                } else {// non optional scalar or enum
                    if (kind3 == SCALAR || kind3 == ENUM) {
                        inputElement.kindOfTableFieldType = SCALAR
                        inputElement.isKindOfTableFieldTypeOptional = false
                        inputElement.tableFieldType = elementInArgs?.type?.ofType?.ofType?.ofType?.name
                        inputElement.tableField = elementInArgs?.name
                        state.argsTables.add(inputElement)
                    }
                }
            } else { // optional input object or scalar or enum
                inputElement.isKindOfTableFieldTypeOptional = true
                if (kind2 == INPUT_OBJECT || kind2 == SCALAR || kind2 == ENUM) {
                    inputElement.kindOfTableFieldType = kind2
                    inputElement.isKindOfTableFieldTypeOptional = true
                    inputElement.tableFieldType = elementInArgs?.type?.ofType?.ofType?.name
                    inputElement.tableField = elementInArgs?.name
                    state.argsTables.add(inputElement)
                }
            }
        } else // non optional input object or scalar or enum not in a list
            if (kind1 == INPUT_OBJECT || kind1 == SCALAR || kind1 == ENUM) {
                inputElement.kindOfTableFieldType = kind1
                inputElement.isKindOfTableFieldTypeOptional = false
                inputElement.tableFieldType = elementInArgs?.type?.ofType?.name
                inputElement.tableField = elementInArgs?.name
                state.argsTables.add(inputElement)
            }
    }

    /*
       This when an entry is optional in argsTables
    */
    private fun handleOptionalInArgsTables(inputElement: Table, elementInArgs: InputValue, state: TempState) {
        val kind0 = elementInArgs?.type?.kind
        val kind1 = elementInArgs?.type?.ofType?.kind
        val kind2 = elementInArgs?.type?.ofType?.ofType?.kind
        if (kind0 == LIST) {//optional list in the top
            inputElement.kindOfTableField = LIST
            inputElement.isKindOfTableFieldOptional = true
            if (kind1 == NON_NULL) {// non optional input object or scalar
                if (kind2 == INPUT_OBJECT || kind2 == SCALAR || kind2 == ENUM) {
                    inputElement.kindOfTableFieldType = kind2
                    inputElement.isKindOfTableFieldTypeOptional = false
                    inputElement.tableFieldType = elementInArgs?.type?.ofType?.ofType?.name
                    inputElement.tableField = elementInArgs?.name
                    state.argsTables.add(inputElement)
                }
            } else //optional input object or scalar or enum
                if (kind1 == INPUT_OBJECT || kind1 == SCALAR || kind1 == ENUM) {
                    inputElement.kindOfTableFieldType = kind1
                    inputElement.isKindOfTableFieldTypeOptional = true
                    inputElement.tableFieldType = elementInArgs?.type?.ofType?.name
                    inputElement.tableField = elementInArgs?.name
                    state.argsTables.add(inputElement)
                }
        } else // optional input object or scalar or enum in the top
            if (kind0 == INPUT_OBJECT || kind0 == SCALAR || kind0 == ENUM) {
                inputElement.kindOfTableFieldType = kind0
                inputElement.isKindOfTableFieldTypeOptional = true
                inputElement.tableFieldType = elementInArgs?.type?.name
                inputElement.tableField = elementInArgs?.name
                state.argsTables.add(inputElement)
            }
    }

    /*
      Extract tempArgsTables
          */
    private fun extractTempArgsTables(state: TempState, schemaObj: SchemaObj) {
        for (elementInInputParamTable in state.argsTables) {
            if (elementInInputParamTable.kindOfTableFieldType == INPUT_OBJECT) {
                for (elementIntypes in schemaObj.data?.__schema?.types.orEmpty()) {
                    if ((elementInInputParamTable.tableFieldType == elementIntypes.name) && (elementIntypes.kind == INPUT_OBJECT))
                        for (elementInInputFields in elementIntypes.inputFields) {
                            val kind0 = elementInInputFields?.type?.kind
                            val kind1 = elementInInputFields?.type?.ofType?.kind
                            if (kind0 == NON_NULL) {//non optional scalar or enum
                                if (kind1 == SCALAR || kind1 == ENUM) {// non optional scalar or enum
                                    val inputElement = Table()
                                    inputElement.tableType = elementIntypes.name
                                    inputElement.kindOfTableFieldType = kind1
                                    inputElement.isKindOfTableFieldTypeOptional = false
                                    inputElement.tableFieldType = elementInInputFields?.type?.ofType?.name
                                    inputElement.tableField = elementInInputFields?.name
                                    state.tempArgsTables.add(inputElement)
                                }
                            } else // optional scalar or enum
                                if (kind0 == SCALAR || kind0 == ENUM) {// optional scalar or enum
                                    val inputElement = Table()
                                    inputElement.tableType = elementIntypes.name
                                    inputElement.kindOfTableFieldType = kind0
                                    inputElement.isKindOfTableFieldTypeOptional = true
                                    inputElement.tableFieldType = elementInInputFields?.type?.name
                                    inputElement.tableField = elementInInputFields?.name
                                    state.tempArgsTables.add(inputElement)
                                }
                        }
                }

            }
        }
    }

    private fun handleEnumInArgsTables(state: TempState, schemaObj: SchemaObj) {
        val allEnumElement: MutableMap<String, MutableList<String>> = mutableMapOf()
        for (elementInInputParamTable in state.argsTables) {
            for (elementIntypes in schemaObj.data?.__schema?.types.orEmpty()) {
                if ((elementInInputParamTable.kindOfTableFieldType == ENUM) && (elementIntypes.kind == ENUM) && (elementIntypes.name == elementInInputParamTable.tableFieldType)) {
                    val enumElement: MutableList<String> = mutableListOf()
                    for (elementInEnumValues in elementIntypes.enumValues) {
                        enumElement.add(elementInEnumValues.name)
                    }
                    allEnumElement.put(elementInInputParamTable.tableFieldType, enumElement)
                }
            }
        }
        for (elementInInputParamTable in state.argsTables) {

            for (elemntInAllEnumElement in allEnumElement) {

                if (elementInInputParamTable.tableFieldType == elemntInAllEnumElement.key)

                    for (elementInElementInAllEnumElement in elemntInAllEnumElement.value) {

                        elementInInputParamTable.enumValues.add(elementInElementInAllEnumElement)
                    }
            }
        }
    }

    private fun handleEnumInTempArgsTables(state: TempState, schemaObj: SchemaObj) {
        val allEnumElement: MutableMap<String, MutableList<String>> = mutableMapOf()
        for (elementInInputParamTable in state.tempArgsTables) {
            for (elementIntypes in schemaObj.data?.__schema?.types.orEmpty()) {
                if ((elementInInputParamTable.kindOfTableFieldType == ENUM) && (elementIntypes.kind == ENUM) && (elementIntypes.name == elementInInputParamTable.tableFieldType)) {
                    val enumElement: MutableList<String> = mutableListOf()
                    for (elementInEnumValues in elementIntypes.enumValues) {
                        enumElement.add(elementInEnumValues.name)
                    }
                    allEnumElement.put(elementInInputParamTable.tableFieldType, enumElement)
                }
            }
        }
        for (elementInInputParamTable in state.tempArgsTables) {

            for (elemntInAllEnumElement in allEnumElement) {

                if (elementInInputParamTable.tableFieldType == elemntInAllEnumElement.key)

                    for (elementInElementInAllEnumElement in elemntInAllEnumElement.value) {

                        elementInInputParamTable.enumValues.add(elementInElementInAllEnumElement)
                    }
            }
        }
    }

    private fun handleEnumInTables(state: TempState, schemaObj: SchemaObj) {
        val allEnumElement: MutableMap<String, MutableList<String>> = mutableMapOf()
        for (elementInInputParamTable in state.tables) {
            for (elementIntypes in schemaObj.data?.__schema?.types.orEmpty()) {
                if ((elementInInputParamTable.kindOfTableFieldType == ENUM) && (elementIntypes.kind == ENUM) && (elementIntypes.name == elementInInputParamTable.tableFieldType)) {
                    val enumElement: MutableList<String> = mutableListOf()
                    for (elementInEnumValues in elementIntypes.enumValues) {
                        enumElement.add(elementInEnumValues.name)
                    }
                    allEnumElement.put(elementInInputParamTable.tableFieldType, enumElement)
                }
            }
        }
        for (elementInInputParamTable in state.tables) {

            for (elemntInAllEnumElement in allEnumElement) {

                if (elementInInputParamTable.tableFieldType == elemntInAllEnumElement.key)

                    for (elementInElementInAllEnumElement in elemntInAllEnumElement.value) {

                        elementInInputParamTable.enumValues.add(elementInElementInAllEnumElement)
                    }
            }
        }
    }

    private fun handleOperation(
            state: TempState,
            actionCluster: MutableMap<String, Action>,
            methodName: String?,
            methodType: String?,
            tableFieldType: String,
            kindOfTableFieldType: String,
            kindOfTableField: String?,
            tableType: String,
            isKindOfTableFieldTypeOptional: Boolean,
            isKindOfTableFieldOptional: Boolean,
            tableFieldWithArgs: Boolean,
            enumValues: MutableList<String>
    ) {
        if (methodName == null) {
            log.warn("Skipping operation, as no method name is defined.")
            return;
        }
        if (methodType == null) {
            log.warn("Skipping operation, as no method type is defined.")
            return;
        }
        val type = when {
            methodType.equals("QUERY", true) -> GQMethodType.QUERY
            /*
               In some schemas, "Root" and "QueryType" types define the entry point of the GraphQL query.
                */
            methodType.equals("Root", true) -> GQMethodType.QUERY
            methodType.equals("QueryType", true) -> GQMethodType.QUERY
            methodType.equals("MUTATION", true) -> GQMethodType.MUTATION
            else -> {
                //TODO log warn
                return
            }
        }

        val actionId = "$methodName${idGenerator.incrementAndGet()}"

        val params = extractParams(state, methodName, tableFieldType, kindOfTableFieldType, kindOfTableField,
                tableType, isKindOfTableFieldTypeOptional,
                isKindOfTableFieldOptional, tableFieldWithArgs, enumValues)

        val action = GraphQLAction(actionId, methodName, type, params )

        actionCluster[action.getName()] = action
    }

    private fun extractParams(
            state: TempState,
            methodName: String,
            tableFieldType: String,
            kindOfTableFieldType: String,
            kindOfTableField: String?,
            tableType: String,
            isKindOfTableFieldTypeOptional: Boolean,
            isKindOfTableFieldOptional: Boolean,
            tableFieldWithArgs: Boolean,
            enumValues: MutableList<String>

    ): MutableList<Param> {

        val params = mutableListOf<Param>()
        val history: Deque<String> = ArrayDeque<String>()

        if (tableFieldWithArgs) {

            for (element in state.argsTables) {

                if (element.tableType == methodName) {

                    val gene = getGene(state, element.tableFieldType, element.kindOfTableField.toString(), element.kindOfTableFieldType.toString(), element.tableType.toString(), history,
                            element.isKindOfTableFieldTypeOptional, element.isKindOfTableFieldOptional, element.enumValues, methodName)

                    params.add(GQInputParam(element.tableFieldType, gene))
                }
            }


            val gene = getGene(state, tableFieldType, kindOfTableField, kindOfTableFieldType, tableType, history,
                    isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)
            params.add(GQReturnParam(tableFieldType, gene))
        } else {
            val gene = getGene(state, tableFieldType, kindOfTableField, kindOfTableFieldType, tableType, history,
                    isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)

            params.add(GQReturnParam(methodName, gene))

        }

        return params
    }

    private fun getGene(
            state: TempState,
            tableFieldType: String,
            kindOfTableField: String?,
            kindOfTableFieldType: String,
            tableType: String,
            history: Deque<String> = ArrayDeque<String>(),
            isKindOfTableFieldTypeOptional: Boolean,
            isKindOfTableFieldOptional: Boolean,
            enumValues: MutableList<String>,
            methodName: String
    ): Gene {

        when (kindOfTableField?.toLowerCase()) {
            "list" ->
                if (isKindOfTableFieldOptional) {
                    history.addLast(tableType)
                    val template = getGene(state, tableType, kindOfTableFieldType, kindOfTableField, tableFieldType, history,
                            isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)
                    history.removeLast()
                    return OptionalGene(methodName, ArrayGene(tableType, template))
                } else {
                    history.addLast(tableType)
                    val template = getGene(state, tableType, kindOfTableFieldType, kindOfTableField, tableFieldType, history,
                            isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)
                    history.removeLast()
                    return ArrayGene(methodName, template)
                }
            "object" ->
                if (isKindOfTableFieldTypeOptional) {
                    val optObjGene = createObjectGene(state, tableType, kindOfTableFieldType, history,
                            isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)
                    return OptionalGene(tableType, optObjGene)
                } else
                    return createObjectGene(state, tableType, kindOfTableFieldType, history,
                            isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)
            "input_object" ->
                if (isKindOfTableFieldTypeOptional) {
                    val optInputObjGene = createInputObjectGene(state, tableType, kindOfTableFieldType, history,
                            isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)
                    return OptionalGene(tableType, optInputObjGene)
                } else
                    return createInputObjectGene(state, tableType, kindOfTableFieldType, history,
                            isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)
            "int" ->
                if (isKindOfTableFieldTypeOptional)
                    return OptionalGene(tableType, IntegerGene(tableType))
                else
                    return IntegerGene(tableType)
            "string" ->
                if (isKindOfTableFieldTypeOptional)
                    return OptionalGene(tableType, StringGene(tableType))
                else
                    return StringGene(tableType)
            "float" ->
                if (isKindOfTableFieldTypeOptional)
                    return OptionalGene(tableType, FloatGene(tableType))
                else
                    return FloatGene(tableType)
            "boolean" ->
                if (isKindOfTableFieldTypeOptional)
                    return OptionalGene(tableType, BooleanGene(tableType))
                else
                    return BooleanGene(tableType)
            "long" ->
                if (isKindOfTableFieldTypeOptional)
                    return OptionalGene(tableType, LongGene(tableType))
                else
                    return LongGene(tableType)
            "null" ->
                return getGene(state, tableType, kindOfTableFieldType, kindOfTableField, tableFieldType, history,
                        isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)
            "date" ->
                if (isKindOfTableFieldTypeOptional)
                    return OptionalGene(tableType, BooleanGene(tableType))
                else
                    return DateGene(tableType)
            "enum" ->
                if (isKindOfTableFieldTypeOptional)
                    return OptionalGene(tableType, EnumGene(tableType, enumValues))
                else
                    return EnumGene(tableType, enumValues)
            "scalar" ->
                return getGene(state, tableFieldType, tableType, kindOfTableFieldType, kindOfTableField, history,
                        isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, enumValues, methodName)
            "id" ->
                if (isKindOfTableFieldTypeOptional)
                    return OptionalGene(tableType, StringGene(tableType))
                else
                    return StringGene(tableType)

            "union" -> {
                LoggingUtil.uniqueWarn(log, "Kind Of Table Field not supported yet: $kindOfTableField")
                return StringGene("TODO")
            }
            "interface" -> {
                LoggingUtil.uniqueWarn(log, "Kind Of Table Field not supported yet: $kindOfTableField")
                return StringGene("TODO")
            }
            else ->
                if (isKindOfTableFieldTypeOptional)
                    return OptionalGene(tableType, StringGene(tableType))
                else
                    return StringGene(tableType)

        }
    }

    private fun createObjectGene(
            state: TempState,
            tableType: String,
            kindOfTableFieldType: String,
            history: Deque<String> = ArrayDeque<String>(),
            isKindOfTableFieldTypeOptional: Boolean,
            isKindOfTableFieldOptional: Boolean,
            enumValues: MutableList<String>,
            methodName: String
    ): Gene {

        val fields: MutableList<Gene> = mutableListOf()
        if (history.count { it == tableType } <= 1) {
            for (element in state.tables) {
                if (element.tableType == tableType) {
                    if (element.kindOfTableFieldType.toString().equals("SCALAR", ignoreCase = true)) {
                        val field = element.tableField
                        val template = field?.let {
                            getGene(state, tableType, element.tableFieldType, kindOfTableFieldType, it, history,
                                    element.isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, element.enumValues, methodName)
                        }
                        if (template != null)
                            fields.add(template)

                    } else {
                        if (element.kindOfTableField.toString().equals("LIST", ignoreCase = true)) {
                            val template =
                                    element.tableField?.let {
                                        getGene(state, element.tableFieldType, element.kindOfTableField.toString(), element.kindOfTableFieldType.toString(),
                                                element.tableFieldType, history, isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, element.enumValues, it)
                                    }


                            if (template != null)
                                fields.add(template)
                        } else
                            if (element.kindOfTableFieldType.toString().equals("OBJECT", ignoreCase = true)) {
                                history.addLast(element.tableType)
                                history.addLast(element.tableFieldType)
                                if (history.count { it == element.tableFieldType } == 1) {
                                    val template =
                                            element.tableField?.let {
                                                getGene(state, element.tableFieldType, element.kindOfTableFieldType.toString(), element.kindOfTableField.toString(),
                                                        element.tableFieldType, history, isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, element.enumValues, it)
                                            }

                                    history.removeLast()
                                    history.removeLast()
                                    if (template != null) {
                                        fields.add(template)
                                    }
                                } else {
                                    fields.add(CycleObjectGene(element.tableFieldType))
                                    history.removeLast()

                                }
                            } else if (element.kindOfTableFieldType.toString().equals("ENUM", ignoreCase = true)) {
                                val field = element.tableField
                                val template = field?.let {
                                    getGene(state, tableType, element.kindOfTableFieldType.toString(), kindOfTableFieldType, it, history,
                                            element.isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, element.enumValues, methodName)
                                }
                                if (template != null)
                                    fields.add(template)

                            }
                    }
                }

            }
            return ObjectGene(methodName, fields, tableType)
        } else {
            fields.add(CycleObjectGene(tableType))
            return ObjectGene(methodName, fields, tableType)
        }
    }

    private fun createInputObjectGene(
            state: TempState,
            tableType: String,
            kindOfTableFieldType: String,
            history: Deque<String> = ArrayDeque<String>(),
            isKindOfTableFieldTypeOptional: Boolean,
            isKindOfTableFieldOptional: Boolean,
            enumValues: MutableList<String>,
            methodName: String
    ): Gene {
        val fields: MutableList<Gene> = mutableListOf()
        for (element in state.argsTables) {
            if (element.tableType == tableType) {
                if (element.kindOfTableFieldType.toString().equals("SCALAR", ignoreCase = true)) {
                    val field = element.tableField
                    val template = field?.let {
                        getGene(state, tableType, element.tableFieldType, kindOfTableFieldType, it, history,
                                element.isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, element.enumValues, tableType)
                    }
                    if (template != null)
                        fields.add(template)

                } else {
                    if (element.kindOfTableField.toString().equals("LIST", ignoreCase = true)) {
                        val template = element.tableField?.let {
                            getGene(state, element.tableFieldType, element.kindOfTableField.toString(),
                                    element.kindOfTableFieldType.toString(),
                                    element.tableFieldType, history, isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, element.enumValues, it)
                        }

                        if (template != null) {
                            fields.add(template)
                        }
                    } else
                        if (element.kindOfTableFieldType.toString().equals("INPUT_OBJECT", ignoreCase = true)) {
                            val template = element.tableField?.let {
                                getGene(state, element.tableFieldType, element.kindOfTableFieldType.toString(), element.kindOfTableField.toString(),
                                        element.tableFieldType, history, isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, element.enumValues, it)
                            }
                            if (template != null)
                                fields.add(template)

                        } else if (element.kindOfTableFieldType.toString().equals("ENUM", ignoreCase = true)) {
                            val field = element.tableField
                            val template = field?.let {
                                getGene(state, tableType, element.kindOfTableFieldType.toString(), kindOfTableFieldType, it, history,
                                        element.isKindOfTableFieldTypeOptional, isKindOfTableFieldOptional, element.enumValues, tableType)
                            }
                            if (template != null)
                                fields.add(template)

                        }
                }
            }
        }
        return ObjectGene(methodName, fields, tableType)
    }

}