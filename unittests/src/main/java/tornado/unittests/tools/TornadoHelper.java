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
			buffer.append("\tRunning test :\t" + ColorsTerminal.BLUE + m.getName() + ColorsTerminal.RESET);
			Request request = Request.method(klass, m.getName());
			Result result = new JUnitCore().run(request);
			if (result.wasSuccessful()) {
				buffer.append("\t ................ " + ColorsTerminal.GREEN + " [PASS] " + ColorsTerminal.RESET + "\n");
				successCounter++;
			} else {
				buffer.append("\t................. " + ColorsTerminal.RED + " [PASS] " + ColorsTerminal.RESET + "\n");
				failedCounter++;
				for (Failure f : result.getFailures()) {
					buffer.append("\t\t" + f.getMessage());
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
