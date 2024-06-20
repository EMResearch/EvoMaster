package org.evomaster.core.output

import org.evomaster.client.java.controller.api.dto.database.schema.DatabaseType
import org.evomaster.core.EMConfig
import org.evomaster.core.TestUtils
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.sql.SqlActionGeneBuilder
import org.evomaster.core.sql.SqlActionResult
import org.evomaster.core.sql.schema.Column
import org.evomaster.core.sql.schema.ColumnDataType.*
import org.evomaster.core.sql.schema.ForeignKey
import org.evomaster.core.sql.schema.Table
import org.evomaster.core.output.EvaluatedIndividualBuilder.Companion.buildResourceEvaluatedIndividual
import org.evomaster.core.output.service.PartialOracles
import org.evomaster.core.output.service.RestTestCaseWriter
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.rest.*
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.gene.*
import org.evomaster.core.search.gene.datetime.DateGene
import org.evomaster.core.search.gene.sql.SqlAutoIncrementGene
import org.evomaster.core.search.gene.sql.SqlForeignKeyGene
import org.evomaster.core.search.gene.sql.SqlPrimaryKeyGene
import org.evomaster.core.search.gene.UUIDGene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.string.StringGene
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.ws.rs.core.MediaType

class TestCaseWriterTest {
    //TODO: BMR- changed the tests to not use expectationsActive. This may require updating.

    private fun getConfig(format: OutputFormat): EMConfig {
        val config = EMConfig()
        config.outputFormat = format
        config.expectationsActive = false
        config.testTimeout = -1
        return config
    }

