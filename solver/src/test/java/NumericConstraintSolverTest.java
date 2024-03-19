import org.evomaster.client.java.controller.api.dto.database.schema.DbSchemaDto;
import org.evomaster.client.java.sql.SchemaExtractor;
import org.evomaster.client.java.sql.SqlScriptRunner;
import org.evomaster.core.search.gene.Gene;
import org.evomaster.core.search.gene.numeric.DoubleGene;
import org.evomaster.core.search.gene.numeric.LongGene;
import org.evomaster.core.sql.SqlAction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class NumericConstraintSolverTest {

    private static DbConstraintSolverZ3InDocker solver;
    private static Connection connection;

    @BeforeAll
    static void setup() throws Exception {
        String resourcesFolder = System.getProperty("user.dir") + "/src/test/resources/";

        connection = DriverManager.getConnection("jdbc:h2:mem:numeric_test", "sa", "");

        SqlScriptRunner.execCommand(connection,
            "CREATE TABLE products(price float not null, max_historical_price double not null, min_price bigint not null, stock long not null);\n" +
            "ALTER TABLE products add CHECK (min_price>1);\n" +
            "ALTER TABLE products add CHECK (stock>=5);"
        );

        DbSchemaDto schemaDto = SchemaExtractor.extract(connection);

        solver = new DbConstraintSolverZ3InDocker(schemaDto, resourcesFolder);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        solver.close();
        connection.close();
    }


    // ************************************************* //
    // ********** Tests for solving from file ********** //
    // ************************************************* //



    /**
     * Test solving a model with the example of returning all types of numeric constraints
     */
    @Test
    public void fromSchema() {
        List<SqlAction> response = solver.solve();

        assertEquals(1, response.size());

        SqlAction productAction = response.get(0);
        assertEquals("SQL_Insert_PRODUCTS_MAX_HISTORICAL_PRICE_MIN_PRICE_PRICE_STOCK", productAction.getName());
        assertEquals(4, productAction.seeTopGenes().size());

        for (Gene gene : productAction.seeTopGenes()) {
            if (gene.getName().equals("PRICE")) {
                assertTrue(gene instanceof DoubleGene);
                assertEquals(0.0, ((DoubleGene) gene).getValue());
                // When using two constraints, the min for the gene is not parsed correctly
                assertNull(((DoubleGene) gene).getMin());
                assertEquals(1.7976931348623157E308, ((DoubleGene) gene).getMaximum());
                assertTrue(((DoubleGene) gene).getMinInclusive());
                assertTrue(((DoubleGene) gene).getMaxInclusive());
            } else if (gene.getName().equals("STOCK")) {
                assertTrue(gene instanceof LongGene);
                assertEquals(5, ((LongGene) gene).getValue());
                assertEquals(5, ((LongGene) gene).getMin());
                assertEquals(9223372036854775807L, ((LongGene) gene).getMaximum());
                assertTrue(((LongGene) gene).getMinInclusive());
                assertTrue(((LongGene) gene).getMaxInclusive());
            } else if (gene.getName().equals("MIN_PRICE")) {
                assertTrue(gene instanceof LongGene);
                assertEquals(2, ((LongGene) gene).getValue());
                assertEquals(2, ((LongGene) gene).getMin());
                assertEquals(9223372036854775807L, ((LongGene) gene).getMaximum());
                assertTrue(((LongGene) gene).getMinInclusive());
                assertTrue(((LongGene) gene).getMaxInclusive());
            } else if (gene.getName().equals("MAX_HISTORICAL_PRICE")) {
                assertTrue(gene instanceof DoubleGene);
                assertEquals(0.0, ((DoubleGene) gene).getValue());
                assertNull(((DoubleGene) gene).getMin());
                assertEquals(1.7976931348623157E308, ((DoubleGene) gene).getMaximum());
                assertTrue(((DoubleGene) gene).getMinInclusive());
                assertTrue(((DoubleGene) gene).getMaxInclusive());
            }
        }
    }
}