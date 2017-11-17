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
 */
package tornado.unittests.ocljit;

import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.Parallel;
import tornado.api.meta.ScheduleMetaData;
import tornado.api.meta.TaskMetaData;
import tornado.drivers.opencl.OCLDriver;
import tornado.drivers.opencl.OpenCL;
import tornado.drivers.opencl.graal.OCLInstalledCode;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import tornado.drivers.opencl.graal.compiler.OCLCompiler;
import tornado.runtime.TornadoRuntime;

/**
 * Test the OpenCL JIT Compiler and connection with the Tornado Runtime
 * Environment.
 *
 */
public class TestOpenCLJIT {

    /**
     * Method to be compile by Tornado into OpenCL
     * 
     * @param a
     * @param b
     * @param c
     */
    public static void testMethodToCompile(int[] a, int[] b, double[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = 0.12 * a[i] * b[i];
        }
    }

    private Method getMethodForName(Class<?> klass, String nameMethod) {
        Method method = null;
        for (Method m : klass.getMethods()) {
            if (m.getName().equals(nameMethod)) {
                method = m;
            }
        }
        return method;
    }

    @Test
    public void testJIT01() {

        // input data
        final int N = 128;
        int[] a = new int[N];
        int[] b = new int[N];
        double[] c = new double[N];

        Arrays.fill(a, -10);
        Arrays.fill(b, 10);

        Method methodToCompile = getMethodForName(TestOpenCLJIT.class, "testMethodToCompile");
        assertNotNull(methodToCompile);

        // Test Tornado Runtime
        TornadoRuntime tornadoRuntime = TornadoRuntime.getTornadoRuntime();
        assertNotNull(tornadoRuntime);

        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);
        assertNotNull(resolvedJavaMethod);

        // Get the backend from Tornado
        OCLBackend openCLBackend = tornadoRuntime.getDriver(OCLDriver.class).getDefaultBackend();
        assertNotNull(openCLBackend);

        // Create a new task for Tornado
        TaskMetaData task = TaskMetaData.create(new ScheduleMetaData("ID0"), methodToCompile.getName(), methodToCompile, false);

        // Compile the code for OpenCL
        OCLCompilationResult compilationResult = OCLCompiler.compileCodeForDevice(resolvedJavaMethod, new Object[] { a, b, c }, task, (OCLProviders) openCLBackend.getProviders(), openCLBackend);

        // Obtain the code
        OCLInstalledCode openCLCode = OpenCL.defaultDevice().getDeviceContext().installCode(compilationResult);

        assertNotNull(openCLCode);

        // XXX: Check the code is correct

    }
}