    @Test
    fun testEmptyDbInitialization() {


        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(emptyList<SqlAction>().toMutableList())

        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode(test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }

    @Test
    fun testOneAction() {
        val aColumn = Column("aColumn", VARCHAR, 10, databaseType = DatabaseType.H2)

        val aTable = Table("myTable", setOf(aColumn), HashSet<ForeignKey>())

        val id = 0L

        val gene = StringGene(aColumn.name, "stringValue", 0, 12)

        val insertIntoTableAction = SqlAction(aTable, setOf(aColumn), id, mutableListOf(gene))

        val dbInitialization = mutableListOf(insertIntoTableAction)

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(dbInitialization)
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode(test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"myTable\", 0L)")
            indent()
            indent()
            add(".d(\"aColumn\", \"\\\"stringValue\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }




    private fun buildEvaluatedIndividual(dbInitialization: MutableList<SqlAction>): Triple<OutputFormat, String, EvaluatedIndividual<RestIndividual>> {
        val format = OutputFormat.JAVA_JUNIT_4

        val baseUrlOfSut = "baseUrlOfSut"

        val sampleType = SampleType.RANDOM

        val restActions = emptyList<RestCallAction>().toMutableList()

        val individual = RestIndividual(restActions, sampleType, dbInitialization)
        TestUtils.doInitializeIndividualForTesting(individual)

        val fitnessVal = FitnessValue(0.0)

        val results = dbInitialization.map { SqlActionResult(it.getLocalId()).also { it.setInsertExecutionResult(true) } }

        val ei = EvaluatedIndividual(fitnessVal, individual, results)
        return Triple(format, baseUrlOfSut, ei)
    }

    @Test
    fun testTwoAction() {
        val aColumn = Column("aColumn", VARCHAR, 10, databaseType = DatabaseType.H2)

        val aTable = Table("myTable", setOf(aColumn), HashSet<ForeignKey>())


        val gene0 = StringGene(aColumn.name, "stringValue0", 0, 16)

        val insertIntoTableAction0 = SqlAction(aTable, setOf(aColumn), 0L, mutableListOf(gene0))

        val gene1 = StringGene(aColumn.name, "stringValue1", 0, 16)

        val insertIntoTableAction1 = SqlAction(aTable, setOf(aColumn), 1L, mutableListOf(gene1))


        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insertIntoTableAction0, insertIntoTableAction1))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode(test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"myTable\", 0L)")
            indent()
            indent()
            add(".d(\"aColumn\", \"\\\"stringValue0\\\"\")")
            deindent()
            add(".and().insertInto(\"myTable\", 1L)")
            indent()
            add(".d(\"aColumn\", \"\\\"stringValue1\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testTwoColumns() {
        val column0 = Column("Column0", VARCHAR, 10, databaseType = DatabaseType.H2)
        val column1 = Column("Column1", VARCHAR, 10, databaseType = DatabaseType.H2)

        val aTable = Table("myTable", setOf(column0, column1), HashSet<ForeignKey>())

        val id = 0L

        val gene0 = StringGene(column0.name, "stringValue0", 0, 16)
        val gene1 = StringGene(column1.name, "stringValue1", 0, 16)

        val insertIntoTableAction = SqlAction(aTable, setOf(column0, column1), id, mutableListOf(gene0, gene1))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insertIntoTableAction))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode(test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"myTable\", 0L)")
            indent()
            indent()
            add(".d(\"Column0\", \"\\\"stringValue0\\\"\")")
            add(".d(\"Column1\", \"\\\"stringValue1\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testIntegerColumn() {
        val idColumn = Column("Id", INTEGER, 10, primaryKey = false, databaseType = DatabaseType.H2)
        val nameColumn = Column("Name", VARCHAR, 10, primaryKey = false, databaseType = DatabaseType.H2)

        val aTable = Table("myTable", setOf(idColumn, nameColumn), HashSet<ForeignKey>())

        val id = 0L

        val integerGene = IntegerGene(idColumn.name, 42, 0, 50)
        val stringGene = StringGene(nameColumn.name, "nameValue", 0, 10)

        val insertIntoTableAction = SqlAction(aTable, setOf(idColumn, nameColumn), id, listOf(integerGene, stringGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insertIntoTableAction))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode(test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"myTable\", 0L)")
            indent()
            indent()
            add(".d(\"Id\", \"42\")")
            add(".d(\"Name\", \"\\\"nameValue\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testPrimaryKeyColumn() {
        val idColumn = Column("Id", INTEGER, 10, primaryKey = true, databaseType = DatabaseType.H2)
        val nameColumn = Column("Name", VARCHAR, 10, primaryKey = false, databaseType = DatabaseType.H2)

        val aTable = Table("myTable", setOf(idColumn, nameColumn), HashSet<ForeignKey>())


        val id = 0L

        val integerGene = IntegerGene(idColumn.name, 42, 0, 100)
        val primaryKeyGene = SqlPrimaryKeyGene(idColumn.name, "myTable", integerGene, 10)
        val stringGene = StringGene(nameColumn.name, "nameValue", 0, 10)

        val insertIntoTableAction = SqlAction(aTable, setOf(idColumn, nameColumn), id, listOf(primaryKeyGene, stringGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insertIntoTableAction))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode(test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"myTable\", 0L)")
            indent()
            indent()
            add(".d(\"Id\", \"42\")")
            add(".d(\"Name\", \"\\\"nameValue\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testForeignKeyColumn() {
        val idColumn = Column("Id", INTEGER, 10, primaryKey = true, databaseType = DatabaseType.H2)
        val table0 = Table("Table0", setOf(idColumn), HashSet<ForeignKey>())

        val fkColumn = Column("fkId", INTEGER, 10, primaryKey = false, databaseType = DatabaseType.H2)
        val table1 = Table("Table1", setOf(idColumn, fkColumn), HashSet<ForeignKey>())


        val pkGeneUniqueId = 12345L

        val integerGene = IntegerGene(idColumn.name, 42, 0, 100)
        val primaryKeyTable0Gene = SqlPrimaryKeyGene(idColumn.name, "Table0", integerGene, pkGeneUniqueId)
        val primaryKeyTable1Gene = SqlPrimaryKeyGene(idColumn.name, "Table1", integerGene, 10)


        val firstInsertionId = 1001L
        val insertIntoTable0 = SqlAction(table0, setOf(idColumn), firstInsertionId, listOf(primaryKeyTable0Gene))
        val secondInsertionId = 1002L
        val foreignKeyGene = SqlForeignKeyGene(fkColumn.name, secondInsertionId, "Table0", false, uniqueIdOfPrimaryKey = pkGeneUniqueId)

        val insertIntoTable1 = SqlAction(table1, setOf(idColumn, fkColumn), secondInsertionId, listOf(primaryKeyTable1Gene, foreignKeyGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insertIntoTable0.copy() as SqlAction, insertIntoTable1.copy() as SqlAction))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode(test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 1001L)")
            indent()
            indent()
            add(".d(\"Id\", \"42\")")
            deindent()
            add(".and().insertInto(\"Table1\", 1002L)")
            indent()
            add(".d(\"Id\", \"42\")")
            add(".d(\"fkId\", \"42\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testBooleanColumn() {
        val aColumn = Column("aColumn", BOOLEAN, 10, databaseType = DatabaseType.H2)

        val aTable = Table("myTable", setOf(aColumn), HashSet<ForeignKey>())


        val id = 0L

        val gene = BooleanGene(aColumn.name, false)

        val insertIntoTableAction = SqlAction(aTable, setOf(aColumn), id, mutableListOf(gene))

        val dbInitialization = mutableListOf(insertIntoTableAction)

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(dbInitialization)
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode(test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"myTable\", 0L)")
            indent()
            indent()
            add(".d(\"aColumn\", \"false\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testNullableForeignKeyColumn() {
        val idColumn = Column("Id", INTEGER, 10, primaryKey = true, databaseType = DatabaseType.H2)
        val table0 = Table("Table0", setOf(idColumn), HashSet<ForeignKey>())

        val fkColumn = Column("fkId", INTEGER, 10, primaryKey = false, nullable = true, databaseType = DatabaseType.H2)
        val table1 = Table("Table1", setOf(idColumn, fkColumn), HashSet<ForeignKey>())


        val integerGene = IntegerGene(idColumn.name, 42, 0, 100)
        val primaryKeyTable0Gene = SqlPrimaryKeyGene(idColumn.name, "Table0", integerGene, 10)
        val primaryKeyTable1Gene = SqlPrimaryKeyGene(idColumn.name, "Table1", integerGene, 10)


        val firstInsertionId = 1001L
        val insertIntoTable0 = SqlAction(table0, setOf(idColumn), firstInsertionId, listOf(primaryKeyTable0Gene))
        val secondInsertionId = 1002L
        val foreignKeyGene = SqlForeignKeyGene(fkColumn.name, secondInsertionId, "Table0", true, -1L)

        val insertIntoTable1 = SqlAction(table1, setOf(idColumn, fkColumn), secondInsertionId, listOf(primaryKeyTable1Gene, foreignKeyGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insertIntoTable0.copy() as SqlAction, insertIntoTable1.copy() as SqlAction))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 1001L)")
            indent()
            indent()
            add(".d(\"Id\", \"42\")")
            deindent()
            deindent()
            indent()
            add(".and().insertInto(\"Table1\", 1002L)")
            indent()
            add(".d(\"Id\", \"42\")")
            add(".d(\"fkId\", \"NULL\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testTimeStampColumn() {
        val aColumn = Column("aColumn", TIMESTAMP, 10, databaseType = DatabaseType.H2)

        val aTable = Table("myTable", setOf(aColumn), HashSet<ForeignKey>())


        val id = 0L

        val gene = SqlActionGeneBuilder().buildSqlTimestampGene(aColumn.name)

        val insertIntoTableAction = SqlAction(aTable, setOf(aColumn), id, mutableListOf(gene))

        val dbInitialization = mutableListOf(insertIntoTableAction)

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(dbInitialization)
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"myTable\", 0L)")
            indent()
            indent()
            add(".d(\"aColumn\", \"\\\"2016-03-12 00:00:00\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testThreeAction() {
        val aColumn = Column("aColumn", VARCHAR, 10, databaseType = DatabaseType.H2)

        val aTable = Table("myTable", setOf(aColumn), HashSet<ForeignKey>())


        val gene0 = StringGene(aColumn.name, "stringValue0", 0, 16)

        val insertIntoTableAction0 = SqlAction(aTable, setOf(aColumn), 0L, mutableListOf(gene0))

        val gene1 = StringGene(aColumn.name, "stringValue1", 0, 16)

        val insertIntoTableAction1 = SqlAction(aTable, setOf(aColumn), 1L, mutableListOf(gene1))

        val gene2 = StringGene(aColumn.name, "stringValue2", 0, 16)

        val insertIntoTableAction2 = SqlAction(aTable, setOf(aColumn), 2L, mutableListOf(gene2))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insertIntoTableAction0, insertIntoTableAction1, insertIntoTableAction2))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"myTable\", 0L)")
            indent()
            indent()
            add(".d(\"aColumn\", \"\\\"stringValue0\\\"\")")
            deindent()
            add(".and().insertInto(\"myTable\", 1L)")
            indent()
            add(".d(\"aColumn\", \"\\\"stringValue1\\\"\")")
            deindent()
            add(".and().insertInto(\"myTable\", 2L)")
            indent()
            add(".d(\"aColumn\", \"\\\"stringValue2\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testTimeStampForeignKeyColumn() {
        val idColumn = Column("Id", TIMESTAMP, 10, primaryKey = true, databaseType = DatabaseType.H2)
        val table0 = Table("Table0", setOf(idColumn), HashSet<ForeignKey>())

        val fkColumn = Column("fkId", TIMESTAMP, 10, primaryKey = false, databaseType = DatabaseType.H2)
        val table1 = Table("Table1", setOf(idColumn, fkColumn), HashSet<ForeignKey>())

        val pkGeneUniqueId = 12345L

        val timeStampGene = SqlActionGeneBuilder().buildSqlTimestampGene(idColumn.name)
        val primaryKeyTable0Gene = SqlPrimaryKeyGene(idColumn.name, "Table0", timeStampGene, pkGeneUniqueId)
        val primaryKeyTable1Gene = SqlPrimaryKeyGene(idColumn.name, "Table1", timeStampGene, 10)


        val firstInsertionId = 1001L
        val insertIntoTable0 = SqlAction(table0, setOf(idColumn), firstInsertionId, listOf(primaryKeyTable0Gene))
        val secondInsertionId = 1002L
        val foreignKeyGene = SqlForeignKeyGene(fkColumn.name, secondInsertionId, "Table0", false, pkGeneUniqueId)

        val insertIntoTable1 = SqlAction(table1, setOf(idColumn, fkColumn), secondInsertionId, listOf(primaryKeyTable1Gene, foreignKeyGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insertIntoTable0.copy() as SqlAction, insertIntoTable1.copy() as SqlAction))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode(test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 1001L)")
            indent()
            indent()
            add(".d(\"Id\", \"\\\"2016-03-12 00:00:00\\\"\")")
            deindent()
            add(".and().insertInto(\"Table1\", 1002L)")
            indent()
            add(".d(\"Id\", \"\\\"2016-03-12 00:00:00\\\"\")")
            add(".d(\"fkId\", \"\\\"2016-03-12 00:00:00\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }

    @Test
    fun testIndirectForeignKeyColumn() {
        val table0_Id = Column("Id", INTEGER, 10, primaryKey = true, autoIncrement = true, databaseType = DatabaseType.H2)
        val table0 = Table("Table0", setOf(table0_Id), HashSet<ForeignKey>())

        val table1_Id = Column("Id", INTEGER, 10, primaryKey = true, foreignKeyToAutoIncrement = true, databaseType = DatabaseType.H2)
        val table1 = Table("Table1", setOf(table1_Id), HashSet<ForeignKey>())

        val table2_Id = Column("Id", INTEGER, 10, primaryKey = true, foreignKeyToAutoIncrement = true, databaseType = DatabaseType.H2)
        val table2 = Table("Table2", setOf(table2_Id), HashSet<ForeignKey>())

        val insertId0 = 1001L
        val autoGene = SqlAutoIncrementGene(table0_Id.name)
        val pkGene0 = SqlPrimaryKeyGene(table0_Id.name, "Table0", autoGene, insertId0)
        val insert0 = SqlAction(table0, setOf(table0_Id), insertId0, listOf(pkGene0))


        val insertId1 = 1002L
        val fkGene0 = SqlForeignKeyGene(table1_Id.name, insertId1, "Table0", false, insertId0)
        val pkGene1 = SqlPrimaryKeyGene(table1_Id.name, "Table1", fkGene0, insertId1)
        val insert1 = SqlAction(table1, setOf(table1_Id), insertId1, listOf(pkGene1))


        val insertId2 = 1003L
        val fkGene1 = SqlForeignKeyGene(table2_Id.name, insertId2, "Table1", false, insertId1)
        val pkGene2 = SqlPrimaryKeyGene(table2_Id.name, "Table2", fkGene1, insertId2)
        val insert2 = SqlAction(table2, setOf(table2_Id), insertId2, listOf(pkGene2))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insert0, insert1, insert2))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 1001L)")
            indent()
            add(".and().insertInto(\"Table1\", 1002L)")
            indent()
            add(".r(\"Id\", 1001L)")
            deindent()
            add(".and().insertInto(\"Table2\", 1003L)")
            indent()
            add(".r(\"Id\", 1002L)")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }

    @Test
    fun testInsertDateColumnType() {
        val idColumn = Column("Id", INTEGER, 10, primaryKey = true, autoIncrement = true, databaseType = DatabaseType.H2)
        val dateColumn = Column("birthDate", DATE, 10, primaryKey = false, autoIncrement = false, databaseType = DatabaseType.H2)

        val table = Table("Table0", setOf(idColumn, dateColumn), HashSet<ForeignKey>())


        val autoGene = SqlAutoIncrementGene(table.name)
        val pkGene0 = SqlPrimaryKeyGene(idColumn.name, "Table0", autoGene, 10)
        val dateGene = DateGene(dateColumn.name)
        val insert = SqlAction(table, setOf(idColumn, dateColumn), 0L, listOf(pkGene0, dateGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insert))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 0L)")
            indent()
            indent()
            add(".d(\"birthDate\", \"\\\"2016-03-12\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }

    @Test
    fun testUUIDColumnType() {
        val idColumn = Column("Id", INTEGER, 10, primaryKey = true, autoIncrement = true,
                databaseType = DatabaseType.H2)
        val uuidColumn = Column("uuidCode", UUID, 10, primaryKey = false, autoIncrement = false,
                databaseType = DatabaseType.H2)

        val table = Table("Table0", setOf(idColumn, uuidColumn), HashSet<ForeignKey>())


        val autoGene = SqlAutoIncrementGene(table.name)
        val pkGene0 = SqlPrimaryKeyGene(idColumn.name, "Table0", autoGene, 10)
        val uuidGene = UUIDGene(uuidColumn.name)
        val insert = SqlAction(table, setOf(idColumn, uuidColumn), 0L, listOf(pkGene0, uuidGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insert))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 0L)")
            indent()
            indent()
            add(".d(\"uuidCode\", \"\\\"00000000-0000-0000-0000-000000000000\\\"\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }

    @Test
    fun testJSONBEmpty() {


        val idColumn = Column("Id", INTEGER, 10, primaryKey = true, autoIncrement = true, databaseType = DatabaseType.POSTGRES)
        val jsonbColumn = Column("jsonbColumn", JSONB, 10, primaryKey = false, autoIncrement = false, databaseType = DatabaseType.POSTGRES)

        val table = Table("Table0", setOf(idColumn, jsonbColumn), HashSet<ForeignKey>())


        val autoGene = SqlAutoIncrementGene(table.name)
        val pkGene0 = SqlPrimaryKeyGene(idColumn.name, "Table0", autoGene, 10)
        val objectGene = ObjectGene(jsonbColumn.name, listOf())
        val insert = SqlAction(table, setOf(idColumn, jsonbColumn), 0L, listOf(pkGene0, objectGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insert))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 0L)")
            indent()
            indent()
            add(".d(\"jsonbColumn\", \"'{}'\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testJSONBColumnType() {


        val idColumn = Column("Id", INTEGER, 10, primaryKey = true, autoIncrement = true, databaseType = DatabaseType.POSTGRES)
        val jsonbColumn = Column("jsonbColumn", JSONB, 10, primaryKey = false, autoIncrement = false, databaseType = DatabaseType.POSTGRES)

        val table = Table("Table0", setOf(idColumn, jsonbColumn), HashSet<ForeignKey>())


        val autoGene = SqlAutoIncrementGene(table.name)
        val pkGene0 = SqlPrimaryKeyGene(idColumn.name, "Table0", autoGene, 10)
        val objectGene = ObjectGene(jsonbColumn.name, listOf(IntegerGene("integerField")))
        val insert = SqlAction(table, setOf(idColumn, jsonbColumn), 0L, listOf(pkGene0, objectGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insert))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 0L)")
            indent()
            indent()
            add(".d(\"jsonbColumn\", \"'{\\\"integerField\\\":0}'\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testJSONBoolean() {
        val idColumn = Column("Id", INTEGER, 10, primaryKey = true, autoIncrement = true, databaseType = DatabaseType.POSTGRES)
        val jsonbColumn = Column("jsonbColumn", JSONB, 10, primaryKey = false, autoIncrement = false, databaseType = DatabaseType.POSTGRES)

        val table = Table("Table0", setOf(idColumn, jsonbColumn), HashSet<ForeignKey>())


        val autoGene = SqlAutoIncrementGene(table.name)
        val pkGene0 = SqlPrimaryKeyGene(idColumn.name, "Table0", autoGene, 10)
        val objectGene = ObjectGene(jsonbColumn.name, listOf(BooleanGene("booleanField")))
        val insert = SqlAction(table, setOf(idColumn, jsonbColumn), 0L, listOf(pkGene0, objectGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insert))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 0L)")
            indent()
            indent()
            add(".d(\"jsonbColumn\", \"'{\\\"booleanField\\\":true}'\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }


    @Test
    fun testJSONString() {
        val idColumn = Column("Id", INTEGER, 10, primaryKey = true, autoIncrement = true, databaseType = DatabaseType.POSTGRES)
        val jsonbColumn = Column("jsonbColumn", JSONB, 10, primaryKey = false, autoIncrement = false, databaseType = DatabaseType.POSTGRES)

        val table = Table("Table0", setOf(idColumn, jsonbColumn), HashSet<ForeignKey>())


        val autoGene = SqlAutoIncrementGene(table.name)
        val pkGene0 = SqlPrimaryKeyGene(idColumn.name, "Table0", autoGene, 10)
        val objectGene = ObjectGene(jsonbColumn.name, listOf(StringGene("stringField")))
        val insert = SqlAction(table, setOf(idColumn, jsonbColumn), 0L, listOf(pkGene0, objectGene))

        val (format, baseUrlOfSut, ei) = buildEvaluatedIndividual(mutableListOf(insert))
        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("List<InsertionDto> insertions = sql().insertInto(\"Table0\", 0L)")
            indent()
            indent()
            add(".d(\"jsonbColumn\", \"'{\\\"stringField\\\":\\\"foo\\\"}'\")")
            deindent()
            add(".dtos();")
            deindent()
            add("InsertionResultsDto insertionsresult = controller.execInsertionsIntoDatabase(insertions);")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }

    @Test
    fun testRootTryCatchTimedout(){
        val format = OutputFormat.JAVA_JUNIT_4

        val baseUrlOfSut = "baseUrlOfSut"
        val action = RestCallAction("1", HttpVerb.GET, RestPath("/"), mutableListOf())
        val restActions = listOf(action).toMutableList()
        val individual = RestIndividual(restActions, SampleType.RANDOM)
        TestUtils.doInitializeIndividualForTesting(individual)

        val fitnessVal = FitnessValue(0.0)
        val result = RestCallResult(action.getLocalId())
        result.setTimedout(timedout = true)
        val results = listOf(result)
        val ei = EvaluatedIndividual(fitnessVal, individual, results)
        val config = getConfig(format)
        config.expectationsActive = true

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())

        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = Lines(format).apply {
            add("@Test")
            add("public void test() throws Exception {")
            indent()
            add("")
            add("try{")
            indent()
            add("given().accept(\"*/*\")")
            indent()
            indent()
            add(".get(baseUrlOfSut + \"/\");")
            deindent()
            deindent()
            deindent()
            add("} catch(Exception e){")
            add("}")
            deindent()
            add("}")
        }

        assertEquals(expectedLines.toString(), lines.toString())
    }

    /*


    fun testResultValue(){
        val objectGene = ObjectGene("Object", listOf(StringGene("stringField")))
        val format = OutputFormat.JAVA_JUNIT_4

        val baseUrlOfSut = "baseUrlOfSut"

        val sampleType = SampleType.RANDOM

        val restActions = emptyList<RestAction>().toMutableList()


        val individual = RestIndividual(restActions, sampleType, dbInitialization)

        val fitnessVal = FitnessValue(0.0)

        val results = emptyList<ActionResult>().toMutableList()

        val ei = EvaluatedIndividual<RestIndividual>(fitnessVal, individual, results)
    }

     */


    @Test
    fun testDbInBetween() {
        val fooId = Column("Id", INTEGER, 10, primaryKey = true, databaseType = DatabaseType.H2)
        val foo = Table("Foo", setOf(fooId), setOf())

        val fkId = Column("fkId", INTEGER, 10, primaryKey = false, databaseType = DatabaseType.H2)
        val bar = Table("Bar", setOf(fooId, fkId), setOf())

        val pkGeneUniqueId = 12345L

        val integerGene = IntegerGene(fooId.name, 42, 0, 100)
        val pkFoo = SqlPrimaryKeyGene(fooId.name, "Foo", integerGene, pkGeneUniqueId)
        val pkBar = SqlPrimaryKeyGene(fooId.name, "Bar", integerGene, 10)
        val fooInsertionId = 1001L
        val fooInsertion = SqlAction(foo, setOf(fooId), fooInsertionId, listOf(pkFoo))
        val barInsertionId = 1002L
        val foreignKeyGene = SqlForeignKeyGene(fkId.name, barInsertionId, "Foo", false, uniqueIdOfPrimaryKey = pkGeneUniqueId)
        val barInsertion = SqlAction(bar, setOf(fooId, fkId), barInsertionId, listOf(pkBar, foreignKeyGene))

        val fooAction = RestCallAction("1", HttpVerb.GET, RestPath("/foo"), mutableListOf())
        val barAction = RestCallAction("2", HttpVerb.GET, RestPath("/bar"), mutableListOf())

        val (format, baseUrlOfSut, ei) = buildResourceEvaluatedIndividual(
            dbInitialization = mutableListOf(),
            groups = mutableListOf(
                (mutableListOf(fooInsertion.copy() as SqlAction) to mutableListOf(fooAction.copy() as RestCallAction)),
                (mutableListOf(barInsertion.copy() as SqlAction) to mutableListOf(barAction.copy() as RestCallAction))
            )
        )

        val config = getConfig(format)
        config.expectationsActive = false
        config.resourceSampleStrategy = EMConfig.ResourceSamplingStrategy.ConArchive
        config.probOfApplySQLActionToCreateResources=0.1

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())
        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = """
@Test
public void test() throws Exception {
    List<InsertionDto> insertions0 = sql().insertInto("Foo", 1001L)
            .d("Id", "42")
        .dtos();
    InsertionResultsDto insertions0result = controller.execInsertionsIntoDatabase(insertions0);
    
    try{
        given().accept("*/*")
                .get(baseUrlOfSut + "/foo");
    } catch(Exception e){
    }
    List<InsertionDto> insertions1 = sql(insertions0).insertInto("Bar", 1002L)
            .d("Id", "42")
            .d("fkId", "42")
        .dtos();
    InsertionResultsDto insertions1result = controller.execInsertionsIntoDatabase(insertions1, insertions0result);
    
    try{
        given().accept("*/*")
                .get(baseUrlOfSut + "/bar");
    } catch(Exception e){
    }
}

""".trimIndent()

        assertEquals(expectedLines, lines.toString())
    }


    @Test
    fun testDbInBetweenSkipFailure() {
        val fooId = Column("Id", INTEGER, 10, primaryKey = true, databaseType = DatabaseType.H2)
        val foo = Table("Foo", setOf(fooId), setOf())

        val fkId = Column("fkId", INTEGER, 10, primaryKey = false, databaseType = DatabaseType.H2)
        val bar = Table("Bar", setOf(fooId, fkId), setOf())

        val pkGeneUniqueId = 12345L

        val integerGene = IntegerGene(fooId.name, 42, 0, 100)
        val pkFoo = SqlPrimaryKeyGene(fooId.name, "Foo", integerGene, pkGeneUniqueId)
        val pkBar = SqlPrimaryKeyGene(fooId.name, "Bar", integerGene, 10)
        val fooInsertionId = 1001L
        val fooInsertion = SqlAction(foo, setOf(fooId), fooInsertionId, listOf(pkFoo))
        val barInsertionId = 1002L
        val foreignKeyGene = SqlForeignKeyGene(fkId.name, barInsertionId, "Foo", false, uniqueIdOfPrimaryKey = pkGeneUniqueId)
        val barInsertion = SqlAction(bar, setOf(fooId, fkId), barInsertionId, listOf(pkBar, foreignKeyGene))

        val fooAction = RestCallAction("1", HttpVerb.GET, RestPath("/foo"), mutableListOf())
        val barAction = RestCallAction("2", HttpVerb.GET, RestPath("/bar"), mutableListOf())

        val groups =  mutableListOf(
            (mutableListOf(fooInsertion.copy() as SqlAction) to mutableListOf(fooAction.copy() as RestCallAction)),
            (mutableListOf(barInsertion.copy() as SqlAction) to mutableListOf(barAction.copy() as RestCallAction))
        )

        val (format, baseUrlOfSut, ei) = buildResourceEvaluatedIndividual(
            dbInitialization = mutableListOf(),
            groups = groups
        )

        val fooInsertionResult = ei.seeResults(groups[0].first)
        assertEquals(1, fooInsertionResult.size)
        assertTrue(fooInsertionResult[0] is SqlActionResult)
        (fooInsertionResult[0] as SqlActionResult).setInsertExecutionResult(false)

        val config = getConfig(format)
        config.resourceSampleStrategy = EMConfig.ResourceSamplingStrategy.ConArchive
        config.probOfApplySQLActionToCreateResources=0.1
        config.skipFailureSQLInTestFile = true

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())
        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = """
@Test
public void test() throws Exception {
    
    try{
        given().accept("*/*")
                .get(baseUrlOfSut + "/foo");
    } catch(Exception e){
    }
    List<InsertionDto> insertions1 = sql().insertInto("Bar", 1002L)
            .d("Id", "42")
            .d("fkId", "42")
        .dtos();
    InsertionResultsDto insertions1result = controller.execInsertionsIntoDatabase(insertions1);
    
    try{
        given().accept("*/*")
                .get(baseUrlOfSut + "/bar");
    } catch(Exception e){
    }
}

""".trimIndent()

        assertEquals(expectedLines, lines.toString())
    }

    @Test
    fun testDbInBetweenWithEmptyDb() {

        val fooAction = RestCallAction("1", HttpVerb.GET, RestPath("/foo"), mutableListOf())
        val barAction = RestCallAction("2", HttpVerb.GET, RestPath("/bar"), mutableListOf())

        val (format, baseUrlOfSut, ei) = buildResourceEvaluatedIndividual(
            dbInitialization = mutableListOf(),
            groups = mutableListOf(
                (mutableListOf<SqlAction>() to mutableListOf(fooAction)),
                (mutableListOf<SqlAction>() to mutableListOf(barAction))
            )
        )

        val config = getConfig(format)
        config.resourceSampleStrategy = EMConfig.ResourceSamplingStrategy.ConArchive
        config.probOfApplySQLActionToCreateResources=0.1

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())
        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = """
@Test
public void test() throws Exception {
    
    try{
        given().accept("*/*")
                .get(baseUrlOfSut + "/foo");
    } catch(Exception e){
    }
    
    try{
        given().accept("*/*")
                .get(baseUrlOfSut + "/bar");
    } catch(Exception e){
    }
}

""".trimIndent()

        assertEquals(expectedLines, lines.toString())
    }


    @Test
    fun testTestWithObjectAssertion(){
        val fooAction = RestCallAction("1", HttpVerb.GET, RestPath("/foo"), mutableListOf())

        val (format, baseUrlOfSut, ei) = buildResourceEvaluatedIndividual(
            dbInitialization = mutableListOf(),
            groups = mutableListOf(
                (mutableListOf<SqlAction>() to mutableListOf(fooAction))
            ),
            format = OutputFormat.JS_JEST
        )

        val fooResult = ei.seeResult(fooAction.getLocalId()) as RestCallResult
        fooResult.setTimedout(false)
        fooResult.setStatusCode(200)
        fooResult.setBody("""
           [
                {},
                {
                    "id":"foo",
                    "properties":[
                        {},
                        {
                          "name":"mapProperty1",
                          "type":"string",
                          "value":"one"
                        },
                        {
                          "name":"mapProperty2",
                          "type":"string",
                          "value":"two"
                        }],
                    "empty":{}
                }
           ]
        """.trimIndent())
        fooResult.setBodyType(MediaType.APPLICATION_JSON_TYPE)


        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())
        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = """
            test("test", async () => {
                
                const res_0 = await superagent
                        .get(baseUrlOfSut + "/foo").set('Accept', "*/*")
                        .ok(res => res.status);
                
                expect(res_0.status).toBe(200);
                expect(res_0.header["content-type"].startsWith("application/json")).toBe(true);
                expect(res_0.body.length).toBe(2);
                expect(Object.keys(res_0.body[0]).length).toBe(0);
                expect(res_0.body[1].properties.length).toBe(3);
                expect(Object.keys(res_0.body[1].properties[0]).length).toBe(0);
                expect(res_0.body[1].properties[1].name).toBe("mapProperty1");
                expect(res_0.body[1].properties[1].type).toBe("string");
                expect(res_0.body[1].properties[1].value).toBe("one");
                expect(res_0.body[1].properties[2].name).toBe("mapProperty2");
                expect(res_0.body[1].properties[2].type).toBe("string");
                expect(res_0.body[1].properties[2].value).toBe("two");
                expect(Object.keys(res_0.body[1].empty).length).toBe(0);
            });

""".trimIndent()

        assertEquals(expectedLines, lines.toString())
    }


