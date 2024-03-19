import org.evomaster.client.java.controller.api.dto.database.schema.DbSchemaDto;
import org.evomaster.client.java.sql.SchemaExtractor;
import org.evomaster.client.java.sql.SqlScriptRunner;
import org.evomaster.core.search.gene.Gene;
import org.evomaster.core.search.gene.numeric.DoubleGene;
import org.evomaster.core.search.gene.numeric.IntegerGene;
import org.evomaster.core.search.gene.numeric.LongGene;
import org.evomaster.core.sql.SqlAction;
import org.evomaster.core.sql.SqlInsertBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class SqlBuilderFromSchemaTest {

    private static Connection connection;

    @BeforeAll
    static void setup() throws Exception {
         connection = DriverManager.getConnection("jdbc:h2:mem:constraint_test", "sa", "");
    }

    @AfterAll
    static void tearDown() throws Exception {
        connection.close();
    }

    /**
     * When having a check constraint that contains only lower bound,
     * then the SqlInsertBuilder takes it as the min value
     */
    @Test
    public void minInt() throws Exception {

        String schemaQuery = "CREATE TABLE products(price int not null);\n" +
                "ALTER TABLE products add CHECK (price>100);";

        List<SqlAction> newActions = getSqlActionsFromSchema(schemaQuery);

        assertEquals(1, newActions.size());
        List<Gene> genes = newActions.get(0).seeTopGenes();
        assertEquals(1, genes.size());

        assertTrue(genes.get(0) instanceof IntegerGene);
        assertEquals(101, ((IntegerGene) genes.get(0)).getMin());
        assertEquals(2147483647, ((IntegerGene) genes.get(0)).getMaximum());
    }

    /**
     * When having a check constraint that contains only upper bound,
     * then the SqlInsertBuilder takes it as the max value
     */
    @Test
    public void maxInt() throws Exception {

        String schemaQuery = "CREATE TABLE products(price int not null);\n" +
                "ALTER TABLE products add CHECK (price<9999);";

        List<SqlAction> newActions = getSqlActionsFromSchema(schemaQuery);

        assertEquals(1, newActions.size());
        List<Gene> genes = newActions.get(0).seeTopGenes();
        assertEquals(1, genes.size());

        assertTrue(genes.get(0) instanceof IntegerGene);
        assertEquals(-2147483648, ((IntegerGene) genes.get(0)).getMin());
        assertEquals(9998, ((IntegerGene) genes.get(0)).getMaximum());
    }

    /**
     * When having two separate check constraints that contains an upper and lower bound,
     * then the SqlInsertBuilder takes them as min/max
     */
    @Test
    public void minAndMaxIntSeparated() throws Exception {

        String schemaQuery = "CREATE TABLE products(price int not null);\n" +
                "ALTER TABLE products add CHECK (price>100);" +
                "ALTER TABLE products add CHECK (price<9999);";

        List<SqlAction> newActions = getSqlActionsFromSchema(schemaQuery);

        assertEquals(1, newActions.size());
        List<Gene> genes = newActions.get(0).seeTopGenes();
        assertEquals(1, genes.size());

        assertTrue(genes.get(0) instanceof IntegerGene);
        assertEquals(101, ((IntegerGene) genes.get(0)).getMin());
        assertEquals(9998, ((IntegerGene) genes.get(0)).getMaximum());
    }

    /**
     * When having a check constraint that contains an upper and lower bound,
     * then the SqlInsertBuilder takes them as min/max
     */
    @Test
    public void minAndMaxIntInTheSameCheck() throws Exception {

        String schemaQuery = "CREATE TABLE products(price int not null);\n" +
                "ALTER TABLE products add CHECK (price>100 AND price<9999);";

        List<SqlAction> newActions = getSqlActionsFromSchema(schemaQuery);

        assertEquals(1, newActions.size());
        List<Gene> genes = newActions.get(0).seeTopGenes();
        assertEquals(1, genes.size());

        assertTrue(genes.get(0) instanceof IntegerGene);
        assertEquals(101, ((IntegerGene) genes.get(0)).getMin());
        assertEquals(9998, ((IntegerGene) genes.get(0)).getMaximum());
    }

    /**
     * When comparing two different fields in the same check condition,
     * then the parser in create insert SQL Builder Action fails
     */
    @Test
    public void compareTwoFields() {

        String schemaQuery = "CREATE TABLE products(price int not null, min_price int not null);\n" +
                "ALTER TABLE products add CHECK (price > min_price);";

        try {
            getSqlActionsFromSchema(schemaQuery);
        } catch (Exception e) {
            // An exception is thrown when parsing the check with two fields
            return;
        }
        fail();
    }

    /**
     * When a column is BigInt type, then the gene is a LongGene
     */
    @Test
    public void bigIntToLongGene() throws Exception {

        String schemaQuery = "CREATE TABLE products(price bigint not null);\n" +
                "ALTER TABLE products add CHECK (price>100 AND price<9999);";

        List<SqlAction> newActions = getSqlActionsFromSchema(schemaQuery);

        assertEquals(1, newActions.size());
        List<Gene> genes = newActions.get(0).seeTopGenes();
        assertEquals(1, genes.size());

        assertTrue(genes.get(0) instanceof LongGene);
        assertEquals(101, ((LongGene) genes.get(0)).getMin());
        assertEquals(9998, ((LongGene) genes.get(0)).getMaximum());
    }

    /**
     * When a column is Float type, then the gene is a DoubleGene
     */
    @Test
    public void floatToDouble() throws Exception {

        String schemaQuery = "CREATE TABLE products(price float not null);\n";

        List<SqlAction> newActions = getSqlActionsFromSchema(schemaQuery);

        assertEquals(1, newActions.size());
        List<Gene> genes = newActions.get(0).seeTopGenes();
        assertEquals(1, genes.size());

        assertTrue(genes.get(0) instanceof DoubleGene);
    }

    /**
     * When setting lower and upper bounds in a Long Column,
     * Then they are considered as min/max in the gene
     */
    @Test
    public void setMinMaxInLongGene() throws Exception {

        String schemaQuery = "CREATE TABLE products(price long not null);\n" +
                "ALTER TABLE products add CHECK (price > 100);" +
                "ALTER TABLE products add CHECK (price < 9999);";

        List<SqlAction> newActions = getSqlActionsFromSchema(schemaQuery);

        assertEquals(1, newActions.size());
        List<Gene> genes = newActions.get(0).seeTopGenes();
        assertEquals(1, genes.size());

        assertTrue(genes.get(0) instanceof LongGene);
        assertEquals(101, ((LongGene) genes.get(0)).getMin());
        assertEquals(9998, ((LongGene) genes.get(0)).getMaximum());
    }

    /**
     * When setting lower and upper bounds in a Double Column,
     * then the parser in create insert SQL Builder Action fails
     */
    @Test
    public void setMinMaxInDoubleGene() {

        String schemaQuery = "CREATE TABLE products(price double not null);\n" +
                "ALTER TABLE products add CHECK (price > 100);" +
                "ALTER TABLE products add CHECK (price < 9999);";

        try {
            getSqlActionsFromSchema(schemaQuery);
        } catch (Exception e) {
            // An exception is thrown when parsing the check with a lower or upper bound
            return;
        }
        fail();
    }

    @AfterEach
    void dropTable() throws Exception {
        SqlScriptRunner.execCommand(connection, "DROP TABLE products;");
    }

    @NotNull
    private static List<SqlAction> getSqlActionsFromSchema(String schemaQuery) throws Exception {
        SqlScriptRunner.execCommand(connection, schemaQuery);

        DbSchemaDto schemaDto = SchemaExtractor.extract(connection);
        SqlInsertBuilder sqlInsertBuilder = new SqlInsertBuilder(schemaDto, null);

        return sqlInsertBuilder.createSqlInsertionAction(
                schemaDto.tables.get(0).name,
                new HashSet<>(Collections.singletonList("*")),
                new LinkedList<>(),
                false,
                false,
                false);
    }
}