/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Juan Fumero
 *
 */
package tornado.unittests.tools;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class TornadoHelper {

	public static void printResult(Result result) {
		System.out.printf("Test ran: %s, Failed: %s%n", result.getRunCount(), result.getFailureCount());
	}

	public static void printResult(int success, int failed) {
		System.out.printf("Test ran: %s, Failed: %s%n", (success + failed), failed);
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

	public static ArrayList<Method> getMethodToTest(Class<?> klass) {
		Method[] methods = klass.getMethods();
		ArrayList<Method> methodsToTest = new ArrayList<>();
		for (Method m : methods) {
			Annotation[] annotations = m.getAnnotations();
			boolean testEnabled = false;
			for (Annotation a : annotations) {
				if (a instanceof org.junit.Test) {
					testEnabled = true;
				} else if (a instanceof org.junit.Ignore) {
					testEnabled = false;
				}
			}
			if (testEnabled) {
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

	public static void runTestverbose(String klassName) throws ClassNotFoundException {

		Class<?> klass = Class.forName(klassName);
		ArrayList<Method> methodsToTest = getMethodToTest(klass);

		StringBuffer buffer = new StringBuffer();

		int successCounter = 0;
		int failedCounter = 0;

		buffer.append("Test: " + klass + "\n");

		for (Method m : methodsToTest) {
			String s = String.format("%-50s",
					"\tRunning test: " + ColorsTerminal.BLUE + m.getName() + ColorsTerminal.RESET);
			buffer.append(s);
			Request request = Request.method(klass, m.getName());
			Result result = new JUnitCore().run(request);
			if (result.wasSuccessful()) {
				s = String.format("%20s",
						" ................ " + ColorsTerminal.GREEN + " [PASS] " + ColorsTerminal.RESET + "\n");
				buffer.append(s);
				successCounter++;
			} else {
				s = String.format("%20s",
						" ................ " + ColorsTerminal.RED + " [FAILED] " + ColorsTerminal.RESET + "\n");
				buffer.append(s);
				failedCounter++;
				for (Failure f : result.getFailures()) {
					buffer.append("\t\t" + f.getMessage() + f.getTrace());
				}
			}
		}

		System.out.println(buffer);
		printResult(successCounter, failedCounter);
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
