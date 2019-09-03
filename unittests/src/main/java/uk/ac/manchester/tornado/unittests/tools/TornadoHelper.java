/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
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

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;

public class TornadoHelper {

    public static void printResult(Result result) {
        System.out.printf("Test ran: %s, Failed: %s%n", result.getRunCount(), result.getFailureCount());
    }

    public static void printResult(int success, int failed) {
        System.out.printf("Test ran: %s, Failed: %s%n", (success + failed), failed);
    }

    public static void printResult(int success, int failed, StringBuffer buffer) {
        buffer.append(String.format("Test ran: %s, Failed: %s%n", (success + failed), failed));
    }

    public static boolean getProperty(String property) {
        if (System.getProperty(property) != null) {
            if (System.getProperty(property).toLowerCase().equals("true")) {
                return true;
            }
            return false;
        }
        return false;
    }

    public static Method getMethodForName(Class<?> klass, String nameMethod) {
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
     * 
     */
    public static ArrayList<Method> getTestMethods(Class<?> klass) {
        Method[] methods = klass.getMethods();
        ArrayList<Method> methodsToTest = new ArrayList<>();
        for (Method m : methods) {
            Annotation[] annotations = m.getAnnotations();
            boolean testEnabled = false;
            boolean ignoreTest = false;
            for (Annotation a : annotations) {
                if (a instanceof org.junit.Ignore) {
                    ignoreTest = true;
                } else if (a instanceof org.junit.Test) {
                    testEnabled = true;
                }
            }
            if (testEnabled & !ignoreTest) {
                methodsToTest.add(m);
            }
        }
        return methodsToTest;
    }

    public static void printInfoTest(String buffer, int success, int fails) {
        System.out.println(buffer);
        System.out.print("\n\t");
        printResult(success, fails);
    }

    public static void runTestVerbose(String klassName, String methodName) throws ClassNotFoundException {

        Class<?> klass = Class.forName(klassName);
        ArrayList<Method> methodsToTest = new ArrayList<>();
        if (methodName == null) {
            methodsToTest = getTestMethods(klass);
        } else {
            Method method = TornadoHelper.getMethodForName(klass, methodName);
            methodsToTest.add(method);
        }

        StringBuffer bufferConsole = new StringBuffer();
        StringBuffer bufferFile = new StringBuffer();

        int successCounter = 0;
        int failedCounter = 0;

        bufferConsole.append("Test: " + klass + "\n");
        bufferFile.append("Test: " + klass + "\n");

        for (Method m : methodsToTest) {
            String message = String.format("%-50s", "\tRunning test: " + ColorsTerminal.BLUE + m.getName() + ColorsTerminal.RESET);
            bufferConsole.append(message);
            bufferFile.append(message);

            Request request = Request.method(klass, m.getName());
            Result result = new JUnitCore().run(request);

            if (result.wasSuccessful()) {
                message = String.format("%20s", " ................ " + ColorsTerminal.GREEN + " [PASS] " + ColorsTerminal.RESET + "\n");
                bufferConsole.append(message);
                bufferFile.append(message);
                successCounter++;
            } else {
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

        printResult(successCounter, failedCounter, bufferConsole);
        printResult(successCounter, failedCounter, bufferFile);
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

    public static void runTestClassAndMethod(String klassName, String methodName) throws ClassNotFoundException {
        Request request = Request.method(Class.forName(klassName), methodName);
        Result result = new JUnitCore().run(request);
        printResult(result);
    }

    public static void runTestClass(String klassName) throws ClassNotFoundException {
        Request request = Request.aClass(Class.forName(klassName));
        Result result = new JUnitCore().run(request);
        printResult(result);
    }
}
