package org.evomaster.dbconstraint.extract;


import net.sf.jsqlparser.expression.StringValue;
import org.evomaster.dbconstraint.*;
import org.evomaster.dbconstraint.ast.SqlAndCondition;
import org.evomaster.dbconstraint.ast.SqlBigDecimalLiteralValue;
import org.evomaster.dbconstraint.ast.SqlBigIntegerLiteralValue;
import org.evomaster.dbconstraint.ast.SqlBinaryDataLiteralValue;
import org.evomaster.dbconstraint.ast.SqlBooleanLiteralValue;
import org.evomaster.dbconstraint.ast.SqlColumn;
import org.evomaster.dbconstraint.ast.SqlComparisonCondition;
import org.evomaster.dbconstraint.ast.SqlCondition;
import org.evomaster.dbconstraint.ast.SqlConditionList;
import org.evomaster.dbconstraint.ast.SqlConditionVisitor;
import org.evomaster.dbconstraint.ast.SqlInCondition;
import org.evomaster.dbconstraint.ast.SqlIsNotNullCondition;
import org.evomaster.dbconstraint.ast.SqlIsNullCondition;
import org.evomaster.dbconstraint.ast.SqlLikeCondition;
import org.evomaster.dbconstraint.ast.SqlLiteralValue;
import org.evomaster.dbconstraint.ast.SqlNullLiteralValue;
import org.evomaster.dbconstraint.ast.SqlOrCondition;
import org.evomaster.dbconstraint.ast.SqlSimilarToCondition;
import org.evomaster.dbconstraint.ast.SqlStringLiteralValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.evomaster.dbconstraint.ast.SqlComparisonOperator.EQUALS_TO;

public class SqlConditionTranslator implements SqlConditionVisitor<TableConstraint, Void> {

    private static final String THIS_METHOD_SHOULD_NOT_BE_INVOKED = "This method should not be directly called";
    public static final String UNEXPECTED_COMPARISON_OPERATOR_MESSAGE = "Unexpected comparison operator ";

    private final TranslationContext translationContext;

    public SqlConditionTranslator(TranslationContext translationContext) {
        this.translationContext = translationContext;
    }

    /**
     * FIXME
     * temporary workaround before major refactoring.
     * Recall that Column.getTable() is not reliable
     */
    private String getTableName(SqlColumn column) {
        String tableName = column.getTableName();
        if (tableName != null) {
            return tableName;
        } else {
            return this.translationContext.getCurrentTableName();
        }
    }


    @Override
    public TableConstraint visit(SqlAndCondition andExpression, Void argument) {
        TableConstraint left = andExpression.getLeftExpr().accept(this, null);
        TableConstraint right = andExpression.getRightExpr().accept(this, null);
        return new AndConstraint(translationContext.getCurrentTableName(), left, right);
    }

    @Override
    public TableConstraint visit(SqlComparisonCondition e, Void argument) {
        SqlCondition left = e.getLeftOperand();
        SqlCondition right = e.getRightOperand();

        if (left instanceof SqlLiteralValue && right instanceof SqlColumn) {
            SqlLiteralValue leftLiteral = (SqlLiteralValue) left;
            SqlColumn rightColumn = (SqlColumn) right;
            return visit(leftLiteral, e, rightColumn);
        } else if (left instanceof SqlColumn && right instanceof SqlLiteralValue) {
            SqlColumn leftColumn = (SqlColumn) left;
            SqlLiteralValue rightLiteral = (SqlLiteralValue) right;
            return visit(leftColumn, e, rightLiteral);
        } else if (left instanceof SqlCondition && right instanceof SqlCondition) {
            TableConstraint leftTableConstraint = e.getLeftOperand().accept(this, null);
            TableConstraint rightTableConstraint = e.getRightOperand().accept(this, null);
            if (e.getSqlComparisonOperator().equals(EQUALS_TO)) {
                return new IffConstraint(translationContext.getCurrentTableName(), leftTableConstraint, rightTableConstraint);
            }
        }
        // TODO This translation should be implemented
        throw new SqlCannotBeTranslatedException(e.toSql() + " cannot be translated yet");
    }

