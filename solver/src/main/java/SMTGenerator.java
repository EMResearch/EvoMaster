import net.sf.jsqlparser.statement.Statement;
import org.evomaster.client.java.controller.api.dto.database.schema.ColumnDto;
import org.evomaster.client.java.controller.api.dto.database.schema.DbSchemaDto;
import org.evomaster.client.java.controller.api.dto.database.schema.TableCheckExpressionDto;
import org.evomaster.client.java.controller.api.dto.database.schema.TableDto;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.expression.Expression;
import org.evomaster.dbconstraint.ConstraintDatabaseType;
import org.evomaster.dbconstraint.ast.*;
import org.evomaster.dbconstraint.parser.SqlConditionParserException;
import org.evomaster.dbconstraint.parser.jsql.JSqlConditionParser;

public class SMTGenerator {

    private final DbSchemaDto schema;
    private static final Map<String, String> TYPE_MAP = new HashMap<String, String>() {{
        put("BIGINT", "Int");
        put("INTEGER", "Int");
        put("FLOAT", "Real");
        put("DOUBLE", "Real");
        put("CHARACTER VARYING", "String");
        put("CHAR", "String");
    }};
    private final JSqlConditionParser parser;
    private final ConstraintDatabaseType dbType;
    private final Integer numberOfRows = 2;

    public SMTGenerator(DbSchemaDto schemaDto) {
        this.schema = schemaDto;
        this.parser = new JSqlConditionParser();
        this.dbType = ConstraintDatabaseType.valueOf(schemaDto.databaseType.name());
    }

