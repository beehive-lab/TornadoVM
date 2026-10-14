/*
 * Copyright (c) 2013-2020, 2022-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.unittests.tools;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.listeners.TestExecutionSummary.Failure;

import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP16NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP8NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceDynamicParallelismNotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceMMANotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoLibraryTaskNotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoNoOpenCLPlatformException;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMetalNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMOpenCLNotSupported;
import uk.ac.manchester.tornado.unittests.tools.Exceptions.UnsupportedConfigurationException;

public class TornadoHelper {


    // Exceptions that mean a test cannot run on the current configuration/device - it is NOT a failure.
    // Kept in sync with the per-method classification in the verbose runner below.
    private static final List<Class<? extends Throwable>> UNSUPPORTED_EXCEPTIONS = List.of(UnsupportedConfigurationException.class, TornadoNoOpenCLPlatformException.class,
            TornadoVMMultiDeviceNotSupported.class, TornadoVMOpenCLNotSupported.class, TornadoVMMetalNotSupported.class, TornadoVMCUDANotSupported.class,
            TornadoDeviceFP64NotSupported.class, TornadoDeviceFP16NotSupported.class, TornadoDeviceMMANotSupported.class,
            TornadoDeviceDynamicParallelismNotSupported.class, TornadoLibraryTaskNotSupported.class);

    private static boolean isUnsupported(Throwable exception) {
        return UNSUPPORTED_EXCEPTIONS.stream().anyMatch(type -> type.isInstance(exception));
    }

    /**
     * Runs the selected tests on the JUnit Platform and returns the execution summary.
     */
    private static TestExecutionSummary run(DiscoverySelector selector) {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selector).build(), listener);
        return listener.getSummary();
    }

    private static String stackTrace(Throwable exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /**
     * Name of the test (Class#method) or, for a failure outside any test (e.g., in a static initializer), of the class.
     */
    private static String testName(Failure failure) {
        return failure.getTestIdentifier().getSource().map(source -> switch (source) {
            case MethodSource method -> method.getClassName() + "#" + method.getMethodName();
            case ClassSource klass -> klass.getClassName();
            default -> failure.getTestIdentifier().getDisplayName();
        }).orElse(failure.getTestIdentifier().getDisplayName());
    }

    private static void printResult(TestExecutionSummary result, String selection) {
        // Separate genuinely-failed tests from those skipped as unsupported on this configuration, so the terse
        // (non-verbose) runner does not report unsupported tests as failures (which reads as false regressions).
        long notSupported = result.getFailures().stream().filter(failure -> isUnsupported(failure.getException())).count();
        long failed = result.getFailures().size() - notSupported;
        // One line per failed test, so that tornado-test can tell failures from whitelisted ones without -V.
        for (Failure failure : result.getFailures()) {
            if (!isUnsupported(failure.getException())) {
                System.out.println(FAILED_TEST_MARKER + testName(failure) + " - " + failure.getException());
            }
        }
        if (result.getTestsFoundCount() == 0) {
            // JUnit 4 failed a request with no runnable methods; the JUnit Platform reports an empty, successful run.
            System.out.println(FAILED_TEST_MARKER + selection + " - no tests found: nothing was run");
            failed++;
        }
        System.out.printf("Test ran: %s, Failed: %s, Unsupported: %s%n", result.getTestsStartedCount(), failed, notSupported);
    }

    private static void printResult(int success, int failed, int notSupported) {
        System.out.printf("Test ran: %s, Failed: %s, Unsupported: %s%n", (success + failed + notSupported), failed, notSupported);
    }

    private static void printResult(int success, int failed, int notSupported, StringBuilder buffer) {
        buffer.append(String.format("Test ran: %s, Failed: %s, Unsupported: %s%n", (success + failed + notSupported), failed, notSupported));
    }

    /**
     * Marker printed on stderr before each test of the verbose runner. If the JVM crashes (e.g., a segfault in a
     * driver), the last marker names the test that crashed. tornado-test reads it and removes it from the output.
     */
    static final String RUNNING_TEST_MARKER = "[TornadoTestRunner] running ";

    /**
     * Prefix of the line the non-verbose runner prints for each failed test, read by tornado-test.
     */
    static final String FAILED_TEST_MARKER = "[TEST FAILED] ";

    /**
     * Console output of the verbose runner. Each line is written as soon as it is complete, not buffered until the
     * class finishes, so that a JVM crash still shows every result up to the test that crashed. A test result is
     * one line, written once the test finishes, so output the test prints itself does not split it.
     */
    private static final class ConsoleOutput {
        private final StringBuilder line = new StringBuilder();

        void append(String message) {
            line.append(message);
            if (message.endsWith("\n")) {
                System.out.print(line);
                System.out.flush();
                line.setLength(0);
            }
        }
    }

    static boolean getProperty(String property) {
        if (System.getProperty(property) != null) {
            return System.getProperty(property).toLowerCase().equals("true");
        }
        return false;
    }

    private static Method getMethodForName(Class<?> klass, String nameMethod) {
        Method method = null;
        for (Method m : klass.getMethods()) {
            if (m.getName().equals(nameMethod)) {
                method = m;
            }
        }
        return method;
    }

    /**
     * It returns the list of methods with the {@link Test} annotation.
     */
    private static TestSuiteCollection getTestMethods(Class<?> klass) {
        Method[] methods = klass.getMethods();
        ArrayList<Method> methodsToTest = new ArrayList<>();
        HashSet<Method> unsupportedMethods = new HashSet<>();
        for (Method m : methods) {
            Annotation[] annotations = m.getAnnotations();
            boolean testEnabled = false;
            boolean ignoreTest = false;
            for (Annotation a : annotations) {
                if (a instanceof Disabled) {
                    ignoreTest = true;
                } else if (a instanceof Test) {
                    testEnabled = true;
                } else if (a instanceof TornadoNotSupported) {
                    testEnabled = true;
                    unsupportedMethods.add(m);
                }
            }
            if (testEnabled & !ignoreTest) {
                methodsToTest.add(m);
            }
        }
        return new TestSuiteCollection(methodsToTest, unsupportedMethods);
    }

    static void runTestVerbose(String klassName, String methodName) throws ClassNotFoundException {

        Class<?> klass = Class.forName(klassName);
        ArrayList<Method> methodsToTest = new ArrayList<>();
        TestSuiteCollection suite = null;
        if (methodName == null) {
            suite = getTestMethods(klass);
            methodsToTest = suite.methodsToTest;
        } else {
            Method method = TornadoHelper.getMethodForName(klass, methodName);
            methodsToTest.add(method);
        }

        ConsoleOutput bufferConsole = new ConsoleOutput();
        StringBuilder bufferFile = new StringBuilder();

        int successCounter = 0;
        int failedCounter = 0;
        int notSupported = 0;

        bufferConsole.append("Test: " + klass);
        bufferFile.append("Test: " + klass);
        if (methodName != null) {
            bufferConsole.append("#" + methodName);
            bufferFile.append("#" + methodName);
        }
        bufferConsole.append("\n");
        bufferFile.append("\n");

        for (Method m : methodsToTest) {
            String message = String.format("%-50s", "\tRunning test: " + ColorsTerminal.BLUE + m.getName() + ColorsTerminal.RESET);
            bufferConsole.append(message);
            bufferFile.append(message);

            if (suite != null && suite.unsupportedMethods.contains(m)) {
                message = String.format("%20s", " ................ " + ColorsTerminal.YELLOW + " [NOT VALID TEST: UNSUPPORTED] " + ColorsTerminal.RESET + "\n");
                bufferConsole.append(message);
                bufferFile.append(message);
                notSupported++;
                continue;
            }

            System.err.println(RUNNING_TEST_MARKER + klass.getName() + "#" + m.getName());
            System.err.flush();
            TestExecutionSummary result = run(selectMethod(klass, m.getName()));

            if (result.getTestsFoundCount() == 0) {
                message = String.format("%20s", " ................ " + ColorsTerminal.RED + " [FAILED] " + ColorsTerminal.RESET + "\n") + "\t\t\\_[REASON] No test found: nothing was run\n";
                bufferConsole.append(message);
                bufferFile.append(message);
                failedCounter++;
            } else if (result.getFailures().isEmpty()) {
                message = String.format("%20s", " ................ " + ColorsTerminal.GREEN + " [PASS] " + ColorsTerminal.RESET + "\n");
                bufferConsole.append(message);
                bufferFile.append(message);
                successCounter++;
            } else {
                // If UnsupportedConfigurationException is thrown this means that test did not
                // fail, it simply can't be run on current configuration
                Optional<Failure> unsupportedConfiguration = result.getFailures().stream().filter(e -> (e.getException() instanceof UnsupportedConfigurationException)).findFirst();
                if (unsupportedConfiguration.isPresent()) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.PURPLE + " [UNSUPPORTED CONFIGURATION: " + unsupportedConfiguration.get().getException().getMessage() + "] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoNoOpenCLPlatformException))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.PURPLE + " [OPENCL CONFIGURATION UNSUPPORTED] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoVMMultiDeviceNotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.PURPLE + " [[UNSUPPORTED] MULTI-DEVICE CONFIGURATION REQUIRED] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoVMOpenCLNotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.PURPLE + " [OPENCL CONFIGURATION UNSUPPORTED] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoVMMetalNotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.PURPLE + " [METAL CONFIGURATION UNSUPPORTED] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoVMCUDANotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.PURPLE + " [CUDA CONFIGURATION UNSUPPORTED] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoDeviceFP64NotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.YELLOW + " [FP64 UNSUPPORTED FOR CURRENT DEVICE] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoDeviceFP16NotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.YELLOW + " [FP16 UNSUPPORTED FOR CURRENT DEVICE] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoDeviceMMANotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.YELLOW + " [MMA UNSUPPORTED FOR CURRENT DEVICE] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoDeviceDynamicParallelismNotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.YELLOW + " [DYNAMIC PARALLELISM UNSUPPORTED FOR CURRENT DEVICE] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoLibraryTaskNotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.YELLOW + " [LIBRARY TASK UNSUPPORTED FOR CURRENT DEVICE] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoDeviceFP8NotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.YELLOW + " [FP8 UNSUPPORTED FOR CURRENT DEVICE] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                message = String.format("%20s", " ................ " + ColorsTerminal.RED + " [FAILED] " + ColorsTerminal.RESET + "\n");
                bufferConsole.append(message);
                bufferFile.append(message);
                failedCounter++;
                for (Failure failure : result.getFailures()) {
                    bufferConsole.append("\t\t\\_[REASON] " + failure.getException().getMessage() + "\n");
                    bufferFile.append("\t\t\\_[REASON] " + failure.getException().getMessage() + "\n\t" + stackTrace(failure.getException()) + "\n" + failure.getTestIdentifier()
                            .getDisplayName() + "\n" + failure.getException());
                }
            }
        }

        printResult(successCounter, failedCounter, notSupported);
        printResult(successCounter, failedCounter, notSupported, bufferFile);
        System.out.println();

        // Print File
        try (BufferedWriter w = new BufferedWriter(new FileWriter("tornado_unittests.log", true))) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            w.write("\n" + dateFormat.format(date) + "\n");
            w.write(bufferFile.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void runTestClassAndMethod(String klassName, String methodName) throws ClassNotFoundException {
        printResult(run(selectMethod(Class.forName(klassName), methodName)), klassName + "#" + methodName);
    }

    static void runTestClass(String klassName) throws ClassNotFoundException {
        printResult(run(selectClass(Class.forName(klassName))), klassName);
    }

    static class TestSuiteCollection {
        ArrayList<Method> methodsToTest;
        HashSet<Method> unsupportedMethods;

        TestSuiteCollection(ArrayList<Method> methodsToTest, HashSet<Method> unsupportedMethods) {
            this.methodsToTest = methodsToTest;
            this.unsupportedMethods = unsupportedMethods;
        }
    }
}
