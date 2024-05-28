
import net.sf.jsqlparser.JSQLParserException;
import org.evomaster.client.java.controller.api.dto.database.schema.DbSchemaDto;
import org.evomaster.client.java.sql.SchemaExtractor;
import org.evomaster.client.java.sql.SqlScriptRunner;
import org.evomaster.dbconstraint.parser.SqlConditionParserException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SMTGeneratorTest {

    static String resourcesFolder = System.getProperty("user.dir") + "/src/test/resources/";
    static String tmpFolderPath = "tmp_" + Instant.now().getEpochSecond() + "/";
    private static SMTGenerator generator;
    private static Connection connection;

    @BeforeAll
    static void setup() throws Exception {

        try {
            Files.createDirectories(Paths.get(resourcesFolder + tmpFolderPath));
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error creating tmp folder '%s'. ", tmpFolderPath), e);
        }

        connection = DriverManager.getConnection("jdbc:h2:mem:constraint_test", "sa", "");

        SqlScriptRunner.execCommand(connection, "CREATE TABLE users(id bigint generated by default as identity primary key, name varchar(255), age int, points int);\n" +
                "ALTER TABLE users add CHECK (age>18 AND age<100);\n" +
                "ALTER TABLE users add CHECK (points<=10);\n" +
                "ALTER TABLE users add CHECK (points>=0);\n" +
                "ALTER TABLE users add CHECK (name = 'agus');");

        DbSchemaDto schemaDto = SchemaExtractor.extract(connection);

        generator = new SMTGenerator(schemaDto);
    }

    @AfterAll
    static void tearDown() throws SQLException, IOException {
        connection.close();

        FileUtils.deleteDirectory(new File(resourcesFolder + tmpFolderPath));
    }

    @Test
    public void satisfiabilityExample() throws IOException, JSQLParserException, SqlConditionParserException {
        String outputFileName = Paths.get(  resourcesFolder + tmpFolderPath + "smt2_" + System.currentTimeMillis() + ".smt2").toString();
        generator.generateSMTFile( "SELECT * FROM Users WHERE Age > 30;", outputFileName);

        // Read from file the response and compare
        String everything = readFromFileAsString(outputFileName);
        String expected = readFromFileAsString(resourcesFolder + "expected-output-test1.smt");

        assertEquals(expected, everything);
    }

    private static String readFromFileAsString(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append(System.lineSeparator());
            line = br.readLine();
        }

        return sb.toString();
    }


}