    private TableConstraint visit(SqlColumn leftColumn, SqlComparisonCondition e, SqlLiteralValue rightLiteral) {
        final String tableName = getTableName(leftColumn);
        final String columnName = leftColumn.getColumnName();
        if (rightLiteral instanceof SqlBigIntegerLiteralValue) {
            long value = ((SqlBigIntegerLiteralValue) rightLiteral).getBigInteger().longValue();
            return getTableConstraint(e, tableName, columnName, value);
        } else if (rightLiteral instanceof SqlBigDecimalLiteralValue) {
            // TODO: Handle this as a float
            throw new UnsupportedOperationException("Unsupported Big Decimal literal " + rightLiteral);
        } else if (rightLiteral instanceof SqlStringLiteralValue) {
            SqlStringLiteralValue stringLiteralValue = (SqlStringLiteralValue) rightLiteral;
            if (e.getSqlComparisonOperator().equals(EQUALS_TO)) {
                return new EnumConstraint(tableName, columnName, Collections.singletonList(stringLiteralValue.getStringValue()));
            } else {
                throw new UnsupportedOperationException(UNEXPECTED_COMPARISON_OPERATOR_MESSAGE + e.getSqlComparisonOperator());
            }
        } else {
            throw new UnsupportedOperationException("Unsupported literal " + rightLiteral);
        }
    }

    private TableConstraint getTableConstraint(SqlComparisonCondition e, String tableName, String columnName, long value) {
        switch (e.getSqlComparisonOperator()) {
            case EQUALS_TO:
                return new RangeConstraint(tableName, columnName, value, value);

            case GREATER_THAN:
                return new LowerBoundConstraint(tableName, columnName, value + 1);

            case GREATER_THAN_OR_EQUAL:
                return new LowerBoundConstraint(tableName, columnName, value);

            case LESS_THAN:
                return new UpperBoundConstraint(tableName, columnName, value - 1);

            case LESS_THAN_OR_EQUAL:
                return new UpperBoundConstraint(tableName, columnName, value);

            default:
                throw new UnsupportedOperationException(UNEXPECTED_COMPARISON_OPERATOR_MESSAGE + e.getSqlComparisonOperator());

        }
    }

    private TableConstraint visit(SqlLiteralValue leftLiteral, SqlComparisonCondition e, SqlColumn rightColumn) {
        if (leftLiteral instanceof SqlBigIntegerLiteralValue) {
            long value = ((SqlBigIntegerLiteralValue) leftLiteral).getBigInteger().longValue();
            final String tableName = getTableName(rightColumn);
            final String columnName = rightColumn.getColumnName();
            switch (e.getSqlComparisonOperator()) {
                case EQUALS_TO:
                    return new RangeConstraint(tableName, columnName, value, value);

                case GREATER_THAN:
                    return new UpperBoundConstraint(tableName, columnName, value - 1);

                case GREATER_THAN_OR_EQUAL:
                    return new UpperBoundConstraint(tableName, columnName, value);

                case LESS_THAN:
                    return new LowerBoundConstraint(tableName, columnName, value + 1);

                case LESS_THAN_OR_EQUAL:
                    return new LowerBoundConstraint(tableName, columnName, value);

                default:
                    throw new UnsupportedOperationException(UNEXPECTED_COMPARISON_OPERATOR_MESSAGE + e.getSqlComparisonOperator());

            }
        } else {
            throw new UnsupportedOperationException("Unsupported literal " + e.getSqlComparisonOperator());
        }
    }


    @Override
    public TableConstraint visit(SqlInCondition inExpression, Void argument) {
        SqlColumn column = inExpression.getSqlColumn();
        String tableName = getTableName(column);
        String columnName = column.getColumnName();
        SqlConditionList rightItemsList = inExpression.getLiteralList();

        List<String> stringValues = new ArrayList<>();
        for (SqlCondition expressionValue : rightItemsList.getSqlConditionExpressions()) {
            final String stringValue;
            if (expressionValue instanceof SqlStringLiteralValue) {
                stringValue = new StringValue(expressionValue.toSql()).getNotExcapedValue();
            } else {
                stringValue = expressionValue.toSql();
            }
            stringValues.add(stringValue);
        }
        return new EnumConstraint(tableName, columnName, stringValues);

    }

    @Override
    public TableConstraint visit(SqlNullLiteralValue e, Void argument) {
        throw new UnsupportedOperationException(THIS_METHOD_SHOULD_NOT_BE_INVOKED);
    }

