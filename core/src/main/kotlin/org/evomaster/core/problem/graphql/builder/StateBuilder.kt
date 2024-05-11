package org.evomaster.core.problem.graphql.builder

import org.evomaster.core.StaticCounter
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.problem.graphql.schema.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

object StateBuilder {

    private val log: Logger = LoggerFactory.getLogger(StateBuilder::class.java)

    /**
     * Specific terms/keywords used in the generated JSON schema retrieved
     * with an introspective query
     */
    private val systemTypes = listOf(
        "__Schema", "__Directive", "__DirectiveLocation", "__EnumValue",
        "__Field", "__InputValue", "__Type", "__TypeKind"
    )

    fun initTablesInfo(schemaObj: SchemaObj): TempState {

        val state = TempState()

        for (elementIntypes in schemaObj.data.__schema.types) {//for each type: object, scalar,enum
            if (systemTypes.contains(elementIntypes.name)) {//if the type is internal to gql skip it
                continue
            }

            for (elementInfields in elementIntypes.fields.orEmpty()) {//for each field in the type
                /**
                 * extracting tables
                 */
                if (elementInfields.args.isNotEmpty()) {
                    val isFieldNameWithArgs = true
                    var fieldName = elementInfields.name

                    if (state.tables.isNotEmpty())
                        for (entry in state.tables) {
                            if (entry.fieldName == elementInfields.name) {
                                fieldName = "${elementInfields.name}${StaticCounter.getAndIncrease()}"
                                state.inputTypeName[fieldName]= elementInfields.name
                            }
                        }
                    if (elementInfields.type.kind == __TypeKind.NON_NULL) // non optional list or object or scalar
                        handleNonOptionalInTables(
                            elementInfields,
                            isFieldNameWithArgs,
                            fieldName,
                            elementIntypes,
                            state,
                            schemaObj
                        )
                    else
                        handleOptionalInTables(
                            elementInfields,
                            isFieldNameWithArgs,
                            fieldName,
                            elementIntypes,
                            state,
                            schemaObj
                        )

                    /*
                    * extracting argsTables: 1/2
                    */

                    for (elementInArgs in elementInfields.args) {

                        handleArguments(elementInArgs, fieldName, isFieldNameWithArgs, state, schemaObj)

                    }
                } else {
                    val isFieldNameWithArgs = false
                    if (elementInfields.type.kind == __TypeKind.NON_NULL) // non optional list or object or scalar
                        handleNonOptionalInTables(
                            elementInfields,
                            isFieldNameWithArgs,
                            elementInfields.name,
                            elementIntypes,
                            state,
                            schemaObj
                        )
                    else
                        handleOptionalInTables(
                            elementInfields,
                            isFieldNameWithArgs,
                            elementInfields.name,
                            elementIntypes,
                            state,
                            schemaObj
                        )
                }
            }
        }

        /*
         *extracting tempArgsTables, an intermediate table for extracting argsTables
         */
        extractTempArgsTables(state, schemaObj)

        /*
         * merging argsTables with tempArgsTables: extracting argsTables: 2/2
         */
        state.argsTables.addAll(state.tempArgsTables)
        state.argsTables =
            state.argsTables.distinctBy { Pair(it.typeName, it.fieldName) }.toMutableList()//remove redundant elements
        state.tables =
            state.tables.distinctBy { Pair(it.typeName, it.fieldName) }.toMutableList()//remove redundant elements

        initTablesAndArgsTablesIndexedByName(state)

        return state
    }

    private fun handleArguments(
        elementInArgs: InputValue,
        typeName: String,
        isFieldNameWithArgs: Boolean,
        state: TempState,
        schemaObj: SchemaObj
    ) {
        if (elementInArgs.type.kind == __TypeKind.NON_NULL) //non optional list or object or scalar or enum
            handleNonOptionalInArgsTables(
                typeName,
                isFieldNameWithArgs,
                elementInArgs,
                state,
                schemaObj
            )
        else  //optional list or input object or scalar or enum

            handleOptionalInArgsTables(
                typeName,
                isFieldNameWithArgs,
                elementInArgs,
                state,
                schemaObj
            )
    }

    private fun initTablesAndArgsTablesIndexedByName(state: TempState) {
        /*
        A set of all possible names
         */
        val namesInTable = state.tables.map { it.typeName }.filterNotNull().toSet()
        val namesInArgsTable = state.argsTables.map { it.typeName }.filterNotNull().toSet()
        /*
        Attach to each name its tables
         */
        for (n in namesInTable) {
            val list = state.tables.filter { it.typeName == n }
            state.tablesIndexedByName[n] = list
        }
        for (n in namesInArgsTable) {
            val list = state.argsTables.filter { it.typeName == n }
            state.argsTablesIndexedByName[n] = list
        }
    }