    @Test
    fun testTestWithObjectLengthAssertion(){
        val fooAction = RestCallAction("1", HttpVerb.GET, RestPath("/foo"), mutableListOf())

        val (format, baseUrlOfSut, ei) = buildResourceEvaluatedIndividual(
            dbInitialization = mutableListOf(),
            groups = mutableListOf(
                (mutableListOf<SqlAction>() to mutableListOf(fooAction))
            ),
            format = OutputFormat.JS_JEST
        )

        val fooResult = ei.seeResult(fooAction.getLocalId()) as RestCallResult
        fooResult.setTimedout(false)
        fooResult.setStatusCode(200)
        fooResult.setBody("""
           {
                "p1":{},
                "p2":{
                    "id":"foo",
                    "properties":[
                        {},
                        {
                          "name":"mapProperty1",
                          "type":"string",
                          "value":"one"
                        },
                        {
                          "name":"mapProperty2",
                          "type":"string",
                          "value":"two"
                        }],
                    "empty":{}
                }
           }
        """.trimIndent())
        fooResult.setBodyType(MediaType.APPLICATION_JSON_TYPE)

        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())
        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = """
            test("test", async () => {
                
                const res_0 = await superagent
                        .get(baseUrlOfSut + "/foo").set('Accept', "*/*")
                        .ok(res => res.status);
                
                expect(res_0.status).toBe(200);
                expect(res_0.header["content-type"].startsWith("application/json")).toBe(true);
                expect(Object.keys(res_0.body.p1).length).toBe(0);
                expect(res_0.body.p2.properties.length).toBe(3);
                expect(Object.keys(res_0.body.p2.properties[0]).length).toBe(0);
                expect(res_0.body.p2.properties[1].name).toBe("mapProperty1");
                expect(res_0.body.p2.properties[1].type).toBe("string");
                expect(res_0.body.p2.properties[1].value).toBe("one");
                expect(res_0.body.p2.properties[2].name).toBe("mapProperty2");
                expect(res_0.body.p2.properties[2].type).toBe("string");
                expect(res_0.body.p2.properties[2].value).toBe("two");
                expect(Object.keys(res_0.body.p2.empty).length).toBe(0);
            });
            
""".trimIndent()
        assertEquals(expectedLines, lines.toString())
    }


    @Test
    fun testApplyAssertionEscapes(){
        val fooAction = RestCallAction("1", HttpVerb.GET, RestPath("/foo"), mutableListOf())

        val (format, baseUrlOfSut, ei) = buildResourceEvaluatedIndividual(
            dbInitialization = mutableListOf(),
            groups = mutableListOf(
                (mutableListOf<SqlAction>() to mutableListOf(fooAction))
            ),
            format = OutputFormat.JS_JEST
        )

        val fooResult = ei.seeResult(fooAction.getLocalId()) as RestCallResult
        val email = "foo@foo.foo"
        fooResult.setTimedout(false)
        fooResult.setStatusCode(200)
        fooResult.setBody("""
           {
                "email":$email
           }
        """.trimIndent())
        fooResult.setBodyType(MediaType.APPLICATION_JSON_TYPE)



        val config = getConfig(format)

        val test = TestCase(test = ei, name = "test")

        val writer = RestTestCaseWriter(config, PartialOracles())
        val lines = writer.convertToCompilableTestCode( test, baseUrlOfSut)

        val expectedLines = """
            test("test", async () => {
                
                const res_0 = await superagent
                        .get(baseUrlOfSut + "/foo").set('Accept', "*/*")
                        .ok(res => res.status);
                
                expect(res_0.status).toBe(200);
                expect(res_0.header["content-type"].startsWith("application/json")).toBe(true);
                expect(res_0.body.email).toBe("$email");
            });
            
""".trimIndent()
        assertEquals(expectedLines, lines.toString())
    }

}
