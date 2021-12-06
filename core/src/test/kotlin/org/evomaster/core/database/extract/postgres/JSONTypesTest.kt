package org.evomaster.core.database.extract.postgres

import org.evomaster.client.java.controller.api.dto.database.schema.DatabaseType
import org.evomaster.client.java.controller.db.SqlScriptRunner
import org.evomaster.client.java.controller.internal.db.SchemaExtractor
import org.evomaster.core.database.DbActionTransformer
import org.evomaster.core.database.SqlInsertBuilder
import org.evomaster.core.search.gene.sql.SqlJSONGene
import org.evomaster.core.search.gene.sql.SqlJSONPathGene
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Created by jgaleotti on 07-May-19.
 */
class JSONTypesTest : ExtractTestBasePostgres() {

    override fun getSchemaLocation() = "/sql_schema/postgres_json_types.sql"


    @Test
    fun testJSONTypes() {

        val schema = SchemaExtractor.extract(connection)

        assertNotNull(schema)

        assertEquals("public", schema.name.lowercase())
        assertEquals(DatabaseType.POSTGRES, schema.databaseType)

        val builder = SqlInsertBuilder(schema)
        val actions = builder.createSqlInsertionAction(
            "JSONTypes", setOf(
                "jsonColumn",
                "jsonbColumn",
                "jsonpathColumn"
            )
        )

        val genes = actions[0].seeGenes()

        assertEquals(3, genes.size)

        assertTrue(genes[0] is SqlJSONGene)
        assertTrue(genes[1] is SqlJSONGene)
        assertTrue(genes[2] is SqlJSONPathGene)

        val dbCommandDto = DbActionTransformer.transform(actions)
        SqlScriptRunner.execInsert(connection, dbCommandDto.insertions)

    }
}