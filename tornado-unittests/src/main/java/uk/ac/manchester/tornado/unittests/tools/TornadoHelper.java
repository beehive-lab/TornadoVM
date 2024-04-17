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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP16NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoNoOpenCLPlatformException;
import uk.ac.manchester.tornado.unittests.common.SPIRVOptNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMOpenCLNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMPTXNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMSPIRVNotSupported;
import uk.ac.manchester.tornado.unittests.tools.Exceptions.UnsupportedConfigurationException;

public class TornadoHelper {

    public static final boolean OPTIMIZE_LOAD_STORE_SPIRV = Boolean.parseBoolean(System.getProperty("tornado.spirv.loadstore", "False"));

    private static void printResult(Result result) {
        System.out.printf("Test ran: %s, Failed: %s%n", result.getRunCount(), result.getFailureCount());
    }

    private static void printResult(int success, int failed, int notSupported) {
        System.out.printf("Test ran: %s, Failed: %s, Unsupported: %s%n", (success + failed + notSupported), failed, notSupported);
    }

    private static void printResult(int success, int failed, int notSupported, StringBuilder buffer) {
        buffer.append(String.format("Test ran: %s, Failed: %s, Unsupported: %s%n", (success + failed + notSupported), failed, notSupported));
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
     * It returns the list of methods with the {@link @Test} annotation.
     */
    private static TestSuiteCollection getTestMethods(Class<?> klass) {
        Method[] methods = klass.getMethods();
        ArrayList<Method> methodsToTest = new ArrayList<>();
        HashSet<Method> unsupportedMethods = new HashSet<>();
        HashSet<Method> spirvNotSupportedMethods = new HashSet<>();
        for (Method m : methods) {
            Annotation[] annotations = m.getAnnotations();
            boolean testEnabled = false;
            boolean ignoreTest = false;
            for (Annotation a : annotations) {
                if (a instanceof org.junit.Ignore) {
                    ignoreTest = true;
                } else if (a instanceof org.junit.Test) {
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
        return new TestSuiteCollection(methodsToTest, unsupportedMethods, spirvNotSupportedMethods);
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

        StringBuilder bufferConsole = new StringBuilder();
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

            Request request = Request.method(klass, m.getName());
            Result result = new JUnitCore().run(request);

            if (result.wasSuccessful()) {
                message = String.format("%20s", " ................ " + ColorsTerminal.GREEN + " [PASS] " + ColorsTerminal.RESET + "\n");
                bufferConsole.append(message);
                bufferFile.append(message);
                successCounter++;
            } else {
                // If UnsupportedConfigurationException is thrown this means that test did not
                // fail, it simply can't be run on current configuration
                if (result.getFailures().stream().filter(e -> (e.getException() instanceof UnsupportedConfigurationException)).count() > 0) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.PURPLE + " [UNSUPPORTED CONFIGURATION: At least 2 accelerators are required] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoVMPTXNotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.PURPLE + " [PTX CONFIGURATION UNSUPPORTED] " + ColorsTerminal.RESET + "\n");
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

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof TornadoVMSPIRVNotSupported))) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.PURPLE + " [SPIRV CONFIGURATION UNSUPPORTED] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    notSupported++;
                    continue;
                }

                if (result.getFailures().stream().anyMatch(e -> (e.getException() instanceof SPIRVOptNotSupported)) && OPTIMIZE_LOAD_STORE_SPIRV) {
                    message = String.format("%20s", " ................ " + ColorsTerminal.RED + " [SPIRV OPTIMIZATION NOT SUPPORTED] " + ColorsTerminal.RESET + "\n");
                    bufferConsole.append(message);
                    bufferFile.append(message);
                    failedCounter++;
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

                message = String.format("%20s", " ................ " + ColorsTerminal.RED + " [FAILED] " + ColorsTerminal.RESET + "\n");
                bufferConsole.append(message);
                bufferFile.append(message);
                failedCounter++;
                for (Failure failure : result.getFailures()) {
                    bufferConsole.append("\t\t\\_[REASON] " + failure.getMessage() + "\n");
                    bufferFile.append("\t\t\\_[REASON] " + failure.getMessage() + "\n\t" + failure.getTrace() + "\n" + failure.getDescription() + "\n" + failure.getException());
                }
            }
        }

        printResult(successCounter, failedCounter, notSupported, bufferConsole);
        printResult(successCounter, failedCounter, notSupported, bufferFile);
        System.out.println(bufferConsole);

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
        Request request = Request.method(Class.forName(klassName), methodName);
        Result result = new JUnitCore().run(request);
        printResult(result);
    }

    static void runTestClass(String klassName) throws ClassNotFoundException {
        Request request = Request.aClass(Class.forName(klassName));
        Result result = new JUnitCore().run(request);
        printResult(result);
    }

    static class TestSuiteCollection {
        ArrayList<Method> methodsToTest;
        HashSet<Method> unsupportedMethods;

        TestSuiteCollection(ArrayList<Method> methodsToTest, HashSet<Method> unsupportedMethods, HashSet<Method> spirvUnsupportedMethods) {
            this.methodsToTest = methodsToTest;
            this.unsupportedMethods = unsupportedMethods;
        }
    }
}
