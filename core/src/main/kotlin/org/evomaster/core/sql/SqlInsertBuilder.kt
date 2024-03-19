package org.evomaster.core.sql

import org.evomaster.client.java.controller.api.dto.database.operations.DataRowDto
import org.evomaster.client.java.controller.api.dto.database.operations.DatabaseCommandDto
import org.evomaster.client.java.controller.api.dto.database.operations.QueryResultDto
import org.evomaster.client.java.controller.api.dto.database.schema.*
import org.evomaster.core.sql.schema.*
import org.evomaster.core.sql.schema.ColumnFactory.createColumnFromDto
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.placeholder.ImmutableDataHolderGene
import org.evomaster.core.search.gene.sql.SqlPrimaryKeyGene
import org.evomaster.dbconstraint.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.evomaster.core.Lazy
import org.evomaster.core.logging.LoggingUtil


class SqlInsertBuilder(
    schemaDto: DbSchemaDto,
    private val dbExecutor: DatabaseExecutor? = null
) {

    /**
     * The counter used to give unique IDs to the DbActions.
     * Should not be negative
     */
    private var counter: Long = 0

    /*
        All the objects here are immutable
     */

    /**
     * Information about tables, indexed by name
     */
    private val tables = mutableMapOf<String, Table>()

    /**
     * Same as table, but possibly with extended info on constraints, derived from analyzing the SUT
     */
    private val extendedTables = mutableMapOf<String, Table>()

    private val compositeTypes = mutableMapOf<String, CompositeType>()

    private val databaseType: DatabaseType

    private val name: String


    companion object {
        private val log: Logger = LoggerFactory.getLogger(SqlInsertBuilder::class.java)

        private const val EMAIL_SIMILAR_TO_PATTERN = "[A-Za-z]{2,}@[A-Za-z]+.[A-Za-z]{2,}"

        /**
         * input = "12345" returns 12345
         * input = "3.14" returns 3
         * input = "42.0" returns 42
         * input = null returns null
         */
        fun getLowerLongBound(input: String?): Long? = input?.toDoubleOrNull()?.toLong() ?: input?.toLongOrNull()

        /**
         * input = "123" returns 123
         * input = "3.14" returns 4
         * input = "42.0" returns 42
         * input = null returns null
         */
        fun getUpperLongBound(input: String?): Long? {
            val decimalValue = input?.toDoubleOrNull()
            return decimalValue?.toLong()?.let { if (decimalValue % 1 != 0.0) it + 1 else it } ?: input?.toLongOrNull()
        }
   }


    init {
        validateInputDTO(schemaDto)

        databaseType = schemaDto.databaseType
        name = schemaDto.name

        // First load all composite types
        for (compositeTypeDto in schemaDto.compositeTypes) {
            val columns = compositeTypeFrom(compositeTypeDto, schemaDto)
            compositeTypes[compositeTypeDto.name] = CompositeType(compositeTypeDto.name, columns)
        }

        val tableToColumns = mutableMapOf<String, MutableSet<Column>>()
        val tableToForeignKeys = mutableMapOf<String, MutableSet<ForeignKey>>()
        val tableToConstraints = mutableMapOf<String, Set<TableConstraint>>()

        // Then load all constraints and columns
        for (tableDto in schemaDto.tables) {

            val tableConstraints = parseTableConstraints(tableDto).toMutableList()
            val columns = generateColumnsFrom(tableDto, tableConstraints, schemaDto)

            tableToConstraints[tableDto.name] = tableConstraints.toSet()
            tableToColumns[tableDto.name] = columns
        }

        // After all columns are loaded, we can load foreign keys
        for (tableDto in schemaDto.tables) {
            tableToForeignKeys[tableDto.name] = calculateForeignKeysFrom(tableDto, tableToColumns)
        }

        // Now we can create the tables
        for (tableDto in schemaDto.tables) {
            val table = Table(
                tableDto.name,
                tableToColumns[tableDto.name]!!,
                tableToForeignKeys[tableDto.name]!!,
                tableToConstraints[tableDto.name]!!
            )
            tables[tableDto.name] = table
        }

        // Setup extended tables
        tables.forEach { (tableName, table) ->
            extendedTables[tableName] = newTableWithExtraConstraints(schemaDto, table)
        }

        validateExtraConstraintsHandle(schemaDto)
    }

    private fun validateExtraConstraintsHandle(schemaDto: DbSchemaDto) {
        schemaDto.extraConstraintDtos.forEach { extraConstraintsDto ->
            val table = tables.values.find { matchJpaName(it.name, extraConstraintsDto.tableName) }
            if (table == null) {
                LoggingUtil.uniqueWarn(
                    log, "Handling of extra constraints failed." +
                            " There is no SQL table called ${extraConstraintsDto.tableName}"
                )
                assert(false)
            } else {
                val k = table.columns.find { matchJpaName(it.name, extraConstraintsDto.columnName) }
                if (k == null) {
                    LoggingUtil.uniqueWarn(
                        log, "Handling of extra constraints failed." +
                                " There is no column called ${extraConstraintsDto.columnName} in SQL table ${table.name}"
                    )

                    //FIXME put back once dealt with ClassAnalyzer
                    //assert(false)
                }
            }
        }
    }

    private fun newTableWithExtraConstraints(
        schemaDto: DbSchemaDto,
        table: Table
    ): Table {
        val extrasForTable = schemaDto.extraConstraintDtos
            .filter { matchJpaName(it.tableName, table.name) }

        val columns = table.columns
            .map { column ->
                val extra = extrasForTable.find { matchJpaName(it.columnName, column.name) }
                if (extra == null) {
                    column // recall immutable
                } else {
                    mergeConstraints(column, extra)
                }
            }.toSet()

        return table.copy(columns = columns)
    }

    private fun calculateForeignKeysFrom(
        tableDto: TableDto,
        tableToColumns: MutableMap<String, MutableSet<Column>>
    ): MutableSet<ForeignKey> {
        val fks = mutableSetOf<ForeignKey>()

        for (fk in tableDto.foreignKeys) {

            tableToColumns[fk.targetTable]
                ?: throw IllegalArgumentException("Foreign key for non-existent table ${fk.targetTable}")

            val sourceColumns = mutableSetOf<Column>()

            for (cname in fk.sourceColumns) {
                // TODO: wrong check, as should be based on targetColumns, when we ll introduce them
                // val c = targetTable.find { it.name.equals(cname, ignoreCase = true) }
                //        ?: throw IllegalArgumentException("Issue in foreign key: table ${f.targetTable} does not have a column called $cname")

                val c = tableToColumns[tableDto.name]!!.find { it.name.equals(cname, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Issue in foreign key: table ${tableDto.name} does not have a column called $cname")

                sourceColumns.add(c)
            }

            fks.add(ForeignKey(sourceColumns, fk.targetTable))
        }
        return fks
    }

    private fun generateColumnsFrom(
        tableDto: TableDto,
        tableConstraints: MutableList<TableConstraint>,
        schemaDto: DbSchemaDto
    ): MutableSet<Column> {
        val columns = mutableSetOf<Column>()

        for (column in tableDto.columns) {

            if (!column.table.equals(tableDto.name, ignoreCase = true)) {
                throw IllegalArgumentException("Column in different table: ${column.table}!=${tableDto.name}")
            }

            val newColumn = createColumnFrom(column, tableConstraints, schemaDto)

            columns.add(newColumn)
        }
        return columns
    }

    private fun createColumnFrom(
        column: ColumnDto,
        tableConstraints: MutableList<TableConstraint>,
        schemaDto: DbSchemaDto
    ): Column {
        val (lowerBoundForColumn: Long?, upperBoundForColumn: Long?) = calculateBounds(tableConstraints, column)
        val enumValuesForColumn: List<String>? = findEnumValuesForColumn(tableConstraints, column, schemaDto)
        val similarToPatternsForColumn: List<String>? = findSimilarToPatternsForColumn(tableConstraints, column)
        val likePatternsForColumn = findLikePatternsForColumn(tableConstraints, column)

        return createColumnFromDto(
            column, lowerBoundForColumn, upperBoundForColumn, enumValuesForColumn,
            similarToPatternsForColumn, likePatternsForColumn, compositeTypes, databaseType
        )
    }

    private fun calculateBounds(
        tableConstraints: MutableList<TableConstraint>,
        column: ColumnDto
    ): Pair<Long?, Long?> {
        var lowerBoundForColumn: Long? = findLowerBound(tableConstraints, column)
        var upperBoundForColumn: Long? = findUpperBound(tableConstraints, column)
        // rangeConstraints can be combined with lower/upper bound constraints
        val pair = findUpperLowerBoundOfRangeConstraints(tableConstraints, column)
        val minRangeValue: Long? = pair.first?.toLong()
        val maxRangeValue: Long? = pair.second?.toLong()

        if (minRangeValue != null) {
            lowerBoundForColumn = maxOf(minRangeValue, lowerBoundForColumn!!)
        }
        if (maxRangeValue != null) {
            upperBoundForColumn = minOf(maxRangeValue, upperBoundForColumn!!)
        }
        return Pair(lowerBoundForColumn, upperBoundForColumn)
    }

    private fun compositeTypeFrom(
        compositeTypeDto: CompositeTypeDto,
        schemaDto: DbSchemaDto
    ): MutableList<Column> {
        val columns = mutableListOf<Column>()
        for (columnDto in compositeTypeDto.columns) {
            val column = ColumnFactory.createColumnFromCompositeTypeDto(
                columnDto = columnDto,
                compositeTypes = schemaDto.compositeTypes,
                databaseType = databaseType
            )
            columns.add(column)
        }
        return columns
    }

    /**
        Here, we need to transform (and validate) the input DTO
        into immutable domain objects
     **/
    private fun validateInputDTO(schemaDto: DbSchemaDto) {

        if (counter < 0) {
            throw IllegalArgumentException("Invalid negative counter: $counter")
        }

        if (schemaDto.databaseType == null) {
            throw IllegalArgumentException("Undefined database type")
        }
        if (schemaDto.name == null) {
            throw IllegalArgumentException("Undefined schema name")
        }
    }

    /**
    Default table naming is a fucking mess in Hibernate/Spring...
    https://www.jpa-buddy.com/blog/hibernate-naming-strategies-jpa-specification-vs-springboot-opinionation/

    converting to snake_case (done in ClassAnalyzer) does not always work...
    for example, seen cases in which ExistingDataEntityX gets transformed into existing_data_entityx
    instead of existing_data_entity_x

    so, if match fails, we try again without _
     */
    private fun matchJpaName(original: String, jpaDefaultMadness: String): Boolean {
        if (original.equals(jpaDefaultMadness, true)) {
            return true
        }
        return original.replace("_", "").equals(jpaDefaultMadness.replace("_", ""), true)
    }

    private fun mergeConstraints(column: Column, extra: ExtraConstraintsDto): Column {
        Lazy.assert { matchJpaName(column.name, extra.columnName) }

        val mergedIsNullable = if (!column.nullable || extra.constraints.isNotBlank ?: false) {
            false
        } else if (extra.constraints.isNullable == null) {
            column.nullable
        } else {
            extra.constraints.isNullable
        }

        val mergedEnumValuesAsStrings = if (column.enumValuesAsStrings.isNullOrEmpty()) {
            extra.constraints.enumValuesAsStrings // take other current is empty
        } else if (extra.constraints.enumValuesAsStrings.isNullOrEmpty()) {
            column.enumValuesAsStrings // no change
        } else {
            /*
                TODO unsure about this one. we still only wants value that are valid, and will not fail the SQL INSERT.
                So, should be subset of column constraints.
                This might mean that business logic (ie JPA) could define fewer valid values, whereas more would make
                little sense. but what if intersection is empty? would that make any sense?
             */
            val intersection = column.enumValuesAsStrings.filter { extra.constraints.enumValuesAsStrings.contains(it) }
            intersection.ifEmpty {
                column.enumValuesAsStrings // unsure on this one
            }
        }

        val mergedLowerBound = listOfNotNull(
            column.lowerBound,
            extra.constraints.minValue,
            extra.constraints.isPositive?.let { if (it) 1 else null },
            extra.constraints.isPositiveOrZero?.let { if (it) 0 else null },
            getLowerLongBound(extra.constraints.decimalMinValue)
        ).maxOrNull()

        val mergedUpperBound = listOfNotNull(
            column.upperBound,
            extra.constraints.maxValue,
            extra.constraints.isNegative?.let { if (it) -1 else null },
            extra.constraints.isNegativeOrZero?.let { if (it) 0 else null },
            getUpperLongBound(extra.constraints.decimalMaxValue)
        ).minOrNull()

        val minSize = extra.constraints.sizeMin

        val maxSize = extra.constraints.sizeMax

        val isNotBlank = extra.constraints.isNotBlank

        val similarToPatterns = (column.similarToPatterns ?: mutableListOf()).toMutableList()
        extra.constraints.isEmail?.let {
            if (it) {
                similarToPatterns.add(EMAIL_SIMILAR_TO_PATTERN)
            }
        }
        val mergedSimilarToPatterns: List<String>? = similarToPatterns.takeIf { it.isNotEmpty() }

        val javaRegExPattern = extra.constraints.patternRegExp


        val size = column.size.coerceAtMost(extra.constraints.digitsInteger?.plus(extra.constraints.digitsFraction ?: 0) ?: column.size)
        val scale = extra.constraints.digitsFraction

        //TODO all other constraints

        return column.copy(
            size = size,
            nullable = mergedIsNullable,
            enumValuesAsStrings = mergedEnumValuesAsStrings,
            lowerBound = mergedLowerBound,
            upperBound = mergedUpperBound,
            similarToPatterns = mergedSimilarToPatterns,
            isNotBlank = isNotBlank,
            minSize = minSize,
            maxSize = maxSize,
            javaRegExPattern = javaRegExPattern,
            scale = scale
        )
    }


    private fun findUpperLowerBoundOfRangeConstraints(
        tableConstraints: MutableList<TableConstraint>,
        c: ColumnDto
    ): Pair<Int?, Int?> {
        val rangeConstraints = filterRangeConstraints(tableConstraints, c.name)
        val minRangeValue: Int?
        val maxRangeValue: Int?
        if (rangeConstraints.isNotEmpty()) {
            minRangeValue = rangeConstraints.map { constr -> constr.minValue }.maxOrNull()!!.toInt()
            maxRangeValue = rangeConstraints.map { constr -> constr.maxValue }.minOrNull()!!.toInt()
        } else {
            minRangeValue = null
            maxRangeValue = null
        }

        tableConstraints.removeAll(rangeConstraints)
        return Pair(minRangeValue, maxRangeValue)
    }

    private fun findSimilarToPatternsForColumn(
        tableConstraints: MutableList<TableConstraint>,
        c: ColumnDto
    ): List<String>? {
        val similarToConstraints = filterSimilarToConstraints(tableConstraints, c.name)
        val similarToPatterns = if (similarToConstraints.isNotEmpty())
            similarToConstraints.map { constr -> constr.pattern }.toList()
        else
            null

        tableConstraints.removeAll(similarToConstraints)
        return similarToPatterns
    }

    private fun findEnumValuesForColumn(
        tableConstraints: MutableList<TableConstraint>,
        c: ColumnDto,
        schemaDto: DbSchemaDto
    ): List<String>? {
        val enumConstraints = filterEnumConstraints(tableConstraints, c.name)
        val enumValuesAsStrings = if (enumConstraints.isNotEmpty())
            enumConstraints
                .map { constr -> constr.valuesAsStrings.toMutableList() }
                .reduce { acc, it -> acc.apply { retainAll(it) } }
        else
            null

        tableConstraints.removeAll(enumConstraints)

        /**
         * We assume that values of the enumerated type declaration
         * are a superset of the values in the constraint (if any).
         * In other words, the disjoint is empty.
         */
        if (c.isEnumeratedType && enumValuesAsStrings.isNullOrEmpty()) {
            return schemaDto.enumeraredTypes.find { it.name.equals(c.type) }!!.values
        } else {
            return enumValuesAsStrings
        }
    }

    private fun findUpperBound(tableConstraints: MutableList<TableConstraint>, c: ColumnDto): Long? {
        val upperBounds = filterUpperBoundConstraints(tableConstraints, c.name)

        val upperBound: Long? = if (upperBounds.isNotEmpty())
            upperBounds.map { constr -> constr.upperBound }.minOrNull()
        else
            null

        tableConstraints.removeAll(upperBounds)
        return upperBound
    }

    private fun findLowerBound(tableConstraints: MutableList<TableConstraint>, c: ColumnDto): Long? {
        val lowerBounds = filterLowerBoundConstraints(tableConstraints, c.name)

        val lowerBound = if (lowerBounds.isNotEmpty())
            lowerBounds.map { constr -> constr.lowerBound }.maxOrNull()
        else
            null

        tableConstraints.removeAll(lowerBounds)
        return lowerBound
    }

    private fun findLikePatternsForColumn(tableConstraints: MutableList<TableConstraint>, c: ColumnDto): List<String>? {
        val likePatterns: List<String>?
        val likeConstraints = filterLikeConstraints(tableConstraints, c.name)
        if (likeConstraints.size > 1)
            throw IllegalArgumentException("cannot handle table with ${likeConstraints.size} LIKE constraints")

        if (likeConstraints.isNotEmpty()) {
            val likeConstraint = likeConstraints.first()
            likePatterns = likeConstraint.patterns
        } else
            likePatterns = null


        tableConstraints.removeAll(likeConstraints)
        return likePatterns
    }

    private fun filterLowerBoundConstraints(
        tableConstraints: List<TableConstraint>,
        columnName: String
    ): List<LowerBoundConstraint> {

         var lowerBounds = tableConstraints
                .asSequence()
            .filterIsInstance<LowerBoundConstraint>()
            .filter { c -> c.columnName.equals(columnName, ignoreCase = true) }
            .toList();

        // Add lowerBounds inside the tableConstraints that are AndConstraints
        // Only do the recursion in AndConstraint, as the OR may not be a bound
        val andConstraints = tableConstraints.filterIsInstance<AndConstraint>();
        for (andConstraint in andConstraints) {
            val lowerBoundsInAndConstraint = filterLowerBoundConstraints(andConstraint.constraintList, columnName);
            lowerBounds = lowerBounds + lowerBoundsInAndConstraint
        }

        return lowerBounds;
    }

    private fun filterUpperBoundConstraints(
        tableConstraints: List<TableConstraint>,
        columnName: String
    ): List<UpperBoundConstraint> {
        var upperBounds = tableConstraints
            .asSequence()
            .filterIsInstance<UpperBoundConstraint>()
            .filter { c -> c.columnName.equals(columnName, ignoreCase = true) }
            .toList()

        // Add upperBounds inside the tableConstraints that are AndConstraints
        // Only do the recursion in AndConstraint, as the OR may not be a bound
        val andConstraints = tableConstraints.filterIsInstance<AndConstraint>();
        for (andConstraint in andConstraints) {
            val upperBoundsInAndConstraint = filterUpperBoundConstraints(andConstraint.constraintList, columnName);
            upperBounds = upperBounds + upperBoundsInAndConstraint
        }

        return upperBounds
    }

    private fun filterSimilarToConstraints(
        tableConstraints: List<TableConstraint>,
        columnName: String
    ): List<SimilarToConstraint> {
        return tableConstraints
            .asSequence()
            .filterIsInstance<SimilarToConstraint>()
            .filter { c -> c.columnName.equals(columnName, ignoreCase = true) }
            .toList()
    }

    private fun filterLikeConstraints(
        tableConstraints: List<TableConstraint>,
        columnName: String
    ): List<LikeConstraint> {
        return tableConstraints
            .asSequence()
            .filterIsInstance<LikeConstraint>()
            .filter { c -> c.columnName.equals(columnName, ignoreCase = true) }
            .toList()
    }




    private fun filterRangeConstraints(
        tableConstraints: List<TableConstraint>,
        columnName: String
    ): List<RangeConstraint> {
        return tableConstraints
            .asSequence()
            .filterIsInstance<RangeConstraint>()
            .filter { c -> c.columnName.equals(columnName, ignoreCase = true) }
            .toList()

    }


    private fun filterEnumConstraints(
        tableConstraints: List<TableConstraint>,
        columnName: String
    ): List<EnumConstraint> {
        return tableConstraints
            .filter { c -> c is EnumConstraint }
            .map { c -> c as EnumConstraint }
            .filter { c -> c.columnName.equals(columnName, ignoreCase = true) }
            .toList()
    }

    private fun getConstraintDatabaseType(currentDatabaseType: DatabaseType): ConstraintDatabaseType {
        return when (currentDatabaseType) {
            DatabaseType.H2 -> ConstraintDatabaseType.H2
            DatabaseType.POSTGRES -> ConstraintDatabaseType.POSTGRES
            DatabaseType.DERBY -> ConstraintDatabaseType.DERBY
            DatabaseType.MYSQL -> ConstraintDatabaseType.MYSQL
            DatabaseType.MARIADB -> ConstraintDatabaseType.MARIADB
            DatabaseType.MS_SQL_SERVER -> ConstraintDatabaseType.MS_SQL_SERVER
            DatabaseType.OTHER -> ConstraintDatabaseType.OTHER
        }
    }

    private fun parseTableConstraints(t: TableDto): List<TableConstraint> {
        val tableConstraints = mutableListOf<TableConstraint>()
        val tableName = t.name
        for (sqlCheckExpression in t.tableCheckExpressions) {
            val builder = TableConstraintBuilder()
            val constraintDatabaseType = getConstraintDatabaseType(this.databaseType)
            val tableConstraint =
                builder.translateToConstraint(tableName, sqlCheckExpression.sqlCheckExpression, constraintDatabaseType)
            tableConstraints.add(tableConstraint)
        }
        return tableConstraints
    }

    /**
     * SQL is not case sensitivity.
     * Therefore, table/column must ignore case sensitivity.
     */
    fun isTable(tableName: String) = tables.keys.any { it.equals(tableName, ignoreCase = true) }

    fun getTable(tableName: String, useExtraConstraints: Boolean): Table {

        val data = if (useExtraConstraints) extendedTables else tables

        /*
         * SQL is not case sensitivity, table/column must ignore case sensitivity.
         */
        val tableNameKey = data.keys.find { tableName.equals(it, ignoreCase = true) }
        return data[tableNameKey] ?: throw IllegalArgumentException("No table called $tableName")

    }

    /**
     * Create a SQL insertion operation into the table called [tableName].
     * Use columns only from [columnNames], to avoid wasting resources in setting
     * non-used data.
     * Note: due to constraints (e.g. non-null), we might create data also for non-specified columns.
     *
     * If the table has non-null foreign keys to other tables, then create an insertion for those
     * as well. This means that more than one action can be returned here.
     *
     * Note: the created actions have default values. You might want to randomize its data before
     * using it.
     *
     * Note: names are case-sensitive, although some DBs are case-insensitive. To make
     * things even harder, there could be a mismatch in casing when inserting and then
     * reading back table/column names from schema :(
     * This is (should not) be a problem when running EM, but can be trickier when writing
     * test cases manually for EM
     */
    fun createSqlInsertionAction(
        tableName: String,
        /**
         * Which columns to create data for. Default is all, ie *.
         * Notice that more columns might be added, eg, to satisfy non-null
         * and PK constraints
         */
        columnNames: Set<String> = setOf("*"),
        /**
         * used to avoid infinite recursion
         */
        history: MutableList<String> = mutableListOf(),
        /**
         *   When adding new insertions due to FK constraints, specify if
         *   should get all columns for those new insertions, or just the minimal
         *   needed to satisfy all the constraints
         */
        forceAll: Boolean = false,
        /**
         *  whether to use extra constraints identified in the business logic
         */
        useExtraSqlDbConstraints: Boolean = false,
        /**
         * whether to enable single insertion for table
         *
         * in order to insert one row to the table,
         * it might need to create its fk tables,
         * and its fk table might have further fk tables as well.
         * eg,
         * D -> B -> A
         * D -> C -> A
         * to insert a row to D,
         * if we do enable single insertion for table,
         * the insertions will ABCD (C and B refer to the same A)
         * otherwise, they will be ABACD
         *
         */
        enableSingleInsertionForTable: Boolean = false
    ): List<SqlAction> {

        // visit current tableName
        history.add(tableName)

        val table = getTable(tableName, useExtraSqlDbConstraints)
        val takeAll = columnNames.contains("*")
        validateColumnNamesInput(takeAll, columnNames, table, tableName)

        // filter only columns that will generate value for
        val selectedColumns = table.columns
            .filter { takeAll || shouldConsiderColumn(columnNames, it) }
            .toSet()

        val insertion = SqlAction(table, selectedColumns, counter++)
        if (log.isTraceEnabled) {
            log.trace("create an insertion which is {} and the counter is {}", insertion.getResolvedName(), counter)
        }

        val actions = mutableListOf(insertion)

        for (fk in table.foreignKeys) {

            val targetTable = fk.targetTable

            // if we have already generated the sql inserts for the target table more than 3 times and all columns are nullable, then skip it
            val maxIter = 3 // TODO: as a configurable parameter in EMConfig?
            val visitedTableCount = history.count { it.equals(targetTable, ignoreCase = true) }
            if (visitedTableCount >= maxIter && fk.sourceColumns.all { it.nullable }) {
                continue
            }
            val targetColumns = if (forceAll) setOf("*") else setOf()

            val targetInsertions = createSqlInsertionAction(
                        targetTable,
                        targetColumns,
                        history,
                        forceAll,
                        useExtraSqlDbConstraints,
                        enableSingleInsertionForTable
                    )

            actions.addAll(0, targetInsertions)
        }
        if (log.isTraceEnabled) {
            log.trace("create insertions and current size is {}", actions.size)
        }

        if (enableSingleInsertionForTable && actions.size > 1) {
            val removed = actions.filterIndexed { index, dbAction ->
                (index > 0 && (index < actions.size - 1 || actions.size == 2)) &&
                actions
                    .subList(0, index - 1)
                    .any { a -> a.table.name.equals(dbAction.table.name, ignoreCase = true) }
            }
            if (removed.isNotEmpty())
                actions.removeAll(removed)
        }

        return actions
    }

    /**
        we need to take primaryKey even if autoIncrement.
        Point is, even if value will be set by DB, and so could skip it,
        we would still need a non-modifiable, non-printable Gene to
        store it, as we can have other Foreign Key genes pointing to it

        // TODO: are there also other constraints to consider?
     **/
    private fun shouldConsiderColumn(
        allColumns: Set<String>,
        column: Column
    ) = (allColumns.any { it.equals(column.name, ignoreCase = true) } || !column.nullable || column.primaryKey)

    private fun validateColumnNamesInput(
        takeAll: Boolean,
        columnNames: Set<String>,
        table: Table,
        tableName: String
    ) {
        if (takeAll && columnNames.size > 1) {
            throw IllegalArgumentException("Invalid column description: more than one entry when using '*'")
        }

        for (cn in columnNames) {
            if (cn != "*" && !table.columns.any { it.name.equals(cn, ignoreCase = true) }) {
                throw IllegalArgumentException("No column called $cn in table $tableName")
            }
        }
    }

    /**
     * Check current state of database.
     * For each row, create a DbAction containing only Primary Keys
     * and immutable data
     */
    fun extractExistingPKs(): List<SqlAction> {

        if (dbExecutor == null) {
            throw IllegalStateException("No Database Executor registered for this object")
        }

        val list = mutableListOf<SqlAction>()

        for (table in tables.values) {

            val pks = table.columns.filter { it.primaryKey }

            if (pks.isEmpty()) {
                /*
                    In some very special cases, it might happen that a table has no defined
                    primary key. It's rare, but it is technically legal.
                    We can just skip those tables.
                 */
                continue
            }

            val sql = formatSelect(pks.map { it.name }, table.name)

            val dto = DatabaseCommandDto()
            dto.command = sql

            val result: QueryResultDto = dbExecutor.executeDatabaseCommandAndGetQueryResults(dto)
                ?: continue

            result.rows.forEach { r ->

                val id = counter++

                val genes = mutableListOf<Gene>()

                for (i in pks.indices) {
                    val pkName = pks[i].name
                    val inQuotes = pks[i].type.shouldBePrintedInQuotes || pks[i].dimension > 0
                    val data = ImmutableDataHolderGene(pkName, r.columnData[i], inQuotes)
                    val pk = SqlPrimaryKeyGene(pkName, table.name, data, id)
                    genes.add(pk)
                }

                val action = SqlAction(table, pks.toSet(), id, genes, true)
                list.add(action)
            }
        }
        list.forEach { it.doInitialize() }

        return list
    }


    /**
     * the method is used to create a select sql based on the specified [pkValues] on [tableName]
     *
     * @param tableName specified the data of table to extract
     * @param pkValues specified the values
     * @param useExtraSqlDbConstraints whether to use extra constraints identified in the business logic
     * @param columnIds specified the columns for the [pkValues].
     *          for the table, there might exist more than one pks, here we allow to define the specific columns for values.
     *          Note that the specified [columnIds] can be empty, then we take all pks by default
     *
     * @return DbAction has all values of columns in the row regarding [pkValues]
     *
     */
    fun extractExistingByCols(
        tableName: String,
        pkValues: DataRowDto,
        useExtraSqlDbConstraints: Boolean,
        columnIds: List<String> = mutableListOf()
    ): SqlAction {

        if (dbExecutor == null) {
            throw IllegalStateException("No Database Executor registered for this object")
        }

        val table = getTable(tableName, useExtraSqlDbConstraints)

        val pks =
            if (columnIds.isNotEmpty()) table.columns.filter { columnIds.contains(it.name) } else table.columns.filter { it.primaryKey }
        val cols = table.columns.toList()

        val row: DataRowDto?
        if (pks.isNotEmpty()) {


            val condition = SQLGenerator.composeAndConditions(
                SQLGenerator.genConditions(
                    pks.map { it.name }.toTypedArray(),
                    pkValues.columnData,
                    table
                )
            )

            val sql = SQLGenerator.genSelect(cols.map { it.name }.toTypedArray(), table, condition)

            val dto = DatabaseCommandDto()
            dto.command = sql

            val result: QueryResultDto = dbExecutor.executeDatabaseCommandAndGetQueryResults(dto)
                ?: throw IllegalArgumentException("rows regarding pks can not be found")
            if (result.rows.size != 1) {
                log.warn("there exist more than one rows (${result.rows.size}) with pkValues $condition")
            }
            row = result.rows.first()
        } else
            row = pkValues


        val id = counter++

        val genes = mutableListOf<Gene>()

        for (i in cols.indices) {

            if (row!!.columnData[i] != "NULL") {

                val colName = cols[i].name
                val inQuotes = cols[i].type.shouldBePrintedInQuotes || cols[i].dimension > 0

                val gene = if (cols[i].primaryKey) {
                    SqlPrimaryKeyGene(
                        colName,
                        table.name,
                        ImmutableDataHolderGene(colName, row.columnData[i], inQuotes),
                        id
                    )
                } else {
                    ImmutableDataHolderGene(colName, row.columnData[i], inQuotes)
                }
                genes.add(gene)
            }
        }

        val db = SqlAction(table, pks.toSet(), id, genes, true)

        db.doInitialize()
        return db
    }


    /**
     * get existing pks in db
     */
    fun extractExistingPKs(dataInDB: MutableMap<String, MutableList<DataRowDto>>) {

        if (dbExecutor == null) {
            throw IllegalStateException("No Database Executor registered for this object")
        }

        dataInDB.clear()

        for (table in tables.values) {
            val pks = table.columns.filter { it.primaryKey }
            val sql = formatSelect(if (pks.isEmpty()) listOf(SQLKey.ALL.key) else pks.map { it.name }, table.name)

            val dto = DatabaseCommandDto()
            dto.command = sql

            val result: QueryResultDto = dbExecutor.executeDatabaseCommandAndGetQueryResults(dto)
                ?: continue
            dataInDB.getOrPut(table.name) { result.rows.map { it }.toMutableList() }
        }
    }


    /**
     * get table info
     */
    fun extractExistingTables(tablesMap: MutableMap<String, Table>? = null) {
        if (tablesMap != null) {
            tablesMap.clear()
            tablesMap.putAll(tables)
        }
    }

    /**
     * extract tables with additional fk tables
     * @param tables to check
     * @param all is a complete set of tables with their fk
     */
    fun extractFkTable(tables: Set<String>, all: MutableSet<String> = mutableSetOf()): Set<String> {
        tables.forEach { t ->
            if (!all.contains(t))
                all.add(t)
            val fk = extractFkTable(t).filterNot { all.contains(it) }.toSet()
            if (fk.isNotEmpty()) {
                extractFkTable(fk, all)
            }
        }
        return all.toSet()
    }

    private fun extractFkTable(tableName: String): Set<String> {
        return tables.filter { t ->
            t.value.foreignKeys.any { f ->
                f.targetTable.equals(
                    tableName,
                    ignoreCase = true
                )
            }
        }.keys
    }


    private fun formatSelect(columnNames: List<String>, tableName: String): String {
        return "SELECT ${columnNames.joinToString(",") { formatNameInSql(it) }} FROM ${formatNameInSql(tableName)}"
    }

    private fun formatNameInSql(name: String): String {
        return when {
            databaseType == DatabaseType.MYSQL || name == SQLKey.ALL.key -> name
            else -> "\"$name\""
        }
    }

    /**
     * get names of all tables
     */
    fun getTableNames() = tables.keys
}
