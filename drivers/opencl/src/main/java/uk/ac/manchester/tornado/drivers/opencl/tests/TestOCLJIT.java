/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.tests;

import java.lang.reflect.Method;
import java.util.Arrays;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.drivers.opencl.OCLDriver;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * Test the OpenCL JIT Compiler and connection with the Tornado Runtime
 * Environment.
 *
 */
public class TestOCLJIT {

    public static void methodToCompile(int[] a, int[] b, double[] c) {
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

    public OCLInstalledCode compileAndRunMethod(int[] a, int[] b, double[] c) {

        Method methodToCompile = getMethodForName(TestOCLJIT.class, "methodToCompile");

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);

        // Get the backend from TornadoVM
        OCLBackend openCLBackend = tornadoRuntime.getDriver(OCLDriver.class).getDefaultBackend();

        // Create a new task for Tornado
        TaskMetaData taskMeta = TaskMetaData.create(new ScheduleMetaData("S0"), methodToCompile.getName(), methodToCompile, false);

        // Compile the code for OpenCL
        OCLCompilationResult compilationResult = OCLCompiler.compileCodeForDevice(resolvedJavaMethod, new Object[] { a, b, c }, taskMeta, (OCLProviders) openCLBackend.getProviders(), openCLBackend);

        // Obtain the code
        OCLInstalledCode openCLCode = OpenCL.defaultDevice().getDeviceContext().installCode(compilationResult);

        String code = openCLCode.getSourceCode();
        System.out.println("GENERATED CODE : " + code);

        // Run the code
        // openCLCode.launchWithoutDeps(stack, taskMeta, 0);

        return openCLCode;
    }

    public void testJIT01() {

        // input data
        final int N = 128;
        int[] a = new int[N];
        int[] b = new int[N];
        double[] c = new double[N];

        Arrays.fill(a, -10);
        Arrays.fill(b, 10);

        compileAndRunMethod(a, b, c);
    }

    public static void main(String[] args) {
        new TestOCLJIT().testJIT01();
    }

}
