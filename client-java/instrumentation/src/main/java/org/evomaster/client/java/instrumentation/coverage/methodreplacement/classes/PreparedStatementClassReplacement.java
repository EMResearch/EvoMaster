package org.evomaster.client.java.instrumentation.coverage.methodreplacement.classes;

import org.evomaster.client.java.instrumentation.SqlInfo;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.MethodReplacementClass;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.Replacement;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.evomaster.client.java.utils.SimpleLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class PreparedStatementClassReplacement implements MethodReplacementClass {

    @Override
    public Class<?> getTargetClass() {
        return PreparedStatement.class;
    }

    public static String extractSqlFromH2PreparedStatement(PreparedStatement stmt) {
        Class<?> klass = stmt.getClass();
        String className = klass.getName();
        if (!className.equals("org.h2.jdbc.JdbcPreparedStatement")) {
            throw new IllegalArgumentException("Invalid type: " + className);
        }

        try {
            /*
                Quite brittle... but easy to test on new H2 releases
             */
            Field cf = klass.getDeclaredField("command");
            cf.setAccessible(true);
            Object command = cf.get(stmt);
            Class<?> ck = command.getClass();
            if(! ck.getName().endsWith("CommandRemote")){
                /*
                    very brittle.. :(
                    based on the concrete class for the Command (there are 3...)
                    the location of the 'sql' private field is different :(
                 */
                ck = ck.getSuperclass();
            }
            Field sf = ck.getDeclaredField("sql");
            sf.setAccessible(true);
            String sql = (String) sf.get(command);

            Method pm = command.getClass().getDeclaredMethod("getParameters");
            pm.setAccessible(true);
            List<?> pv = (List<?>) pm.invoke(command);
            List<String> params = pv.stream()
                    .map(it -> {
                        try {
                            Method gpvm = it.getClass().getDeclaredMethod("getParamValue");
                            gpvm.setAccessible(true);
                            Object value =  gpvm.invoke(it);
                            if(value.getClass().getName().equals("org.h2.value.ValueLobDb")){
                                //FIXME this gives issues... not sure we can really handle it
                                return "LOB";
                            }
                            return value.toString();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            return interpolateSqlString(sql, params);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String interpolateSqlString(String sql, List<String> params) {

        long replacements = sql.chars().filter(it -> it=='?').count();
        if(replacements != params.size()){
            SimpleLogger.error("EvoMaster ERROR. Mismatch of parameter count " + replacements+"!="+ params.size()
                    + " in SQL command: " + sql);
            return null;
        }

        for(String p : params){
            sql = sql.replaceFirst("\\?", p);
        }

        return sql;
    }


    private static void handlePreparedStatement(PreparedStatement stmt) {
        if (stmt == null) {
            //nothing to do
        }

        String fullClassName = stmt.getClass().getName();
        if (fullClassName.startsWith("com.zaxxer.hikari.pool") ||
        fullClassName.startsWith("org.apache.tomcat.jdbc.pool") ||
        fullClassName.startsWith("com.sun.proxy")) {
            /*
                this is likely a proxy/wrapper, so we can skip it, as anyway we are going to
                intercept the call to the delegate
             */
            return;
        }

        /*
            This is very tricky, as not supported by all DBs... see for example:
            https://stackoverflow.com/questions/2382532/how-can-i-get-the-sql-of-a-preparedstatement

            So what done here is quite ad-hoc...
            There is no direct way to access the SQL command... but some drivers do print it
            when calling toString.

            Another option would be to intercept all methods to build the query, and reconstruct
            it manually... but it would be a LOT of work.
            Maybe something to consider if we are going to support more DBs in the future...
         */
        String sql = stmt.toString();

        //Postgres print the command as it is

        if (sql.startsWith("com.mysql")) {
            //MySQL prepend the command with the name of class followed by :
            sql = sql.substring(sql.indexOf(":") + 1);
        }

        if (stmt.getClass().getName().equals("org.h2.jdbc.JdbcPreparedStatement")) {
            /*
                Unfortunately H2 does not support it... so we have to extract and
                recompute it manually :(
             */
            sql = extractSqlFromH2PreparedStatement(stmt);
        }

        //TODO see TODO in StatementClassReplacement
        SqlInfo info = new SqlInfo(sql, false, false);
        ExecutionTracer.addSqlInfo(info);
    }


    @Replacement(type = ReplacementType.TRACKER, isPure = false)
    public static ResultSet executeQuery(PreparedStatement stmt) throws SQLException {
        handlePreparedStatement(stmt);
        return stmt.executeQuery();
    }

    @Replacement(type = ReplacementType.TRACKER, isPure = false)
    public static int executeUpdate(PreparedStatement stmt) throws SQLException {
        handlePreparedStatement(stmt);
        return stmt.executeUpdate();
    }

    @Replacement(type = ReplacementType.TRACKER, isPure = false)
    public static boolean execute(PreparedStatement stmt) throws SQLException {
        handlePreparedStatement(stmt);
        return stmt.execute();
    }
}