    @Override
    public TableConstraint visit(SqlStringLiteralValue e, Void argument) {
        throw new UnsupportedOperationException(THIS_METHOD_SHOULD_NOT_BE_INVOKED);
    }

    @Override
    public TableConstraint visit(SqlConditionList e, Void argument) {
        throw new UnsupportedOperationException(THIS_METHOD_SHOULD_NOT_BE_INVOKED);
    }

    @Override
    public TableConstraint visit(SqlIsNotNullCondition e, Void argument) {
        String tableName = getTableName(e.getColumn());
        String columnName = e.getColumn().getColumnName();
        return new IsNotNullConstraint(tableName, columnName);
    }

    @Override
    public TableConstraint visit(SqlBinaryDataLiteralValue e, Void argument) {
        throw new UnsupportedOperationException(THIS_METHOD_SHOULD_NOT_BE_INVOKED);
    }

    @Override
    public TableConstraint visit(SqlSimilarToCondition e, Void argument) {
        String tableName = getTableName(e.getColumn());
        final String pattern = e.getPattern().getStringValue();
        return new SimilarToConstraint(tableName, e.getColumn().getColumnName(), pattern, translationContext.getDatabaseType());
    }

    @Override
    public TableConstraint visit(SqlIsNullCondition e, Void argument) {
        throw new UnsupportedOperationException(THIS_METHOD_SHOULD_NOT_BE_INVOKED);
    }

    @Override
    public TableConstraint visit(SqlLikeCondition e, Void argument) {
        String tableName = getTableName(e.getColumnName());
        String columnName = e.getColumnName().getColumnName();
        String pattern = e.getPattern().getStringValue();
        return new LikeConstraint(tableName, columnName, pattern, translationContext.getDatabaseType());
    }

    @Override
    public TableConstraint visit(SqlOrCondition e, Void argument) {
        List<TableConstraint> orConstraints = e.getOrConditions().stream().map(c -> c.accept(this, null)).collect(Collectors.toList());
        if (orConstraints.stream().allMatch(c -> c instanceof LikeConstraint)) {
            // all like constraints should have the same tablename, column and database type
            Set<String> columnNames = orConstraints
                    .stream()
                    .map(c -> (LikeConstraint) c)
                    .map(c -> c.getColumnName())
                    .collect(Collectors.toSet());
            Set<String> tableNames = orConstraints
                    .stream()
                    .map(c -> (LikeConstraint) c)
                    .map(c -> c.getTableName())
                    .collect(Collectors.toSet());
            Set<ConstraintDatabaseType> databaseTypes = orConstraints
                    .stream().map(c -> (LikeConstraint) c)
                    .map(c -> c.getDatabaseType())
                    .collect(Collectors.toSet());

            if (columnNames.size() == 1 && tableNames.size() == 1 && databaseTypes.size() == 1) {
                String tableName = tableNames.iterator().next();
                String columnName = columnNames.iterator().next();
                ConstraintDatabaseType databaseType = databaseTypes.iterator().next();
                List<String> patterns = orConstraints
                        .stream()
                        .map(c -> (LikeConstraint) c)
                        .map(c -> c.getPatterns())
                        .flatMap(List::stream)
                        .collect(Collectors.toList());
                return new LikeConstraint(tableName,columnName,patterns,databaseType);
            }
        }
        return new OrConstraint(translationContext.getCurrentTableName(), orConstraints.toArray(new TableConstraint[]{}));
    }

    @Override
    public TableConstraint visit(SqlBigDecimalLiteralValue e, Void argument) {
        throw new UnsupportedOperationException(THIS_METHOD_SHOULD_NOT_BE_INVOKED);
    }

    @Override
    public TableConstraint visit(SqlBigIntegerLiteralValue e, Void argument) {
        throw new UnsupportedOperationException(THIS_METHOD_SHOULD_NOT_BE_INVOKED);
    }

    @Override
    public TableConstraint visit(SqlBooleanLiteralValue e, Void argument) {
        throw new UnsupportedOperationException(THIS_METHOD_SHOULD_NOT_BE_INVOKED);
    }

    @Override
    public TableConstraint visit(SqlColumn e, Void argument) {
        throw new UnsupportedOperationException(THIS_METHOD_SHOULD_NOT_BE_INVOKED);
    }


}
