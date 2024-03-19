package org.evomaster.core.sql

import org.evomaster.client.java.controller.api.dto.database.schema.DatabaseType
import org.evomaster.client.java.instrumentation.shared.RegexSharedUtils
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.sql.schema.Column
import org.evomaster.core.sql.schema.ColumnDataType
import org.evomaster.core.sql.schema.ForeignKey
import org.evomaster.core.sql.schema.Table
import org.evomaster.core.parser.RegexHandler
import org.evomaster.core.parser.RegexHandler.createGeneForPostgresLike
import org.evomaster.core.parser.RegexHandler.createGeneForPostgresSimilarTo
import org.evomaster.core.search.gene.*
import org.evomaster.core.search.gene.collection.EnumGene
import org.evomaster.core.search.gene.datetime.DateGene
import org.evomaster.core.search.gene.datetime.DateTimeGene
import org.evomaster.core.search.gene.sql.time.SqlTimeIntervalGene
import org.evomaster.core.search.gene.datetime.TimeGene
import org.evomaster.core.search.gene.sql.geometric.*
import org.evomaster.core.search.gene.network.CidrGene
import org.evomaster.core.search.gene.network.InetGene
import org.evomaster.core.search.gene.network.MacAddrGene
import org.evomaster.core.search.gene.numeric.*
import org.evomaster.core.search.gene.optional.ChoiceGene
import org.evomaster.core.search.gene.optional.NullableGene
import org.evomaster.core.search.gene.regex.RegexGene
import org.evomaster.core.search.gene.sql.*
import org.evomaster.core.search.gene.sql.textsearch.SqlTextSearchQueryGene
import org.evomaster.core.search.gene.sql.textsearch.SqlTextSearchVectorGene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.utils.NumberCalculationUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class SqlActionGeneBuilder {


    private fun getForeignKey(table: Table, column: Column): ForeignKey? {

        //TODO: what if a column is part of more than 1 FK? is that even possible?

        return table.foreignKeys.find { it.sourceColumns.contains(column) }
    }

    fun buildGene(id: Long, table: Table, column: Column): Gene {

        val fk = getForeignKey(table, column)

        var gene = when {

            //TODO handle all constraints and cases
            column.autoIncrement ->
                SqlAutoIncrementGene(column.name)
            fk != null ->
                SqlForeignKeyGene(column.name, id, fk.targetTable, column.nullable)

            else -> when (column.type) {
                // Man: TODO need to check
                ColumnDataType.BIT ->
                    handleBitColumn(column)

                ColumnDataType.VARBIT ->
                    handleBitVaryingColumn(column)

                /**
                 * BOOLEAN(1) is assumed to be a boolean/Boolean field
                 */
                ColumnDataType.BOOLEAN, ColumnDataType.BOOL ->
                    handleBooleanColumn(column)

                /**
                 * TINYINT(3) is assumed to be representing a byte/Byte field
                 */
//                ColumnDataType.TINYINT ->
//                    handleTinyIntColumn(column)

                /**
                 * SMALLINT(5) is assumed as a short/Short field
                 */
//                ColumnDataType.INT2, ColumnDataType.SMALLINT ->
//                    handleSmallIntColumn(column)

                /**
                 * CHAR(255) is assumed to be a char/Character field.
                 * A StringGene of length 1 is used to represent the data.
                 *
                 */
                ColumnDataType.CHAR,
                ColumnDataType.CHARACTER,
                ColumnDataType.CHARACTER_LARGE_OBJECT ->
                    handleCharColumn(column)

                /**
                 * TINYINT(3) is assumed to be representing a byte/Byte field
                 * INT2/SMALLINT(5) is assumed as a short/Short field
                 * INT4/INTEGER(10) is a int/Integer field
                 */
                ColumnDataType.TINYINT, ColumnDataType.INT2, ColumnDataType.SMALLINT, ColumnDataType.INT, ColumnDataType.INT4, ColumnDataType.INTEGER, ColumnDataType.SERIAL, ColumnDataType.MEDIUMINT ->
                    handleIntegerColumn(column)

                /**
                 * BIGINT(19) is a long/Long field
                 */
                ColumnDataType.INT8, ColumnDataType.BIGINT, ColumnDataType.BIGSERIAL ->
                    handleBigIntColumn(column)

                /**
                 * DOUBLE(17) is assumed to be a double/Double field
                 *
                 */

                ColumnDataType.DOUBLE,
                ColumnDataType.DOUBLE_PRECISION ->
                    handleDoubleColumn(column)

                /**
                 * VARCHAR(N) is assumed to be a String with a maximum length of N.
                 * N could be as large as Integer.MAX_VALUE
                 */
                ColumnDataType.TINYTEXT,
                ColumnDataType.TEXT,
                ColumnDataType.LONGTEXT,
                ColumnDataType.VARCHAR,
                ColumnDataType.CHARACTER_VARYING,
                ColumnDataType.VARCHAR_IGNORECASE,
                ColumnDataType.CLOB,
                ColumnDataType.MEDIUMTEXT,
                ColumnDataType.LONGBLOB,
                ColumnDataType.MEDIUMBLOB,
                ColumnDataType.TINYBLOB ->
                    handleTextColumn(column)

                //TODO normal TIME, and add tests for it. this is just a quick workaround for patio-api
                ColumnDataType.TIME ->
                    buildSqlTimeGene(column)

                ColumnDataType.TIMETZ,
                ColumnDataType.TIME_WITH_TIME_ZONE ->
                    buildSqlTimeWithTimeZoneGene(column)


                /**
                 * TIMESTAMP is assumed to be a Date field
                 */
                ColumnDataType.TIMESTAMP,
                ColumnDataType.TIMESTAMPTZ,
                ColumnDataType.TIMESTAMP_WITH_TIME_ZONE ->
                    handleTimestampColumn(column)

                /**
                 * DATE is a date without time of day
                 */
                ColumnDataType.DATE ->
                    DateGene(column.name)

                /**
                 * TODO need to check with Andrea regarding fsp which is the fractional seconds precision
                 *
                 * see https://dev.mysql.com/doc/refman/8.0/en/date-and-time-type-syntax.html
                 */
                ColumnDataType.DATETIME ->
                    DateTimeGene(column.name)


                ColumnDataType.YEAR ->
                    handleYearColumn(column)

                /**
                 * Could be any kind of binary data... so let's just use a string,
                 * which also simplifies when needing generate the test files
                 */
                ColumnDataType.BLOB,
                ColumnDataType.BINARY_LARGE_OBJECT ->
                    handleBLOBColumn(column)

                ColumnDataType.BINARY ->
                    handleMySqlBinaryColumn(column)

                ColumnDataType.BINARY_VARYING,
                ColumnDataType.VARBINARY,
                ColumnDataType.JAVA_OBJECT ->
                    handleMySqlVarBinaryColumn(column)


                ColumnDataType.BYTEA ->
                    handlePostgresBinaryStringColumn(column)


                ColumnDataType.FLOAT,
                ColumnDataType.REAL,
                ColumnDataType.FLOAT4 ->
                    handleFloatColumn(column, MIN_FLOAT4_VALUE, MAX_FLOAT4_VALUE)

                ColumnDataType.FLOAT8 ->
                    handleFloatColumn(column, MIN_FLOAT8_VALUE, MAX_FLOAT8_VALUE)

                ColumnDataType.DEC,
                ColumnDataType.DECIMAL,
                ColumnDataType.NUMERIC ->
                    handleDecimalColumn(column)

                /**
                 * Postgres UUID column type
                 */
                ColumnDataType.UUID ->
                    UUIDGene(column.name)

                /**
                 * Postgres JSONB column type
                 */
                ColumnDataType.JSON, ColumnDataType.JSONB ->
                    SqlJSONGene(column.name)

                /**
                 * PostgreSQL XML column type
                 */
                ColumnDataType.XML ->
                    SqlXMLGene(column.name)

                ColumnDataType.ENUM ->
                    handleEnumColumn(column)

                ColumnDataType.MONEY ->
                    handleMoneyColumn(column)

                ColumnDataType.BPCHAR ->
                    handleTextColumn(column, isFixedLength = true)

                ColumnDataType.INTERVAL ->
                    SqlTimeIntervalGene(column.name)

                /*
                 * MySQL and PostgreSQL Point column data type
                 */
                ColumnDataType.POINT ->
                    buildSqlPointGene(column)

                /*
                 * PostgreSQL LINE Column data type
                 */
                ColumnDataType.LINE ->
                    SqlLineGene(column.name)

                /*
                 * PostgreSQL LSEG (line segment) column data type
                 */
                ColumnDataType.LSEG ->
                    SqlLineSegmentGene(column.name)

                /*
                 * PostgreSQL BOX column data type
                 */
                ColumnDataType.BOX ->
                    SqlBoxGene(column.name)

                /*
                 * MySQL,H2 LINESTRING and PostgreSQL PATH
                 * column data types
                 */
                ColumnDataType.LINESTRING,
                ColumnDataType.PATH ->
                    buildSqlPathGene(column)

                /**
                 * MySQL and H2 MULTIPOINT
                 * column types
                 */
                ColumnDataType.MULTIPOINT ->
                    buildSqlMultiPointGene(column)

                /**
                 * PostgreSQL and H2 MULTILINESTRING
                 * column types
                 */
                ColumnDataType.MULTILINESTRING ->
                    buildSqlMultiPathGene(column)

                /* MySQL and PostgreSQL POLYGON
                 * column data type
                 */
                ColumnDataType.POLYGON ->
                    buildSqlPolygonGene(column)

                /* MySQL and H2 MULTIPOLYGON
                 * column data type
                   */
                ColumnDataType.MULTIPOLYGON ->
                    buildSqlMultiPolygonGene(column)

                /**
                 * H2 GEOMETRY column data type
                 */
                ColumnDataType.GEOMETRY ->
                    handleSqlGeometry(column)

                /**
                 * H2 GEOMETRYCOLLECTION and MYSQL GEOMCOLLECTION
                 * column data types
                 */
                ColumnDataType.GEOMCOLLECTION,
                ColumnDataType.GEOMETRYCOLLECTION ->
                    handleSqlGeometryCollection(column)


                /*
                 * PostgreSQL CIRCLE column data type
                 */
                ColumnDataType.CIRCLE ->
                    SqlCircleGene(column.name)

                ColumnDataType.CIDR ->
                    CidrGene(column.name)

                ColumnDataType.INET ->
                    InetGene(column.name)

                ColumnDataType.MACADDR ->
                    MacAddrGene(column.name)

                ColumnDataType.MACADDR8 ->
                    MacAddrGene(column.name, numberOfOctets = MacAddrGene.MACADDR8_SIZE)

                ColumnDataType.TSVECTOR ->
                    SqlTextSearchVectorGene(column.name)

                ColumnDataType.TSQUERY ->
                    SqlTextSearchQueryGene(column.name)

                ColumnDataType.JSONPATH ->
                    SqlJSONPathGene(column.name)

                ColumnDataType.INT4RANGE ->
                    buildSqlIntegerRangeGene(column)

                ColumnDataType.INT8RANGE ->
                    buildSqlLongRangeGene(column)

                ColumnDataType.NUMRANGE ->
                    buildSqlFloatRangeGene(column)

                ColumnDataType.DATERANGE ->
                    buildSqlDateRangeGene(column)

                ColumnDataType.TSRANGE, ColumnDataType.TSTZRANGE ->
                    buildSqlTimestampRangeGene(column)

                ColumnDataType.INT4MULTIRANGE ->
                    SqlMultiRangeGene(column.name, template = buildSqlIntegerRangeGene(column))

                ColumnDataType.INT8MULTIRANGE ->
                    SqlMultiRangeGene(column.name, template = buildSqlLongRangeGene(column))

                ColumnDataType.NUMMULTIRANGE ->
                    SqlMultiRangeGene(column.name, template = buildSqlFloatRangeGene(column))

                ColumnDataType.DATEMULTIRANGE ->
                    SqlMultiRangeGene(column.name, template = buildSqlDateRangeGene(column))

                ColumnDataType.TSMULTIRANGE, ColumnDataType.TSTZMULTIRANGE ->
                    SqlMultiRangeGene(column.name, template = buildSqlTimestampRangeGene(column))

                ColumnDataType.PG_LSN ->
                    SqlLogSeqNumberGene(column.name)

                ColumnDataType.COMPOSITE_TYPE ->
                    handleCompositeColumn(id, table, column)

                ColumnDataType.OID,
                ColumnDataType.REGCLASS,
                ColumnDataType.REGCOLLATION,
                ColumnDataType.REGCONFIG,
                ColumnDataType.REGDICTIONARY,
                ColumnDataType.REGNAMESPACE,
                ColumnDataType.REGOPER,
                ColumnDataType.REGOPERATOR,
                ColumnDataType.REGPROC,
                ColumnDataType.REGPROCEDURE,
                ColumnDataType.REGROLE,
                ColumnDataType.REGTYPE ->
                    handleObjectIdentifierType(column.name)


                else -> throw IllegalArgumentException("Cannot handle: $column.")
            }

        }

        if (column.primaryKey) {
            gene = SqlPrimaryKeyGene(column.name, table.name, gene, id)
        }

        if (column.nullable && fk == null) {
            //FKs handle nullability in their own custom way
            gene = NullableGene(column.name, gene, true, "NULL")
        }

        if (column.dimension > 0) {
            gene = SqlMultidimensionalArrayGene(column.name,
                    databaseType = column.databaseType,
                    template = gene,
                    numberOfDimensions = column.dimension)
        }
        return gene
    }

    private fun handleSqlGeometry(column: Column): ChoiceGene<*> {
        return ChoiceGene(name = column.name,
                listOf(buildSqlPointGene(column),
                        buildSqlMultiPointGene(column),
                        buildSqlPathGene(column),
                        buildSqlMultiPathGene(column),
                        buildSqlPolygonGene(column),
                        buildSqlMultiPolygonGene(column)))
    }

    private fun buildSqlPointGene(column: Column) =
            SqlPointGene(column.name, databaseType = column.databaseType)

    private fun buildSqlPathGene(column: Column) =
            SqlPathGene(column.name, databaseType = column.databaseType)

    private fun buildSqlMultiPointGene(column: Column) =
            SqlMultiPointGene(column.name, databaseType = column.databaseType)

    private fun buildSqlMultiPathGene(column: Column) =
            SqlMultiPathGene(column.name, databaseType = column.databaseType)

    private fun buildSqlMultiPolygonGene(column: Column) =
            SqlMultiPolygonGene(column.name, databaseType = column.databaseType)

    private fun handleSqlGeometryCollection(column: Column) =
            SqlGeometryCollectionGene(column.name,
                    databaseType = column.databaseType,
                    template = handleSqlGeometry(column))

    private fun buildSqlPolygonGene(column: Column): SqlPolygonGene {
        return when (column.databaseType) {
            /*
             * TODO: Uncomment this option when the [isValid()]
             * method is ensured after each mutation of the
             * children of the SqlPolygonGene.
             */
            DatabaseType.MYSQL -> {
                SqlPolygonGene(column.name, minLengthOfPolygonRing = 3, onlyNonIntersectingPolygons = true, databaseType = column.databaseType)
            }
            DatabaseType.H2 -> {
                SqlPolygonGene(column.name, minLengthOfPolygonRing = 3, onlyNonIntersectingPolygons = false, databaseType = column.databaseType)
            }
            DatabaseType.POSTGRES -> {
                SqlPolygonGene(column.name, minLengthOfPolygonRing = 2, onlyNonIntersectingPolygons = false, databaseType = column.databaseType)
            }
            else -> {
                throw IllegalArgumentException("Must define minLengthOfPolygonRing for database ${column.databaseType}")
            }
        }
    }


    private fun buildSqlTimestampRangeGene(column: Column) =
            SqlRangeGene(column.name, template = buildSqlTimestampGene("bound", databaseType = column.databaseType))

    private fun buildSqlDateRangeGene(column: Column) =
            SqlRangeGene(column.name, template = DateGene("bound"))

    private fun buildSqlFloatRangeGene(column: Column) =
            SqlRangeGene(column.name, template = FloatGene("bound"))

    private fun buildSqlLongRangeGene(column: Column) =
            SqlRangeGene(column.name, template = LongGene("bound"))

    private fun buildSqlIntegerRangeGene(column: Column) =
            SqlRangeGene(column.name, template = IntegerGene("bound"))

    /*
        https://dev.mysql.com/doc/refman/8.0/en/year.html
     */
    private fun handleYearColumn(column: Column): Gene {
        // Year(2) is not supported by mysql 8.0
        if (column.size == 2)
            return IntegerGene(column.name, 16, min = 0, max = 99)

        return IntegerGene(column.name, 2016, min = 1901, max = 2155)
    }

    private fun handleEnumColumn(column: Column): Gene {
        return EnumGene(name = column.name, data = column.enumValuesAsStrings ?: listOf())
    }

    private fun handleBigIntColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(column.name, column.enumValuesAsStrings.map { it.toLong() })
        } else {
            /*
                TODO might need to use ULong to handle unsigned long
                https://dev.mysql.com/doc/refman/8.0/en/integer-types.html

                Man: TODO need to check whether to update this with BigIntegerGene
             */
            val min: Long? = if (column.isUnsigned) 0 else null
            val max: Long = Long.MAX_VALUE
            LongGene(column.name,
                min = column.lowerBound ?: min,
                max = column.upperBound ?: max)
        }
    }

    private fun handleIntegerColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(column.name, column.enumValuesAsStrings.map { it.toInt() })
        } else {

            if ((column.type == ColumnDataType.INT4
                            || column.type == ColumnDataType.INT
                            || column.type == ColumnDataType.INTEGER) && column.isUnsigned
            ) {
                LongGene(column.name, min = 0L, max = 4294967295L)
            } else {
                val min = when {
                    column.isUnsigned -> 0
                    column.type == ColumnDataType.TINYINT -> Byte.MIN_VALUE.toInt()
                    column.type == ColumnDataType.SMALLINT || column.type == ColumnDataType.INT2 -> Short.MIN_VALUE.toInt()
                    column.type == ColumnDataType.MEDIUMINT -> -8388608
                    else -> Int.MIN_VALUE
                }

                val max = when (column.type) {
                    ColumnDataType.TINYINT -> if (column.isUnsigned) 255 else Byte.MAX_VALUE.toInt()
                    ColumnDataType.SMALLINT, ColumnDataType.INT2 -> if (column.isUnsigned) 65535 else Short.MAX_VALUE.toInt()
                    ColumnDataType.MEDIUMINT -> if (column.isUnsigned) 16777215 else 8388607
                    else -> Int.MAX_VALUE
                }

                IntegerGene(
                        column.name,
                        min = column.lowerBound?.toInt() ?: min,
                        max = column.upperBound?.toInt() ?: max
                )
            }
        }
    }

    private fun handleCharColumn(column: Column): Gene {
        //  TODO How to discover if it is a char or a char[] of 255 elements?
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings)
        } else {
            val minLength: Int
            val maxLength: Int
            when (column.databaseType) {
                DatabaseType.H2 -> {
                    minLength = column.size
                    maxLength = column.size
                }
                else -> {
                    minLength = 0
                    maxLength = 1
                }
            }
            StringGene(name = column.name, value = "f", minLength = minLength, maxLength = maxLength)
        }
    }

    private fun handleDoubleColumn(column: Column): Gene {
        // TODO How to discover if the source field is a float/Float field?
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings.map { it.toDouble() })
        } else {
            DoubleGene(column.name)
        }
    }

    private fun handleMySqlBinaryColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings)
        } else {
            SqlBinaryStringGene(name = column.name,
                    minSize = column.size,
                    maxSize = column.size,
                    databaseType = column.databaseType)
        }
    }

    private fun handleMySqlVarBinaryColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings)
        } else {
            SqlBinaryStringGene(name = column.name,
                    minSize = 0,
                    maxSize = column.size,
                    databaseType = column.databaseType)
        }
    }

    private fun handlePostgresBinaryStringColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings)
        } else {
            SqlBinaryStringGene(name = column.name,
                    databaseType = column.databaseType)
        }
    }

    private fun handleFloatColumn(column: Column, minValue: Double, maxValue: Double): Gene {
        /**
         * REAL is identical to the floating point statement float(24).
         * TODO How to discover if the source field is a float/Float field?
         */
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings.map { it.toDouble() })

        } else {
            DoubleGene(column.name, min = minValue, max = maxValue)
        }
    }

    @Deprecated("replaced by handleIntegerColumn, now all numeric types resulting in IntegerGene would by handled in handleIntegerColumn")
    private fun handleSmallIntColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            if (column.enumValuesAsStrings.isEmpty()) {
                throw IllegalArgumentException("the list of enumerated values cannot be empty")
            } else {
                EnumGene(column.name, column.enumValuesAsStrings.map { it.toInt() })
            }
        } else {
            IntegerGene(
                    column.name,
                    min = column.lowerBound?.toInt() ?: Short.MIN_VALUE.toInt(),
                    max = column.upperBound?.toInt() ?: Short.MAX_VALUE.toInt()
            )
        }
    }

    @Deprecated("replaced by handleIntegerColumn, now all numeric types resulting in IntegerGene would by handled in handleIntegerColumn")
    private fun handleTinyIntColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            if (column.enumValuesAsStrings.isEmpty()) {
                throw IllegalArgumentException("the list of enumerated values cannot be empty")
            } else {
                EnumGene(column.name, column.enumValuesAsStrings.map { it.toInt() })
            }
        } else {
            IntegerGene(
                    column.name,
                    min = column.lowerBound?.toInt() ?: Byte.MIN_VALUE.toInt(),
                    max = column.upperBound?.toInt() ?: Byte.MAX_VALUE.toInt()
            )
        }
    }

    private fun handleTextColumn(column: Column, isFixedLength: Boolean = false): Gene {
        return if (column.enumValuesAsStrings != null) {
            if (column.enumValuesAsStrings.isEmpty()) {
                throw IllegalArgumentException("the list of enumerated values cannot be empty")
            } else {
                EnumGene(name = column.name, data = column.enumValuesAsStrings)
            }
        } else {

            val numberOfRegexPatterns = (column.similarToPatterns?.size ?: 0) +  (column.likePatterns?.size ?:0) + if (column.javaRegExPattern == null ) 0 else 1

            if (!column.similarToPatterns.isNullOrEmpty()) {
                val columnName = column.name
                val similarToPattern: String = column.similarToPatterns[0]
                if (numberOfRegexPatterns>1) {
                    /**
                     * TODO Handle a conjunction of Regex patterns
                     */
                    log.warn("Handling only a regex pattern for (${column.name}). Using similar to pattern: ${similarToPattern}}")
                }
                buildSimilarToRegexGene(columnName, similarToPattern, databaseType = column.databaseType)
            } else if (!column.likePatterns.isNullOrEmpty()) {
                val columnName = column.name
                val likePattern = column.likePatterns[0]
                if (numberOfRegexPatterns>1) {
                    /**
                     * TODO Handle a conjunction of Regex patterns
                     */
                    LoggingUtil.uniqueWarn(log, "Handling only a regex pattern for (${column.name}). Using like pattern: ${likePattern}}")
                }
                buildLikeRegexGene(columnName, likePattern, databaseType = column.databaseType)
            } else if (column.javaRegExPattern != null) {

                try {
                    buildJavaRegexGene(column.name, column.javaRegExPattern)
                } catch (e: Exception){
                    LoggingUtil.uniqueWarn(log, "Failed to handle regex: ${column.javaRegExPattern}")
                    buildStringGene(isFixedLength, column)
                }
                /*
                    TODO in those cases of regex, shouldn't still check for size constraints?
                 */

            } else {
                buildStringGene(isFixedLength, column)
            }
        }
    }

    private fun buildStringGene(
        isFixedLength: Boolean,
        column: Column,
    ): StringGene {
        val columnMinLength = if (isFixedLength) {
            column.size
        } else {
            if (column.minSize != null && column.minSize > 0) {
                column.minSize
            } else if (column.isNotBlank == true) {
                1
            } else {
                0
            }
        }
        val columnMaxLength = if (column.maxSize != null) {
            minOf(column.maxSize, column.size)
        } else {
            column.size
        }
        return StringGene(name = column.name, minLength = columnMinLength, maxLength = columnMaxLength)
    }

    private fun buildJavaRegexGene(name: String, javaRegExPattern: String): RegexGene {
        val fullMatchRegex = RegexSharedUtils.forceFullMatch(javaRegExPattern)
        val disjunctionRxGenes = RegexHandler.createGeneForJVM(fullMatchRegex).disjunctions
        return RegexGene(name, disjunctions = disjunctionRxGenes, "${RegexGene.JAVA_REGEX_PREFIX}${fullMatchRegex}")
    }

    /**
     * Builds a RegexGene using a name and a list of LIKE patterns.
     * The resulting gene is a disjunction of the given patterns
     *
     * TODO need to handle NOT and ILIKE
     */
    fun buildLikeRegexGene(geneName: String, likePattern: String, databaseType: DatabaseType): RegexGene {
        return when (databaseType) {
            DatabaseType.POSTGRES, //https://www.postgresqltutorial.com/postgresql-tutorial/postgresql-like/
            DatabaseType.H2, // http://www.h2database.com/html/grammar.html#like_predicate_right_hand_side
            DatabaseType.MYSQL -> {
                buildPostgresMySQLLikeRegexGene(geneName, likePattern)
            }
            //TODO: support other database SIMILAR_TO check expressions
            else -> throw UnsupportedOperationException(
                    "Must implement LIKE expressions for database %s".format(
                            databaseType
                    )
            )
        }
    }

    private fun buildPostgresMySQLLikeRegexGene(geneName: String, likePattern: String): RegexGene {
        val disjunctionRxGenes = createGeneForPostgresLike(likePattern).disjunctions
        return RegexGene(geneName, disjunctions = disjunctionRxGenes, "${RegexGene.DATABASE_REGEX_PREFIX}${likePattern}")
    }


    /**
     * Builds a RegexGene using a name and a SIMILAR_TO pattern.
     */
    fun buildSimilarToRegexGene(
            geneName: String,
            similarToPattern: String,
            databaseType: DatabaseType
    ): RegexGene {
        return when(databaseType) {
             DatabaseType.POSTGRES,
             DatabaseType.H2 -> buildPostgresSimilarToRegexGene(geneName, similarToPattern)
            //TODO: support other database SIMILAR_TO check expressions
            else -> throw UnsupportedOperationException(
                    "Must implement similarTo expressions for database %s".format(
                            databaseType
                    )
            )
        }
    }

    private fun buildPostgresSimilarToRegexGene(geneName: String, similarToPattern: String): RegexGene {
        val regexGene = createGeneForPostgresSimilarTo(similarToPattern)
        return RegexGene(geneName, disjunctions = regexGene.disjunctions, "${RegexGene.DATABASE_REGEX_PREFIX}${similarToPattern}")
    }

    private fun buildSqlTimeWithTimeZoneGene(column: Column): TimeGene {
        return TimeGene(
                column.name,
                hour = IntegerGene("hour", 0, 0, 23),
                minute = IntegerGene("minute", 0, 0, 59),
                second = IntegerGene("second", 0, 0, 59),
                timeGeneFormat = TimeGene.TimeGeneFormat.TIME_WITH_MILLISECONDS
        )
    }


    private fun buildSqlTimeGene(column: Column): TimeGene {
        return TimeGene(
                column.name,
                hour = IntegerGene("hour", 0, 0, 23),
                minute = IntegerGene("minute", 0, 0, 59),
                second = IntegerGene("second", 0, 0, 59),
                timeGeneFormat = TimeGene.TimeGeneFormat.ISO_LOCAL_DATE_FORMAT
        )
    }

    fun buildSqlTimestampGene(name: String, databaseType: DatabaseType = DatabaseType.H2): DateTimeGene {
        val minYear: Int
        val maxYear: Int
        when (databaseType) {
            DatabaseType.MYSQL -> {
                minYear = 1970
                maxYear = 2037
            }
            else -> {
                minYear = 1900
                maxYear = 2100
            }
        }
        return DateTimeGene(
                name = name,
                date = DateGene(
                        "date",
                        year = IntegerGene("year", 2016, minYear, maxYear),
                        month = IntegerGene("month", 3, 1, 12),
                        day = IntegerGene("day", 12, 1, 31),
                        onlyValidDates = true
                ),
                time = TimeGene(
                        "time",
                        hour = IntegerGene("hour", 0, 0, 23),
                        minute = IntegerGene("minute", 0, 0, 59),
                        second = IntegerGene("second", 0, 0, 59)
                ),
                dateTimeGeneFormat = DateTimeGene.DateTimeGeneFormat.DEFAULT_DATE_TIME
        )

    }

    private fun handleTimestampColumn(column: Column): DateTimeGene {
        if (column.enumValuesAsStrings != null) {
            throw RuntimeException("Unsupported enum in TIMESTAMP. Please implement")
        } else {
            return buildSqlTimestampGene(column.name, databaseType = column.databaseType)
        }
    }

    private fun handleCLOBColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings)

        } else {
            StringGene(name = column.name, minLength = 0, maxLength = column.size)
        }
    }

    private fun handleBLOBColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings)
        } else {
            StringGene(name = column.name, minLength = 0, maxLength = 8)
        }
    }

    private fun handleRealColumn(column: Column): Gene {
        /**
         * REAL is identical to the floating point statement float(24).
         * TODO How to discover if the source field is a float/Float field?
         */
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings.map { it.toDouble() })

        } else {
            DoubleGene(column.name)
        }
    }

    private fun handleDecimalColumn(column: Column): Gene {
        /**
         * TODO: DECIMAL precision is lower than a float gene
         */
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings.map { it.toFloat() })
        } else {

            if (column.scale != null && column.scale >= 0) {
                /*
                    set precision and boundary for DECIMAL
                    https://dev.mysql.com/doc/refman/8.0/en/fixed-point-types.html

                    for mysql, precision is [1, 65] (default 10), and scale is [0, 30] (default 0)
                    different db might have different range, then do not validate the range for the moment
                 */
                val range = NumberCalculationUtil.boundaryDecimal(column.size, column.scale)
                BigDecimalGene(
                        column.name,
                        min = if (column.isUnsigned) BigDecimal.ZERO.setScale(column.scale) else range.first,
                        max = range.second,
                        precision = column.size,
                        scale = column.scale
                )
            } else {
                if (column.scale == null) {
                    FloatGene(column.name)
                } else {
                    /*
                        TO check
                        with CompositeTypesTest for postgres,
                        the value of precision and scale is -1, may need to check with the authors
                     */
                    log.warn("invalid scale value (${column.scale}) for decimal, and it should not be less than 0")
                    if (column.size <= 0) {
                        log.warn("invalid precision value (${column.size}) for decimal, and it should not be less than 1")
                        FloatGene(column.name)
                    } else {
                        // for mysql, set the scale with default value 0 if it is invalid
                        BigDecimalGene(column.name, precision = column.size, scale = 0)
                    }
                }
            }
        }
    }

    private fun handleMoneyColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(name = column.name, data = column.enumValuesAsStrings.map { it.toFloat() })
        } else {
            val MONEY_COLUMN_PRECISION = 2
            val MONEY_COLUMN_SIZE = 8
            val range = NumberCalculationUtil.boundaryDecimal(MONEY_COLUMN_SIZE, MONEY_COLUMN_PRECISION)

            BigDecimalGene(
                    column.name,
                    min = range.first,
                    max = range.second,
                    precision = MONEY_COLUMN_SIZE,
                    scale = MONEY_COLUMN_PRECISION
            )
        }
    }


    private fun handleBooleanColumn(column: Column): Gene {
        return if (column.enumValuesAsStrings != null) {
            checkNotEmpty(column.enumValuesAsStrings)
            EnumGene(column.name, column.enumValuesAsStrings.map { it.toBoolean() })
        } else {
            BooleanGene(column.name)
        }
    }

    private fun handleCompositeColumn(id: Long, table: Table, column: Column): Gene {
        if (column.compositeType == null) {
            throw IllegalArgumentException("Composite column should have a composite type for column ${column.name}")
        }
        val fields = column.compositeType
                .map { t -> buildGene(id, table, t) }
                .toList()
        return SqlCompositeGene(column.name, fields, column.compositeTypeName)
    }

    /**
     * Handle Postgres Object identifier type
     * (https://www.postgresql.org/docs/current/datatype-oid.html) as
     * integers.
     */
    private fun handleObjectIdentifierType(name: String) = IntegerGene(name)


    /**
     * handle bit for mysql
     * https://dev.mysql.com/doc/refman/8.0/en/bit-value-literals.html
     */
    private fun handleBitColumn(column: Column): Gene {
        val gene = SqlBitStringGene(column.name, minSize = column.size, maxSize = column.size)
        return gene
    }

    /**
     * handle bit varying for postgres
     * https://www.postgresql.org/docs/14/datatype-bit.html
     */
    private fun handleBitVaryingColumn(column: Column): Gene {
        return SqlBitStringGene(column.name, minSize = 0, maxSize = column.size)
    }

    companion object {
        /**
         * Throws an exception if the enum values is empty
         * (parameter is non-nullable by definition)
         */
        private fun checkNotEmpty(enumValuesAsStrings: List<String>) {
            if (enumValuesAsStrings.isEmpty()) {
                throw IllegalArgumentException("the list of enumerated values cannot be empty")
            }
        }

        //  the real type has a range of around 1E-37 to 1E+37 with a precision of at least 6 decimal digits
        val MAX_FLOAT4_VALUE: Double = "1E38".toDouble()

        // The double precision type has a range of around 1E-307 to 1E+308 with a precision of at least 15 digits
        val MAX_FLOAT8_VALUE: Double = "1E308".toDouble()

        //  the real type has a range of around 1E-37 to 1E+37 with a precision of at least 6 decimal digits
        val MIN_FLOAT4_VALUE: Double = MAX_FLOAT4_VALUE.unaryMinus()

        // The double precision type has a range of around 1E-307 to 1E+308 with a precision of at least 15 digits
        val MIN_FLOAT8_VALUE: Double = MAX_FLOAT8_VALUE.unaryMinus()

        private val log: Logger = LoggerFactory.getLogger(SqlActionGeneBuilder::class.java)
    }
}
