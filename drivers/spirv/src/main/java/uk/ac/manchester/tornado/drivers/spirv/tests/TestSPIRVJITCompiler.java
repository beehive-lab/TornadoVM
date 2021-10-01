/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.spirv.tests;

import java.lang.reflect.Method;
import java.util.Arrays;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDriver;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * Testing the SPIRV JIT Compiler and integration with the TornadoVM SPIRV
 * Runtime.
 */
public class TestSPIRVJITCompiler {

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

    public static class MetaCompilation {
        TaskMetaData taskMeta;
        SPIRVInstalledCode spirvCode;

        public MetaCompilation(TaskMetaData taskMeta, SPIRVInstalledCode openCLCode) {
            this.taskMeta = taskMeta;
            this.spirvCode = openCLCode;
        }

        public TaskMetaData getTaskMeta() {
            return taskMeta;
        }

        public SPIRVInstalledCode getSpirvCode() {
            return spirvCode;
        }
    }

    public MetaCompilation compileMethod(Class<?> klass, String methodName, int[] a, int[] b, double[] c) {

        // Get the method object to be compiled
        Method methodToCompile = getMethodForName(klass, methodName);

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the Graal Resolved Java Method
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);

        // Get the backend from TornadoVM
        SPIRVBackend spirvBackend = tornadoRuntime.getDriver(SPIRVDriver.class).getDefaultBackend();

        TornadoDevice device = tornadoRuntime.getDriver(SPIRVDriver.class).getDefaultDevice();

        System.out.println("Selecting Device: " + device.getPhysicalDevice().getDeviceName());

        // Create a new task for TornadoVM
        TaskMetaData taskMeta = TaskMetaData.create(new ScheduleMetaData("s0"), methodToCompile.getName(), methodToCompile);
        taskMeta.setDevice(device);

        // FIXME <TODO> <COMPLETE>
        // XXX: Compile the SPIRV Code

        // Install the SPIRV code into the VM

        return new MetaCompilation(taskMeta, null);
    }

    public void test() {

        // input data
        final int N = 128;
        int[] a = new int[N];
        int[] b = new int[N];
        double[] c = new double[N];

        Arrays.fill(a, -10);
        Arrays.fill(b, 10);

        MetaCompilation compileMethod = compileMethod(TestSPIRVJITCompiler.class, "methodToCompile", a, b, c);

        // FIXME <TODO> <COMPLETE>

    }

    public static void main(String[] args) {
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVJITCompiler");
        new TestSPIRVJITCompiler().test();
    }

}
