package org.evomaster.client.java.instrumentation.staticstate;

public class MethodReplacementPreserveSemantics {

    /**
     * Most method replacements do preserve semantics.
     * So, when evaluating dynamically generated test cases in E2E tests, there is (should be) no issue.
     * The problem is when some method replacements DO NOT preserve semantics... the generated tests
     * will have no way to setup instrumentation info.
     *
     * In the ideal world, when evaluating dynamically generated tests, we should unload the instrumented classes.
     * But this does not seem possible :-(
     *
     * Anyway, this is only needed for E2E tests, so must have no impact on running EM.
     */
    public static boolean shouldPreserveSemantics = false;
}