    /**
     * Generates an SMT file based on an SQL query
     * @param sqlQuery SQL query to generate SMT file for
     * @param filePath Path to write the SMT file to
     */
    public void generateSMTFile(String sqlQuery, String filePath) {
        StringBuilder smt = new StringBuilder();

        // Parse the SQL query to extract the table name and condition
        String tableName;
        Expression condition;
        try {
            tableName = extractTableName(sqlQuery);
            condition = extractCondition(sqlQuery);
        } catch (JSQLParserException e) {
            throw new RuntimeException("Error when parsing table and condition from sqlQuery: " + sqlQuery, e);
        }

        TableDto table = schema.tables.stream()
                .filter(t -> t.name.equalsIgnoreCase(tableName))
                .findFirst()
                .orElse(null);

        if (table == null) {
            throw new RuntimeException("Table not found in schema: " + tableName);
        }

        // Generate SMT definitions for the table
        generateTableDefinitions(table, smt);

        // Add constraints from the SQL query
        addQueryConstraints(table, condition, smt);

        // Add final SMT lines
        addFinalLines(smt, table.name.toLowerCase());

        // Write to file
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(smt.toString());
        } catch (IOException e) {
            throw new RuntimeException("Error when writing SMT to file", e);
        }
    }

    private String extractTableName(String sqlQuery) throws JSQLParserException {
        Statement selectStatement = CCJSqlParserUtil.parse(sqlQuery);
        Select select = (Select) selectStatement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        Table table = (Table) plainSelect.getFromItem();
        return table.getName();
    }

    private Expression extractCondition(String sqlQuery) throws JSQLParserException {
        Statement selectStatement = CCJSqlParserUtil.parse(sqlQuery);
        Select select = (Select) selectStatement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        return plainSelect.getWhere();
    }

    public void generateTableDefinitions(TableDto table, StringBuilder smt) {
        String tableName = table.name.substring(0, 1).toUpperCase() + table.name.substring(1).toLowerCase();
        String columnsConcat = table.columns.stream()
                .map(c -> c.name.toLowerCase())
                .reduce((a, b) -> a + "-" + b)
                .orElse("");

        smt.append("(declare-datatypes () ((")
                .append(tableName).append("Row (")
                .append(columnsConcat).append(" ");

        for (ColumnDto column : table.columns) {
            String smtType = TYPE_MAP.get(column.type.toUpperCase());
            if (smtType == null) {
                throw new RuntimeException("Unsupported column type: " + column.type);
            }
            smt.append("(").append(column.name.toUpperCase()).append(" ").append(smtType).append(") ");
        }
        smt.append("))))\n");

        // Declare constants for rows
        for (int i = 1; i <= numberOfRows; i++) {
            smt.append("(declare-const ").append(table.name.toLowerCase()).append(i)
                    .append(" ").append(tableName).append("Row)\n");
        }

        // Add constraints for each column
        for (TableCheckExpressionDto check : table.tableCheckExpressions) {
            try {
                SqlCondition condition = parser.parse(check.sqlCheckExpression, this.dbType);
                for (int i = 1; i <= numberOfRows; i++) {
                    String constraint = parseCheckExpression(table, condition, i);
                    smt.append("(assert ").append(constraint).append(")\n");
                }
            } catch (SqlConditionParserException e) {
                throw new RuntimeException("Error parsing check expression: " + check.sqlCheckExpression, e);
            }
        }
    }

    /**
     * Parses a check expression and returns the string to assert in smt format
     * @param tableDto the table where the variable is a column of
     * @param condition the condition to parse and translate
     * @return the string to assert in smt format
     */
    private String parseCheckExpression(TableDto tableDto, SqlCondition condition, Integer index) {

        if (condition instanceof SqlAndCondition) {
            SqlAndCondition andCondition = (SqlAndCondition) condition;

            String leftResponse = parseCheckExpression(tableDto, andCondition.getLeftExpr(), index);
            String rightResponse = parseCheckExpression(tableDto, andCondition.getRightExpr(), index);

            return "(and " + leftResponse + " " + rightResponse + ")";
        }

        if (condition instanceof SqlOrCondition) {
            SqlOrCondition orCondition = (SqlOrCondition) condition;

            List<String> orMembers = new ArrayList<>();
            for (SqlCondition c : orCondition.getOrConditions()) {
                String response = parseCheckExpression(tableDto, c, index);
                orMembers.add(response);
            }
            return concatenateOrs(orMembers);
        }
        if (!(condition instanceof SqlComparisonCondition)) {
            // TODO: Support other check expressions
            throw new RuntimeException("The condition is not supported: " + condition.getClass().getSimpleName());
        }
        SqlComparisonCondition comparisonCondition = (SqlComparisonCondition) condition;

        return getAssertFromComparison(tableDto, index, comparisonCondition);
    }

    private static String getAssertFromComparison(TableDto table, Integer index, SqlComparisonCondition condition) {
        String columnName = condition.getLeftOperand().toString();
        String variable = "(" + columnName + " " + table.name.toLowerCase() + index + ")";
        String compare = condition.getRightOperand().toString().replace("'", "\"");
        String comparator = condition.getSqlComparisonOperator().toString();
        return "(" + comparator + " " + variable + " " + compare + ")";
    }

    private String concatenateOrs(List<String> orMembers) {
        if (orMembers.isEmpty())
            throw new RuntimeException("The or condition is empty");

        if (orMembers.size() == 1)
            return orMembers.get(0);

        if (orMembers.size() == 2)
            return "(or " + orMembers.get(0) + " " + orMembers.get(1) + ")";

        return "(or " + orMembers.get(orMembers.size() - 1) + " " +
                concatenateOrs(orMembers.subList(0, orMembers.size() - 1)) + ")";
    }

    private void addQueryConstraints(TableDto table, Expression where, StringBuilder smt) {
        if (where != null) {
            for (int i = 1; i <= numberOfRows; i++) {
                String rowVariable = table.name.toLowerCase() + i;
                StringBuilder conditionBuilder = new StringBuilder();
                SMTExpressionDeParser expressionDeParser = new SMTExpressionDeParser(conditionBuilder, rowVariable);
                where.accept(expressionDeParser);

                smt.append("(assert ").append(conditionBuilder).append(")\n");
            }
        }
    }

    private void addFinalLines(StringBuilder smt, String tableNameLower) {
        smt.append("(check-sat)\n");
        for (int i = 1; i <= numberOfRows; i++) {
            smt.append("(get-value (").append(tableNameLower).append(i).append("))\n");
        }
    }
}