    /*
    This when an entry is optional in Tables
    */
    private fun handleOptionalInTables(
        elementInfields: __Field,
        isFieldNameWithArgs: Boolean,
        fieldName: String,
        elementInTypes: FullType,
        state: TempState,
        schemaObj: SchemaObj
    ) {
        /*
        *Note: the introspective query of GQl goes until 7 ofType. Here we go until 3 ofTypes since only 2 APIs go deeper.
         */
        val (kind0, kind1, kind2, kind3)= quadKinds(elementInfields)

        if (kind0 == __TypeKind.LIST) {//optional list in the top
            if (kind1 == __TypeKind.NON_NULL) {// non optional object or scalar or enum or union or interface
                if (kind2?.let { isKindObjOrScaOrEnumOrUniOrInter(it) }!!)
                    when (kind2) {
                        __TypeKind.ENUM -> {
                            val enumElement: MutableList<String> =
                                collectEnumElementsInTable(schemaObj, elementInfields.type.ofType.ofType.name)
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isKindOfFieldNameOptional = true,
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    isKindOfFieldTypeOptional = false,

                                    kindOfFieldType = kind2.toString(),
                                    fieldType = elementInfields.type.ofType.ofType.name,
                                    typeName = elementInTypes.name,

                                    enumValues = enumElement
                                )
                            )
                        }
                        __TypeKind.UNION -> {
                            val unionElement: MutableList<String> =
                                collectUnionElementsInTable(schemaObj, elementInfields.type.ofType.ofType.name)
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isKindOfFieldNameOptional = true,
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    isKindOfFieldTypeOptional = false,

                                    kindOfFieldType = kind2.toString(),
                                    fieldType = elementInfields.type.ofType.ofType.name,
                                    typeName = elementInTypes.name,
                                    unionTypes = unionElement
                                )
                            )

                        }
                        __TypeKind.INTERFACE -> {
                            val interfaceElement: MutableList<String> =
                                collectInterfaceElementsInTable(schemaObj, elementInfields.type.ofType.ofType.name)
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isKindOfFieldNameOptional = true,
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    isKindOfFieldTypeOptional = false,

                                    kindOfFieldType = kind2.toString(),
                                    fieldType = elementInfields.type.ofType.ofType.name,
                                    typeName = elementInTypes.name,
                                    interfaceTypes = interfaceElement
                                )
                            )
                        }
                        else -> {//not enum, not union, not interface
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isKindOfFieldNameOptional = true,
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    isKindOfFieldTypeOptional = false,

                                    kindOfFieldType = kind2.toString(),
                                    fieldType = elementInfields.type.ofType.ofType.name,
                                    typeName = elementInTypes.name
                                )
                            )
                        }
                    }

            } else //optional object or scalar or enum or union or interface
                if (kind1?.let { isKindObjOrScaOrEnumOrUniOrInter(it) }!!)
                    when (kind1) {
                        __TypeKind.ENUM -> {
                            val enumElement: MutableList<String> =
                                collectEnumElementsInTable(schemaObj, elementInfields.type.ofType.name)
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isKindOfFieldNameOptional = true,
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    isKindOfFieldTypeOptional = true,

                                    kindOfFieldType = kind1.toString(),
                                    fieldType = elementInfields.type.ofType.name,
                                    typeName = elementInTypes.name,

                                    enumValues = enumElement

                                )
                            )
                        }
                        __TypeKind.UNION -> {//not enum, but union

                            val unionElement: MutableList<String> =
                                collectUnionElementsInTable(schemaObj, elementInfields.type.ofType.name)
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isKindOfFieldNameOptional = true,
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    isKindOfFieldTypeOptional = true,

                                    kindOfFieldType = kind1.toString(),
                                    fieldType = elementInfields.type.ofType.name,
                                    typeName = elementInTypes.name,

                                    unionTypes = unionElement

                                )
                            )

                        }
                        __TypeKind.INTERFACE -> {
                            val interfaceElement: MutableList<String> =
                                collectInterfaceElementsInTable(schemaObj, elementInfields.type.ofType.name)
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isKindOfFieldNameOptional = true,
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    isKindOfFieldTypeOptional = true,

                                    kindOfFieldType = kind1.toString(),
                                    fieldType = elementInfields.type.ofType.name,
                                    typeName = elementInTypes.name,

                                    interfaceTypes = interfaceElement
                                )
                            )
                        }
                        else -> {
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isKindOfFieldNameOptional = true,
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    isKindOfFieldTypeOptional = true,

                                    kindOfFieldType = kind1.toString(),
                                    fieldType = elementInfields.type.ofType.name,
                                    typeName = elementInTypes.name
                                )
                            )
                        }
                    }

        } else
            if (kind0?.let { isKindObjOrScaOrEnumOrUniOrInter(it) }!!) // optional object or scalar or enum or union or interface in the top

                when (kind0) {
                    __TypeKind.ENUM -> {
                        val enumElement: MutableList<String> =
                            collectEnumElementsInTable(schemaObj, elementInfields.type.name)
                        state.tables.add(
                            Table(
                                fieldName = fieldName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                isKindOfFieldTypeOptional = true,

                                kindOfFieldType = kind0.toString(),
                                fieldType = elementInfields.type.name,
                                typeName = elementInTypes.name,

                                enumValues = enumElement
                            )
                        )

                    }
                    __TypeKind.UNION -> {
                        val unionElement: MutableList<String> =
                            collectUnionElementsInTable(schemaObj, elementInfields.type.name)
                        state.tables.add(
                            Table(
                                fieldName = fieldName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                isKindOfFieldTypeOptional = true,

                                kindOfFieldType = kind0.toString(),
                                fieldType = elementInfields.type.name,
                                typeName = elementInTypes.name,

                                unionTypes = unionElement
                            )
                        )
                    }
                    __TypeKind.INTERFACE -> {
                        val interfaceElement: MutableList<String> =
                            collectInterfaceElementsInTable(schemaObj, elementInfields.type.name)
                        state.tables.add(
                            Table(
                                fieldName = fieldName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                isKindOfFieldTypeOptional = true,

                                kindOfFieldType = kind0.toString(),
                                fieldType = elementInfields.type.name,
                                typeName = elementInTypes.name,

                                interfaceTypes = interfaceElement
                            )
                        )
                    }
                    else -> {
                        state.tables.add(
                            Table(
                                fieldName = fieldName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                isKindOfFieldTypeOptional = true,

                                kindOfFieldType = kind0.toString(),
                                fieldType = elementInfields.type.name,
                                typeName = elementInTypes.name
                            )
                        )
                    }
                }

    }

    /*
        This is to handle entries that are NOT optional, and must be there, ie, they cannot be null
     */
    private fun handleNonOptionalInTables(
        elementInfields: __Field,
        isFieldNameWithArgs: Boolean,
        fieldName: String,
        elementIntypes: FullType,
        state: TempState,
        schemaObj: SchemaObj
    ) {
        val (kind0, kind1, kind2, kind3)= quadKinds(elementInfields)
        if (kind1 == __TypeKind.LIST) {// non optional list
            if (kind2 == __TypeKind.NON_NULL) {// non optional object or scalar or enum or union or interface

                when (kind3) {
                    __TypeKind.ENUM -> {
                        val enumElement: MutableList<String> =
                            collectEnumElementsInTable(schemaObj, elementInfields.type.ofType.ofType.ofType.name)
                        state.tables.add(
                            Table(
                                fieldName = fieldName,
                                isKindOfFieldTypeOptional = false,
                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                kindOfFieldType = kind3.toString(),
                                fieldType = elementInfields.type.ofType.ofType.ofType.name,
                                typeName = elementIntypes.name,
                                isKindOfFieldNameOptional = false,

                                enumValues = enumElement
                            )
                        )

                    }
                    __TypeKind.UNION -> {
                        val unionElement: MutableList<String> =
                            collectUnionElementsInTable(schemaObj, elementInfields.type.ofType.ofType.ofType.name)
                        state.tables.add(
                            Table(
                                fieldName = fieldName,
                                isKindOfFieldTypeOptional = false,
                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                kindOfFieldType = kind3.toString(),
                                fieldType = elementInfields.type.ofType.ofType.ofType.name,
                                typeName = elementIntypes.name,
                                isKindOfFieldNameOptional = false,

                                unionTypes = unionElement
                            )
                        )
                    }
                    __TypeKind.INTERFACE -> {
                        val interfaceElement: MutableList<String> =
                            collectInterfaceElementsInTable(schemaObj, elementInfields.type.ofType.ofType.ofType.name)
                        state.tables.add(
                            Table(
                                fieldName = fieldName,
                                isKindOfFieldTypeOptional = false,
                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                kindOfFieldType = kind3.toString(),
                                fieldType = elementInfields.type.ofType.ofType.ofType.name,
                                typeName = elementIntypes.name,
                                isKindOfFieldNameOptional = false,

                                interfaceTypes = interfaceElement
                            )
                        )
                    }

                    else -> {
                        state.tables.add(
                            Table(
                                fieldName = fieldName,
                                isKindOfFieldTypeOptional = false,
                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                kindOfFieldType = kind3.toString(),//kind3 could be null
                                fieldType = elementInfields.type.ofType.ofType.ofType.name,
                                typeName = elementIntypes.name,
                                isKindOfFieldNameOptional = false
                            )
                        )
                    }
                }
            } else {//optional object or scalar or enum or union or interface
                if (elementInfields?.type?.ofType?.ofType?.name == null)
                    LoggingUtil.uniqueWarn(log, "Depth not supported yet $elementIntypes")
                else
                    when (kind2) {
                        __TypeKind.ENUM -> {
                            val enumElement: MutableList<String> =
                                collectEnumElementsInTable(schemaObj, elementInfields.type.ofType.ofType.name)
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    isKindOfFieldNameOptional = false,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    kindOfFieldType = kind2.toString(),
                                    isKindOfFieldTypeOptional = true,
                                    fieldType = elementInfields.type.ofType.ofType.name,
                                    typeName = elementIntypes.name,

                                    enumValues = enumElement
                                )
                            )
                        }
                        __TypeKind.UNION -> {
                            val unionElement: MutableList<String> =
                                collectUnionElementsInTable(schemaObj, elementInfields.type.ofType.ofType.name)
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    isKindOfFieldNameOptional = false,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    kindOfFieldType = kind2.toString(),
                                    isKindOfFieldTypeOptional = true,
                                    fieldType = elementInfields.type.ofType.ofType.name,
                                    typeName = elementIntypes.name,

                                    unionTypes = unionElement
                                )
                            )
                        }
                        __TypeKind.INTERFACE -> {
                            val interfaceElement: MutableList<String> =
                                collectInterfaceElementsInTable(schemaObj, elementInfields.type.ofType.ofType.name)
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    isKindOfFieldNameOptional = false,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    kindOfFieldType = kind2.toString(),
                                    isKindOfFieldTypeOptional = true,
                                    fieldType = elementInfields.type.ofType.ofType.name,
                                    typeName = elementIntypes.name,

                                    interfaceTypes = interfaceElement
                                )
                            )
                        }
                        else -> {
                            state.tables.add(
                                Table(
                                    fieldName = fieldName,
                                    isKindOfFieldNameOptional = false,
                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                    isFieldNameWithArgs = isFieldNameWithArgs,

                                    kindOfFieldType = kind2.toString(),
                                    isKindOfFieldTypeOptional = true,
                                    fieldType = elementInfields.type.ofType.ofType.name,
                                    typeName = elementIntypes.name
                                )
                            )

                        }
                    }
            }
        } else if (kind1?.let { isKindObjOrScaOrEnumOrUniOrInter(it) }!!)
            when (kind1) {
                __TypeKind.ENUM -> {
                    val enumElement: MutableList<String> =
                        collectEnumElementsInTable(schemaObj, elementInfields.type.ofType.name)

                    state.tables.add(
                        Table(
                            fieldName = fieldName,
                            isKindOfFieldNameOptional = false,
                            isFieldNameWithArgs = isFieldNameWithArgs,

                            kindOfFieldType = kind1.toString(),
                            fieldType = elementInfields.type.ofType.name,
                            typeName = elementIntypes.name,

                            enumValues = enumElement
                        )
                    )
                }


                __TypeKind.UNION -> {
                    val unionElement: MutableList<String> =
                        collectUnionElementsInTable(schemaObj, elementInfields.type.ofType.name)

                    state.tables.add(
                        Table(
                            fieldName = fieldName,
                            isKindOfFieldNameOptional = false,
                            isFieldNameWithArgs = isFieldNameWithArgs,

                            kindOfFieldType = kind1.toString(),
                            fieldType = elementInfields.type.ofType.name,
                            typeName = elementIntypes.name,

                            unionTypes = unionElement
                        )
                    )
                }

                __TypeKind.INTERFACE -> {
                    val interfaceElement: MutableList<String> =
                        collectInterfaceElementsInTable(schemaObj, elementInfields.type.ofType.name)

                    state.tables.add(
                        Table(
                            fieldName = fieldName,
                            isKindOfFieldNameOptional = false,
                            isFieldNameWithArgs = isFieldNameWithArgs,

                            kindOfFieldType = kind1.toString(),
                            fieldType = elementInfields.type.ofType.name,
                            typeName = elementIntypes.name,

                            interfaceTypes = interfaceElement
                        )
                    )
                }

                else -> {
                    state.tables.add(
                        Table(
                            fieldName = fieldName,
                            isKindOfFieldNameOptional = false,
                            isFieldNameWithArgs = isFieldNameWithArgs,

                            kindOfFieldType = kind1.toString(),
                            fieldType = elementInfields.type.ofType.name,
                            typeName = elementIntypes.name,
                        )
                    )
                }
            }
        else
            LoggingUtil.uniqueWarn(log, "Type not supported yet:  ${elementInfields.type.ofType.kind}")

    }

    private fun collectEnumElementsInTable(
        schemaObj: SchemaObj,
        elementInfields: String
    ): MutableList<String> {
        val enumElement: MutableList<String> = mutableListOf()
        for (elementIntypes in schemaObj.data.__schema.types)
            if ((elementIntypes.kind == __TypeKind.ENUM) && (elementIntypes.name == elementInfields))
                for (elementInEnumValues in elementIntypes.enumValues)
                    enumElement.add(elementInEnumValues.name)//Get the enum list of this element
        return enumElement
    }

    private fun collectUnionElementsInTable(
        schemaObj: SchemaObj,
        elementInfields: String
    ): MutableList<String> {
        val unionElement: MutableList<String> = mutableListOf()
        for (elementIntypes in schemaObj.data.__schema.types)
            if ((elementIntypes.kind == __TypeKind.UNION) && (elementIntypes.name == elementInfields))
                for (elementInUnionTypes in elementIntypes.possibleTypes)
                    unionElement.add(elementInUnionTypes.name)//get the name of the obj_n
        return unionElement
    }

    private fun collectInterfaceElementsInTable(schemaObj: SchemaObj,  elementInfields: String): MutableList<String> {
        val interfaceElement: MutableList<String> = mutableListOf()
        for (elementIntypes in schemaObj.data.__schema.types)
            if ((elementIntypes.kind == __TypeKind.INTERFACE) && (elementIntypes.name == elementInfields))
                for (elementInInterfaceTypes in elementIntypes.possibleTypes)
                    interfaceElement.add(elementInInterfaceTypes.name)//get the name of the obj_n
        return interfaceElement
    }

    private fun isKindObjOrScaOrEnumOrUniOrInter(kind: __TypeKind) =
        kind == __TypeKind.OBJECT || kind == __TypeKind.SCALAR || kind == __TypeKind.ENUM || kind == __TypeKind.UNION || kind == __TypeKind.INTERFACE

    /*
      This when an entry is not optional in argsTables
       */
    private fun handleNonOptionalInArgsTables(
        typeName: String,
        isFieldNameWithArgs: Boolean,
        elementInArgs: InputValue,
        state: TempState,
        schemaObj: SchemaObj
    ) {
        val (kind0, kind1, kind2, kind3) = quadKindsInInputs(elementInArgs)

        if (kind1 == __TypeKind.LIST) {//non optional list
            if (kind2 == __TypeKind.NON_NULL) {// non-optional input object or scalar or enum
                if (elementInArgs.type.ofType.ofType.ofType.kind == __TypeKind.INPUT_OBJECT)
                    state.argsTables.add(
                        Table(
                            typeName = typeName,
                            isFieldNameWithArgs = isFieldNameWithArgs,

                            KindOfFieldName = __TypeKind.LIST.toString(),
                            isKindOfFieldNameOptional = false,

                            kindOfFieldType = __TypeKind.INPUT_OBJECT.toString(),
                            isKindOfFieldTypeOptional = false,
                            fieldType = elementInArgs.type.ofType.ofType.ofType.name,
                            fieldName = elementInArgs.name
                        )
                    )
                else if (kind3 == __TypeKind.SCALAR || kind3 == __TypeKind.ENUM) {
                    if (kind3 == __TypeKind.ENUM) {//enum
                        val enumElement: MutableList<String> =
                            collectEnumElementsInTable(schemaObj, elementInArgs.type.ofType.ofType.ofType.name)
                        state.argsTables.add(
                            Table(
                                typeName = typeName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isKindOfFieldNameOptional = false,

                                kindOfFieldType = __TypeKind.ENUM.toString(),
                                isKindOfFieldTypeOptional = false,
                                fieldType = elementInArgs.type.ofType.ofType.ofType.name,
                                fieldName = elementInArgs.name,

                                enumValues = enumElement
                            )
                        )
                    } else //scalar
                        state.argsTables.add(
                            Table(
                                typeName = typeName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isKindOfFieldNameOptional = false,

                                kindOfFieldType = __TypeKind.SCALAR.toString(),
                                isKindOfFieldTypeOptional = false,
                                fieldType = elementInArgs.type.ofType.ofType.ofType.name,
                                fieldName = elementInArgs.name
                            )
                        )
                }
            } else //optional: input object or scalar or enum
                if (isKindInputObjOrScaOrEnum(kind2!!)) {
                    if (kind2 == __TypeKind.ENUM) {//enum
                        val enumElement: MutableList<String> =
                            collectEnumElementsInTable(schemaObj, elementInArgs.type.ofType.ofType.name)
                        state.argsTables.add(
                            Table(
                                typeName = typeName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isKindOfFieldNameOptional = false,

                                isKindOfFieldTypeOptional = true,

                                kindOfFieldType = kind2.toString(),
                                fieldType = elementInArgs.type.ofType.ofType.name,
                                fieldName = elementInArgs.name,

                                enumValues = enumElement
                            )
                        )
                    } else//scalar or input obj
                        state.argsTables.add(
                            Table(
                                typeName = typeName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isKindOfFieldNameOptional = false,

                                isKindOfFieldTypeOptional = true,

                                kindOfFieldType = kind2.toString(),
                                fieldType = elementInArgs.type.ofType.ofType.name,
                                fieldName = elementInArgs.name
                            )
                        )
                }
        } else // non-optional: input object or scalar or enum not in a list
            if (kind1?.let { isKindInputObjOrScaOrEnum(it) }!!) {
                if (kind1 == __TypeKind.ENUM) {
                    val enumElement: MutableList<String> =
                        collectEnumElementsInTable(schemaObj, elementInArgs.type.ofType.name)

                    state.argsTables.add(
                        Table(
                            typeName = typeName,
                            isFieldNameWithArgs = isFieldNameWithArgs,

                            kindOfFieldType = kind1.toString(),
                            isKindOfFieldTypeOptional = false,
                            fieldType = elementInArgs.type.ofType.name,
                            fieldName = elementInArgs.name,

                            enumValues = enumElement
                        )
                    )
                } else
                    state.argsTables.add(
                        Table(
                            typeName = typeName,
                            isFieldNameWithArgs = isFieldNameWithArgs,

                            kindOfFieldType = kind1.toString(),
                            isKindOfFieldTypeOptional = false,
                            fieldType = elementInArgs.type.ofType.name,
                            fieldName = elementInArgs.name,
                        )
                    )
            }
    }

    /*
       This when an entry is optional in argsTables
    */
    private fun handleOptionalInArgsTables(
        typeName: String,
        isFieldNameWithArgs: Boolean,
        elementInArgs: InputValue,
        state: TempState,
        schemaObj: SchemaObj
    ) {
        val (kind0, kind1, kind2, kind3)= quadKindsInInputs(elementInArgs)

        if (kind0 == __TypeKind.LIST) {//optional list in the top
            if (kind1 == __TypeKind.NON_NULL) {// non optional input object or scalar
                if (kind2?.let { isKindInputObjOrScaOrEnum(it) }!!) {
                    if (kind2 == __TypeKind.ENUM) {
                        val enumElement: MutableList<String> =
                            collectEnumElementsInTable(schemaObj, elementInArgs.type.ofType.ofType.name)
                        state.argsTables.add(
                            Table(
                                typeName = typeName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isKindOfFieldNameOptional = true,

                                kindOfFieldType = kind2.toString(),
                                isKindOfFieldTypeOptional = false,
                                fieldType = elementInArgs.type.ofType.ofType.name,
                                fieldName = elementInArgs.name,

                                enumValues = enumElement
                            )
                        )
                    } else
                        state.argsTables.add(
                            Table(
                                typeName = typeName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isKindOfFieldNameOptional = true,

                                kindOfFieldType = kind2.toString(),
                                isKindOfFieldTypeOptional = false,
                                fieldType = elementInArgs.type.ofType.ofType.name,
                                fieldName = elementInArgs.name
                            )
                        )
                }
            } else //optional input object or scalar or enum
                if (kind1?.let { isKindInputObjOrScaOrEnum(it) }!!) {
                    if (kind1 == __TypeKind.ENUM) {
                        val enumElement: MutableList<String> =
                            collectEnumElementsInTable(schemaObj, elementInArgs.type.ofType.name)

                        state.argsTables.add(
                            Table(
                                typeName = typeName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isKindOfFieldNameOptional = true,

                                kindOfFieldType = kind1.toString(),
                                isKindOfFieldTypeOptional = true,
                                fieldType = elementInArgs.type.ofType.name,
                                fieldName = elementInArgs.name,

                                enumValues = enumElement
                            )
                        )
                    } else
                        state.argsTables.add(
                            Table(
                                typeName = typeName,
                                isFieldNameWithArgs = isFieldNameWithArgs,

                                KindOfFieldName = __TypeKind.LIST.toString(),
                                isKindOfFieldNameOptional = true,

                                kindOfFieldType = kind1.toString(),
                                isKindOfFieldTypeOptional = true,
                                fieldType = elementInArgs.type.ofType.name,
                                fieldName = elementInArgs.name
                            )
                        )
                }
        } else // optional input object or scalar or enum in the top
            if (kind0?.let { isKindInputObjOrScaOrEnum(it) }!!) {

                if (kind0 == __TypeKind.ENUM) {
                    val enumElement: MutableList<String> =
                        collectEnumElementsInTable(schemaObj, elementInArgs.type.name)

                    state.argsTables.add(
                        Table(
                            typeName = typeName,
                            isFieldNameWithArgs = isFieldNameWithArgs,

                            kindOfFieldType = kind0.toString(),
                            isKindOfFieldTypeOptional = true,
                            fieldType = elementInArgs.type.name,
                            fieldName = elementInArgs.name,
                            enumValues = enumElement
                        )
                    )
                } else
                    state.argsTables.add(
                        Table(
                            typeName = typeName,
                            isFieldNameWithArgs = isFieldNameWithArgs,

                            kindOfFieldType = kind0.toString(),
                            isKindOfFieldTypeOptional = true,
                            fieldType = elementInArgs.type.name,
                            fieldName = elementInArgs.name
                        )
                    )
            }
    }

    private fun isKindInputObjOrScaOrEnum(kind: __TypeKind) =
        kind == __TypeKind.INPUT_OBJECT || kind == __TypeKind.SCALAR || kind == __TypeKind.ENUM

    /*
      Extract tempArgsTables
          */
    private fun extractTempArgsTables(state: TempState, schemaObj: SchemaObj) {
        //Todo we need better strategies for arguments extraction
        for (elementInInputParamTable in state.argsTables)
            if (elementInInputParamTable.kindOfFieldType == __TypeKind.INPUT_OBJECT.toString())
                for (elementIntypes in schemaObj.data.__schema.types)
                    if ((elementInInputParamTable.fieldType == elementIntypes.name) && (elementIntypes.kind == __TypeKind.INPUT_OBJECT))
                        for (elementInInputFields in elementIntypes.inputFields) {

                            /* val kind0 = elementInInputFields.type.kind
                            val kind1 = elementInInputFields?.type?.ofType?.kind*/

                            val (kind0, kind1, kind2, kind3) = quadKindsInInputs(elementInInputFields)

                            if (kind0 == __TypeKind.NON_NULL) {////non-optional list or scalar or enum
                                if (kind1 == __TypeKind.LIST) {//nn-optional list in the top
                                    if (kind2 == __TypeKind.NON_NULL) {
                                        if (kind3 == __TypeKind.ENUM) {
                                            val enumElement: MutableList<String> =
                                                collectEnumElementsInTable(
                                                    schemaObj,
                                                    elementInInputFields.type.ofType.ofType.ofType.name
                                                )
                                            state.tempArgsTables.add(
                                                Table(
                                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                                    isKindOfFieldNameOptional = false,

                                                    typeName = elementIntypes.name,
                                                    kindOfFieldType = kind3.toString(),
                                                    isKindOfFieldTypeOptional = false,
                                                    fieldType = elementInInputFields.type.ofType.ofType.ofType.name,
                                                    fieldName = elementInInputFields.name,
                                                    enumValues = enumElement
                                                )
                                            )
                                        } else
                                            if (kind3 == __TypeKind.SCALAR) {
                                                state.tempArgsTables.add(
                                                    Table(
                                                        KindOfFieldName = __TypeKind.LIST.toString(),
                                                        isKindOfFieldNameOptional = false,

                                                        typeName = elementIntypes.name,
                                                        kindOfFieldType = kind3.toString(),
                                                        isKindOfFieldTypeOptional = false,
                                                        fieldType = elementInInputFields.type.ofType.ofType.ofType.name,
                                                        fieldName = elementInInputFields.name
                                                    )
                                                )
                                            } else
                                            //TODO: This portion will generate GC errors, fix me.
                                                if (kind3 == __TypeKind.INPUT_OBJECT) {
                                                    state.tempArgsTables.add(
                                                        Table(
                                                            KindOfFieldName = __TypeKind.LIST.toString(),
                                                            isKindOfFieldNameOptional = false,

                                                            typeName = elementIntypes.name,
                                                            kindOfFieldType = kind3.toString(),
                                                            isKindOfFieldTypeOptional = false,
                                                            fieldType = elementInInputFields.type.ofType.ofType.ofType.name,
                                                            fieldName = elementInInputFields.name
                                                        )
                                                    )
                                                }
                                    }
                                } else {
                                    if (kind1 == __TypeKind.SCALAR || kind1 == __TypeKind.ENUM) { // non-optional scalar or enum
                                        if (kind1 == __TypeKind.ENUM) {
                                            val enumElement: MutableList<String> =
                                                collectEnumElementsInTable(
                                                    schemaObj,
                                                    elementInInputFields.type.ofType.name
                                                )
                                            state.tempArgsTables.add(
                                                Table(
                                                    typeName = elementIntypes.name,
                                                    kindOfFieldType = kind1.toString(),
                                                    isKindOfFieldTypeOptional = false,
                                                    fieldType = elementInInputFields.type.ofType.name,
                                                    fieldName = elementInInputFields.name,

                                                    enumValues = enumElement
                                                )
                                            )
                                        } else
                                            state.tempArgsTables.add(
                                                Table(
                                                    typeName = elementIntypes.name,
                                                    kindOfFieldType = kind1.toString(),
                                                    isKindOfFieldTypeOptional = false,
                                                    fieldType = elementInInputFields.type.ofType.name,
                                                    fieldName = elementInInputFields.name
                                                )
                                            )
                                    }

                                }
                            } else
                            //TODO: This portion will generate GC errors, fix me.
                                if (kind0 == __TypeKind.LIST) {

                                    if (kind1 == __TypeKind.NON_NULL) {
                                        if (kind2 == __TypeKind.INPUT_OBJECT) {
                                            state.tempArgsTables.add(
                                                Table(
                                                    KindOfFieldName = __TypeKind.LIST.toString(),
                                                    isKindOfFieldNameOptional = true,

                                                    typeName = elementIntypes.name,
                                                    kindOfFieldType = kind2.toString(),
                                                    isKindOfFieldTypeOptional = false,
                                                    fieldType = elementInInputFields.type.ofType.ofType.name,
                                                    fieldName = elementInInputFields.name,
                                                )
                                            )
                                        }
                                    }
                                } else
                                //TODO: This portion will generate GC errors, fix me.
                                    if (kind0 == __TypeKind.INPUT_OBJECT) {
                                        state.tempArgsTables.add(
                                            Table(
                                                typeName = elementIntypes.name,
                                                kindOfFieldType = kind0.toString(),
                                                isKindOfFieldTypeOptional = true,
                                                fieldType = elementInInputFields.type.name,
                                                fieldName = elementInInputFields.name,
                                            )
                                        )
                                    } else
                                        if (kind0 == __TypeKind.SCALAR || kind0 == __TypeKind.ENUM) {// optional scalar or enum
                                            if (kind0 == __TypeKind.ENUM) {
                                                val enumElement: MutableList<String> =
                                                    collectEnumElementsInTable(
                                                        schemaObj,
                                                        elementInInputFields.type.name
                                                    )
                                                state.tempArgsTables.add(
                                                    Table(
                                                        typeName = elementIntypes.name,
                                                        kindOfFieldType = kind0.toString(),
                                                        isKindOfFieldTypeOptional = true,
                                                        fieldType = elementInInputFields.type.name,
                                                        fieldName = elementInInputFields.name,

                                                        enumValues = enumElement
                                                    )
                                                )
                                            } else
                                                state.tempArgsTables.add(
                                                    Table(
                                                        typeName = elementIntypes.name,
                                                        kindOfFieldType = kind0.toString(),
                                                        isKindOfFieldTypeOptional = true,
                                                        fieldType = elementInInputFields.type.name,
                                                        fieldName = elementInInputFields.name
                                                    )
                                                )
                                        }
                        }
    }

    private fun quadKinds(elementInfields: __Field): List<__TypeKind?> {
        return listOf(
            elementInfields?.type?.kind,
            elementInfields?.type?.ofType?.kind,
            elementInfields?.type?.ofType?.ofType?.kind,
            elementInfields?.type?.ofType?.ofType?.ofType?.kind
        )
    }

    private fun quadKindsInInputs(elementInfields: InputValue):List<__TypeKind?> {
        return listOf(
            elementInfields?.type?.kind,
            elementInfields?.type?.ofType?.kind,
            elementInfields?.type?.ofType?.ofType?.kind,
            elementInfields?.type?.ofType?.ofType?.ofType?.kind
        )
    }

}