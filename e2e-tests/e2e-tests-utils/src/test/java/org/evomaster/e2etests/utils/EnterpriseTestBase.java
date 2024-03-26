package org.evomaster.e2etests.utils;

import com.google.inject.Injector;
import kotlin.Unit;
import org.apache.commons.io.FileUtils;
import org.evomaster.client.java.controller.EmbeddedSutController;
import org.evomaster.client.java.controller.InstrumentedSutStarter;
import org.evomaster.client.java.controller.api.dto.SutInfoDto;
import org.evomaster.client.java.controller.internal.SutController;
import org.evomaster.client.java.instrumentation.shared.ClassName;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.evomaster.client.java.instrumentation.staticstate.MethodReplacementPreserveSemantics;
import org.evomaster.client.java.instrumentation.staticstate.ObjectiveRecorder;
import org.evomaster.client.java.instrumentation.staticstate.UnitsInfoRecorder;
import org.evomaster.client.java.utils.SimpleLogger;
import org.evomaster.core.EMConfig;
import org.evomaster.core.Main;
import org.evomaster.core.StaticCounter;
import org.evomaster.core.logging.TestLoggingUtil;
import org.evomaster.core.output.OutputFormat;
import org.evomaster.core.output.compiler.CompilerForTestGenerated;
import org.evomaster.core.problem.api.ApiWsIndividual;
import org.evomaster.core.remote.service.RemoteController;
import org.evomaster.core.remote.service.RemoteControllerImplementation;
import org.evomaster.core.search.Solution;
import org.evomaster.core.search.service.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.opentest4j.AssertionFailedError;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public abstract class EnterpriseTestBase {

    protected static InstrumentedSutStarter embeddedStarter;
    protected static String baseUrlOfSut;
    protected static SutController controller;
    protected static RemoteController remoteController;
    protected static int controllerPort;


    private final static int STARTING_SEED = 42;
    protected int defaultSeed = STARTING_SEED;

    public final static String TESTS_OUTPUT_ROOT_FOLDER = "target/em-tests/";

    @BeforeAll
    public static void initInstrumentation(){
        /*
            Make sure we init agent immediately... this is to avoid classes (eg kotlin library)
            being not instrumented when tests start (as controllers might load them)
         */
        InstrumentedSutStarter.loadAgent();

        /*
            avoid boot-time info across e2e tests
         */
        ObjectiveRecorder.reset(true);

        UnitsInfoRecorder.reset();
    }

    @AfterAll
    public static void tearDown() {

        assertTimeoutPreemptively(Duration.ofMinutes(2), () -> {
            boolean stopped = remoteController.stopSUT();
            stopped = embeddedStarter.stop() && stopped;

            assertTrue(stopped);
        });

        SimpleLogger.setThreshold(SimpleLogger.Level.INFO);
    }


    @BeforeEach
    public void initTest() {

        //make sure the search can use all instrumentation, even the ones using non-preserving semantics
        MethodReplacementPreserveSemantics.shouldPreserveSemantics = false;

        //in case it was modified in a previous test in the same class
        defaultSeed = STARTING_SEED;

        StaticCounter.Companion.reset();

        assertTimeoutPreemptively(Duration.ofMinutes(2), () -> {
            boolean reset = remoteController.resetSUT();
            assertTrue(reset);
        });

        SimpleLogger.setThreshold(SimpleLogger.Level.DEBUG);

    }

    @AfterEach
    public void clean(){
        /*
            clean boot-time targets achieved during executing generated tests
            thus, it would have a side effect, ie, the boot-time targets would be
            correctly collect only by the first test, unless we stop the sut after
            each test.
         */
        ObjectiveRecorder.reset(true);

        //as this can be modified (eg before running generated tests), make sure to put it back to default
        MethodReplacementPreserveSemantics.shouldPreserveSemantics = false;
    }


    protected Injector init(List<String> args) {
        return Main.init(args.toArray(new String[0]));
    }


    protected String outputFolderPath(String outputFolderName){
        return TESTS_OUTPUT_ROOT_FOLDER + outputFolderName;
    }


    protected void runAndCheckDeterminism(int iterations, Consumer<List<String>> lambda){

        List<String> args =  new ArrayList<>(Arrays.asList(
                "--createTests", "false",
                "--seed", "42",
                "--showProgress", "false",
                "--avoidNonDeterministicLogs", "true",
                "--sutControllerPort", "" + controllerPort,
                "--maxActionEvaluations", "" + iterations,
                "--stoppingCriterion", "FITNESS_EVALUATIONS",
                "--useTimeInFeedbackSampling" , "false"
        ));

        StaticCounter.Companion.reset();
        String firstRun = TestLoggingUtil.Companion.runWithDeterministicLogger(
                () -> {lambda.accept(args); return Unit.INSTANCE;}
        );

        StaticCounter.Companion.reset();
        String secondRun = TestLoggingUtil.Companion.runWithDeterministicLogger(
                () -> {lambda.accept(args); return Unit.INSTANCE;}
        );

        assertEquals(firstRun, secondRun);
    }

    protected void runTestHandlingFlaky(
            String outputFolderName,
            String fullClassName,
            int iterations,
            boolean createTests,
            Consumer<List<String>> lambda) throws Throwable{

        runTestHandlingFlaky(outputFolderName, fullClassName, iterations, createTests, lambda, 3);
    }


    protected void runTestHandlingFlaky(
            String outputFolderName,
            String fullClassName,
            int iterations,
            boolean createTests,
            Consumer<List<String>> lambda,
            int timeoutMinutes) throws Throwable{

        runTestHandlingFlaky(outputFolderName, fullClassName, null, iterations, createTests, lambda, timeoutMinutes);
    }

    protected void runTestHandlingFlaky(
            String outputFolderName,
            String fullClassName,
            List<String> terminations,
            int iterations,
            boolean createTests,
            Consumer<List<String>> lambda,
            int timeoutMinutes) throws Throwable{


//        MethodReplacementPreserveSemantics.shouldPreserveSemantics = true;

        List<ClassName> classNames = new ArrayList<>();

        String splitType = "";

        if(terminations == null || terminations.isEmpty()){
            classNames.add(new ClassName(fullClassName));
            splitType = "NONE";
        } else {
            for (String termination : terminations) {
                classNames.add(new ClassName(fullClassName + termination));
            }
            splitType = "CLUSTER";
        }

         /*
            Years have passed, still JUnit 5 does not handle global test timeouts :(
            https://github.com/junit-team/junit5/issues/80
         */
        String finalSplitType = splitType;
        assertTimeoutPreemptively(Duration.ofMinutes(timeoutMinutes), () -> {
            ClassName className = new ClassName(fullClassName);
            clearGeneratedFiles(outputFolderName, classNames);

            handleFlaky(
                    () -> {
                        List<String> args = getArgsWithCompilation(iterations, outputFolderName, className, createTests, finalSplitType, "FALSE");
                        defaultSeed++;
                        lambda.accept(new ArrayList<>(args));
                    }
            );
        });
    }

    protected void runTestHandlingFlakyAndCompilation(
            String label,
            int iterations,
            Consumer<List<String>> lambda) throws Throwable {

        runTestHandlingFlakyAndCompilation(label, "org.bar."+label, iterations, lambda);
    }


    protected void runTestHandlingFlakyAndCompilation(
            String outputFolderName,
            String fullClassName,
            int iterations,
            Consumer<List<String>> lambda) throws Throwable {

        runTestHandlingFlakyAndCompilation(outputFolderName, fullClassName, null, iterations, true, lambda, 3);
    }

    protected void runTestHandlingFlakyAndCompilation(
            String outputFolderName,
            String fullClassName,
            List<String> terminations,
            int iterations,
            Consumer<List<String>> lambda) throws Throwable {

        runTestHandlingFlakyAndCompilation(outputFolderName, fullClassName, terminations, iterations, true, lambda, 3);
    }

    protected void runTestHandlingFlakyAndCompilation(
            String outputFolderName,
            String fullClassName,
            List<String> terminations,
            int iterations,
            boolean createTests,
            Consumer<List<String>> lambda,
            int timeoutMinutes) throws Throwable {

        runTestHandlingFlaky(outputFolderName, fullClassName, terminations, iterations, createTests,lambda, timeoutMinutes);

        if (terminations == null) terminations = Arrays.asList("");
        //BMR: this is where I should handle multiples???
        if (createTests){
            // TODO: Verify
            MethodReplacementPreserveSemantics.shouldPreserveSemantics = true;

            for (String termination : terminations) {
                assertTimeoutPreemptively(Duration.ofMinutes(2), () -> {
                    ClassName className = new ClassName(fullClassName + termination);
                    clearCompiledFiles(className);
                    //the first one goes through, but for the second generated files appear to not be clean.
                    compileRunAndVerifyTests(outputFolderName, className);
                });
            }
        }
    }

    protected void runTestHandlingFlakyAndCompilation(
            String outputFolderName,
            String fullClassName,
            int iterations,
            boolean createTests,
            Consumer<List<String>> lambda,
            int timeoutMinutes) throws Throwable {

        runTestHandlingFlaky(outputFolderName, fullClassName, iterations, createTests,lambda, timeoutMinutes);

        if (createTests){
            assertTimeoutPreemptively(Duration.ofMinutes(2), () -> {
                ClassName className = new ClassName(fullClassName);
                compileRunAndVerifyTests(outputFolderName, className);
            });
        }
    }

    protected void compileRunAndVerifyTests(String outputFolderName, ClassName className){

        ExecutionTracer.setKillSwitch(false); //make sure it is not on

        Class<?> klass = loadClass(className);
        assertNull(klass);

        compile(outputFolderName);
        klass = loadClass(className);
        assertNotNull(klass);

        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);

        TestExecutionSummary summary = JUnitTestRunner.runTestsInClass(klass);
        summary.printFailuresTo(pw, 100);
        String failures = writer.toString();

        assertTrue(summary.getContainersFoundCount() > 0);
        assertEquals(0, summary.getContainersFailedCount(), failures);
        assertTrue(summary.getContainersSucceededCount() > 0);
        assertTrue(summary.getTestsFoundCount() > 0);
        assertEquals(0, summary.getTestsFailedCount(), failures);
        assertTrue(summary.getTestsSucceededCount() > 0);
    }

    protected void clearGeneratedFiles(String outputFolderName, List<ClassName> testClassNames){

        File folder = new File(outputFolderPath(outputFolderName));
        try{
            FileUtils.deleteDirectory(folder);
        }catch (Exception e){
            throw new RuntimeException(e);
        }

        for (ClassName testClassName : testClassNames){
            clearCompiledFiles(testClassName);
        }

    }

    protected void clearGeneratedFiles(String outputFolderName, ClassName testClassName){
        List<ClassName> classNames = new ArrayList<ClassName>();
        classNames.add(testClassName);

        clearGeneratedFiles(outputFolderName, classNames);
    }

    /**
     *  As E2E generates test cases, we need delete the previous ones from previous runs, to make sure
     *  we are running the latest generated.
     *
     *  However, if you run everything from "org.", those existing tests from previous run will be loaded into
     *  the JVM, and so checks for their presence after this is executed will pass... and so the E2E will fail
     */
    protected void clearCompiledFiles(ClassName testClassName){
        String byteCodePath = "target/test-classes/" + testClassName.getAsResourcePath();
        File compiledFile = new File(byteCodePath);
        boolean result = compiledFile.delete();
    }

    protected Class<?> loadClass(ClassName className){
        try {
            return this.getClass().getClassLoader().loadClass(className.getFullNameWithDots());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    protected void compile(String outputFolderName){

        CompilerForTestGenerated.INSTANCE.compile(
                OutputFormat.KOTLIN_JUNIT_5,
                new File(outputFolderPath(outputFolderName)),
                new File("target/test-classes")
        );
    }

    protected List<String> getArgsWithCompilation(int iterations, String outputFolderName, ClassName testClassName){
        return getArgsWithCompilation(iterations, outputFolderName, testClassName, true);
    }

    protected List<String> getArgsWithCompilation(int iterations, String outputFolderName, ClassName testClassName, boolean createTests){
        return getArgsWithCompilation(iterations, outputFolderName, testClassName, createTests, "NONE", "FALSE");
    }
    protected List<String> getArgsWithCompilation(int iterations, String outputFolderName, ClassName testClassName, boolean createTests, String split, String summary){

        return new ArrayList<>(Arrays.asList(
                "--createTests", "" + createTests,
                "--seed", "" + defaultSeed,
                "--useTimeInFeedbackSampling" , "false",
                "--sutControllerPort", "" + controllerPort,
                "--maxActionEvaluations", "" + iterations,
                "--stoppingCriterion", "FITNESS_EVALUATIONS",
                "--outputFolder", outputFolderPath(outputFolderName),
                "--outputFormat", OutputFormat.KOTLIN_JUNIT_5.toString(),
                //FIXME: should avoid deprecated option, but then need TODO update how class files are deleted from FS
                "--testSuiteFileName", testClassName.getFullNameWithDots(),
                "--testSuiteSplitType", split,
                "--expectationsActive", "TRUE",
                "--executiveSummary", summary
        ));
    }


    /**
     * Unfortunately JUnit 5 does not handle flaky tests, and Maven is not upgraded yet.
     * See https://github.com/junit-team/junit5/issues/1558#issuecomment-414701182
     *
     * TODO: once that issue is fixed (if it will ever be fixed), then this method
     * will no longer be needed.
     * Actually no... as we change the seed at each re-execution... so we still need
     * this code
     *
     * @param lambda
     * @throws Throwable
     */
    protected void handleFlaky(Runnable lambda) throws Throwable{

        int attempts = 3;
        Throwable error = null;

        for(int i=0; i<attempts; i++){

            try{
                lambda.run();
                return;
            }catch (OutOfMemoryError e){
                throw e;
            }
            catch (AssertionError e){
                //this happens if there is an internal bug in EM which leads to a broken invariant.
                //however, we do not want to rethrow JUnit asserts, as those are related to the
                //flakiness we want to deal with here
                if(e instanceof AssertionFailedError){
                    error = e;
                } else {
                    throw e;
                }
            }
            catch (Throwable t){
                error = t;
            }
        }

        throw error;
    }

    protected static void initClass(EmbeddedSutController controller) throws Exception {
        initClass(controller, new EMConfig());
    }

    /**
     * Passing config here is only needed when dealing with Method Replacements, as it impacts
     * what gets instrumented
     */
    protected static void initClass(EmbeddedSutController controller, EMConfig config) throws Exception {

        EnterpriseTestBase.controller = controller;

        embeddedStarter = new InstrumentedSutStarter(controller);
        embeddedStarter.start();

        controllerPort = embeddedStarter.getControllerServerPort();

        remoteController = new RemoteControllerImplementation("localhost", controllerPort, true, true, config);
        boolean started = remoteController.startSUT();
        assertTrue(started, "Failed to start the SUT");

        SutInfoDto dto = remoteController.getSutInfo();
        assertNotNull(dto, "Failed to get SUT info");

        baseUrlOfSut = dto.baseUrlOfSUT;
        assertNotNull(baseUrlOfSut, "No URL of the SUT was retrieved");

        System.out.println("Remote controller running on port " + controllerPort);
        System.out.println("SUT listening on " + baseUrlOfSut);
    }


    protected void assertInsertionIntoTable(Solution<? extends ApiWsIndividual> solution, String tableName) {

        boolean ok = solution.getIndividuals().stream().anyMatch(
                ind -> ind.getIndividual().seeDbActions().stream().anyMatch(
                        da -> da.getTable().getName().equalsIgnoreCase(tableName))
        );

        assertTrue(ok);
    }

    /**
     * Bit of a helper method to replace the default output format for testing purposes.
     *
     * @param args - the list of arguments
     * @param outputFormat - the desired output format
     */
    protected void setOutputFormat(List<String> args, OutputFormat outputFormat){
        if (outputFormat != null){
            args.replaceAll(s -> s.replace(OutputFormat.KOTLIN_JUNIT_5.name(), outputFormat.name()));
        }
    }

    /**
     * assert a certain text in the generated tests
     * @param outputFolder the folder where the test is
     * @param className the complete test name
     * @param content is the content to check
     */
    protected void assertTextInTests(String outputFolder, String className, String content) {
        String path = outputFolderPath(outputFolder)+ "/"+String.join("/", className.split("\\."))+".kt";
        Path test = Paths.get(path);
        try {
            boolean ok = Files.lines(test).anyMatch(l-> l.contains(content));
            String msg = "Cannot find "+content+" in "+className+" in "+outputFolder;
            assertTrue(ok, msg);
        }catch (IOException e){
            throw new IllegalStateException("Fail to get the test "+className+" in "+outputFolder+" with error "+ e.getMessage());
        }

    }

    /**
     * This function is used to retrieve the item with the identifier "key" in the Solution "sol"
     * from Statistics. Returns the value of the element if the element exists, otherwise returns null.
     * @param sol
     * @param key
     * @return
     */
    protected String findValueOfItemWithKeyInStats(Solution sol, String key) {

        for(Statistics.Pair el : (List<Statistics.Pair>)sol.getStatistics()) {
            if (el.getHeader().equals(key)) {
                return el.getElement();
            }
        }

        return null;
    }

}
