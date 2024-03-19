import org.evomaster.client.java.controller.api.dto.database.schema.ColumnDto;
import org.evomaster.client.java.controller.api.dto.database.schema.DatabaseType;
import org.evomaster.client.java.controller.api.dto.database.schema.TableCheckExpressionDto;
import org.evomaster.client.java.controller.api.dto.database.schema.TableDto;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Smt2WriterTest {

    static TableDto products = new TableDto();

    @BeforeAll
    public static void setup() {
        products.name = "products";
        ColumnDto price = new ColumnDto();
        price.name = "PRICE";
        price.type = "INTEGER";
        ColumnDto stock = new ColumnDto();
        stock.name = "STOCK";
        stock.type = "BIGINT";
        ColumnDto size = new ColumnDto();
        size.name = "SIZE";
        size.type = "LONG";
        ColumnDto average = new ColumnDto();
        average.name = "AVERAGE";
        average.type = "FLOAT";
        ColumnDto max_average = new ColumnDto();
        max_average.name = "MAX_AVERAGE";
        max_average.type = "DOUBLE";

        products.columns = Arrays.asList(price, stock, size, average, max_average);
    }

    // ************************************** //
    // ********** CHECK constraint ********** //
    // ************************************** //
    @Test
    public void productGreaterPriceAsParsed() {
        Smt2Writer writer = new Smt2Writer(DatabaseType.H2);
        boolean succeed = writer.addTableCheckExpression(products, CheckExpressionFrom("(\"PRICE\" > 100)"));

        assertTrue(succeed);

        String text = writer.asText();

        String expected = "(set-logic QF_SLIA)\n" +
                "(declare-fun products_PRICE () Int)\n" +
                "(assert (> products_PRICE 100))\n" +
                "(check-sat)\n" +
                "(get-value (products_PRICE))\n";

        assertEquals(expected, text);
    }

    @Test
    public void productGreaterAndLowerPriceAsParsed() {
        Smt2Writer writer = new Smt2Writer(DatabaseType.H2);
        boolean succeed = writer.addTableCheckExpression(products, CheckExpressionFrom("(\"PRICE\" > 100 AND \"PRICE\" < 9999)"));

        assertTrue(succeed);

        String text = writer.asText();

        String expected = "(set-logic QF_SLIA)\n" +
                "(declare-fun products_PRICE () Int)\n" +
                "(assert (and (> products_PRICE 100) (< products_PRICE 9999)))\n" +
                "(check-sat)\n" +
                "(get-value (products_PRICE))\n";

        assertEquals(expected, text);
    }
    @Test
    public void productOrPriceParsed() {
        Smt2Writer writer = new Smt2Writer(DatabaseType.H2);
        boolean succeed = writer.addTableCheckExpression(products, CheckExpressionFrom("(\"STOCK\" >= 5 OR \"STOCK\" = 100)"));

        assertTrue(succeed);

        String text = writer.asText();

        String expected = "(set-logic QF_SLIA)\n" +
                "(declare-fun products_STOCK () Int)\n" +
                "(assert (or (>= products_STOCK 5) (= products_STOCK 100)))\n" +
                "(check-sat)\n" +
                "(get-value (products_STOCK))\n";

        assertEquals(expected, text);
    }


    @Test
    public void productGreaterPriceAndStock() {
        Smt2Writer writer = new Smt2Writer(DatabaseType.H2);
        boolean succeed = writer.addTableCheckExpression(products, CheckExpressionFrom("(\"PRICE\">1000)"));
        succeed = succeed && writer.addTableCheckExpression(products, CheckExpressionFrom("(\"STOCK\">=5)"));

        assertTrue(succeed);

        String text = writer.asText();

        String expected = "(set-logic QF_SLIA)\n" +
                "(declare-fun products_STOCK () Int)\n" +
                "(declare-fun products_PRICE () Int)\n" +
                "(assert (> products_PRICE 1000))\n" +
                "(assert (>= products_STOCK 5))\n" +
                "(check-sat)\n" +
                "(get-value (products_STOCK))\n" +
                "(get-value (products_PRICE))\n";

        assertEquals(expected, text);
    }

    @Test
    public void productPriceEnum() {
        Smt2Writer writer = new Smt2Writer(DatabaseType.H2);
        boolean succeed = writer.addTableCheckExpression(products, CheckExpressionFrom("(\"PRICE\" IN (12, 13, 14))"));

        assertTrue(succeed);

        String text = writer.asText();

        String expected = "(set-logic QF_SLIA)\n" +
                "(declare-fun products_PRICE () Int)\n" +
                "(assert (or (= products_PRICE 14) (or (= products_PRICE 13) (= products_PRICE 12))))\n" +
                "(check-sat)\n" +
                "(get-value (products_PRICE))\n";

        assertEquals(expected, text);
    }

    @Test
    public void allNumericTypes() {
        Smt2Writer writer = new Smt2Writer(DatabaseType.H2);
        boolean succeed = writer.addTableCheckExpression(products, CheckExpressionFrom("(\"PRICE\">1000)"));
        succeed = succeed && writer.addTableCheckExpression(products, CheckExpressionFrom("(\"STOCK\">=5)"));
        succeed = succeed && writer.addTableCheckExpression(products, CheckExpressionFrom("(\"SIZE\"<500)"));
        succeed = succeed && writer.addTableCheckExpression(products, CheckExpressionFrom("(\"AVERAGE\"=5)"));
        succeed = succeed && writer.addTableCheckExpression(products, CheckExpressionFrom("(\"MAX_AVERAGE\"=8)"));

        assertTrue(succeed);

        String text = writer.asText();

        String expected = "(set-logic QF_SLIA)\n" +
                "(declare-fun products_SIZE () Int)\n" +
                "(declare-fun products_PRICE () Int)\n" +
                "(declare-fun products_STOCK () Int)\n" +
                "(declare-fun products_AVERAGE () Real)\n" +
                "(declare-fun products_MAX_AVERAGE () Real)\n" +
                "(assert (> products_PRICE 1000))\n" +
                "(assert (>= products_STOCK 5))\n" +
                "(assert (< products_SIZE 500))\n" +
                "(assert (= products_AVERAGE 5))\n" +
                "(assert (= products_MAX_AVERAGE 8))\n" +
                "(check-sat)\n" +
                "(get-value (products_SIZE))\n" +
                "(get-value (products_PRICE))\n" +
                "(get-value (products_STOCK))\n" +
                "(get-value (products_AVERAGE))\n" +
                "(get-value (products_MAX_AVERAGE))\n";

        assertEquals(expected, text);
    }

    @NotNull
    private TableCheckExpressionDto CheckExpressionFrom(String checkPriceString) {
        TableCheckExpressionDto checkExpression = new TableCheckExpressionDto();
        checkExpression.sqlCheckExpression = checkPriceString;
        return checkExpression;
    }

}

