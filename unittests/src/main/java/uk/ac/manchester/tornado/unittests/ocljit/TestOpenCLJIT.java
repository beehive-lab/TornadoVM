/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Authors: Juan Fumero, Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.unittests.ocljit;

import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.api.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.drivers.opencl.OCLDriver;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.runtime.TornadoRuntime;

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
