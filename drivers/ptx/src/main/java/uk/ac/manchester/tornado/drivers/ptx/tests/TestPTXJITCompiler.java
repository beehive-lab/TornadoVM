/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.ptx.tests;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.ptx.PTX;
import uk.ac.manchester.tornado.drivers.ptx.PTXDriver;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXProviders;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompiler;
import uk.ac.manchester.tornado.drivers.ptx.runtime.PTXTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * Test the PTX JIT Compiler and connection with the Tornado Runtime
 * Environment.
 *
 */
public class TestPTXJITCompiler {

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
        PTXInstalledCode ptxCode;

        public MetaCompilation(TaskMetaData taskMeta, PTXInstalledCode ptxCode) {
            this.taskMeta = taskMeta;
            this.ptxCode = ptxCode;
        }

        public TaskMetaData getTaskMeta() {
            return taskMeta;
        }

        public PTXInstalledCode getPtxCode() {
            return ptxCode;
        }

    }

    public MetaCompilation compileMethod(Class<?> klass, String methodName, PTXTornadoDevice tornadoDevice, int[] a, int[] b, double[] c) {

        // Get the method object to be compiled
        Method methodToCompile = getMethodForName(klass, methodName);

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the Graal Resolved Java Method
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);

        // Get the backend from TornadoVM
        PTXBackend ptxBackend = tornadoRuntime.getDriver(PTXDriver.class).getDefaultBackend();

        // Create a new task for Tornado
        TaskMetaData taskMeta = TaskMetaData.create(new ScheduleMetaData("S0"), methodToCompile.getName(), methodToCompile);
        taskMeta.setDevice(PTX.defaultDevice());

        // Compile the PTX code
        PTXCompilationResult compilationResult = PTXCompiler.compileCodeForDevice(resolvedJavaMethod, new Object[] { a, b, c }, taskMeta, (PTXProviders) ptxBackend.getProviders(), ptxBackend, 0);

        // Install the PTX Code in the VM
        TornadoInstalledCode ptxCode = tornadoDevice.getDeviceContext().installCode(compilationResult, resolvedJavaMethod.getName());

        return new MetaCompilation(taskMeta, (PTXInstalledCode) ptxCode);
    }

    public void runWithPTXAPI(PTXTornadoDevice tornadoDevice, PTXInstalledCode ptxCode, TaskMetaData taskMeta, int[] a, int[] b, double[] c) {
        PTX.run(tornadoDevice, ptxCode, taskMeta, new Access[] { Access.READ, Access.READ, Access.WRITE }, new Object[] { a, b, c });
    }

    public void run(PTXTornadoDevice tornadoDevice, PTXInstalledCode ptxCode, TaskMetaData taskMeta, int[] a, int[] b, double[] c) {
        // First we allocate, A, B and C
        GlobalObjectState stateA = new GlobalObjectState();
        DeviceObjectState objectStateA = stateA.getDeviceState(tornadoDevice);

        GlobalObjectState stateB = new GlobalObjectState();
        DeviceObjectState objectStateB = stateB.getDeviceState(tornadoDevice);

        GlobalObjectState stateC = new GlobalObjectState();
        DeviceObjectState objectStateC = stateC.getDeviceState(tornadoDevice);

        // Copy-IN A
        tornadoDevice.ensurePresent(a, objectStateA, null, 0, 0);
        // Copy-IN B
        tornadoDevice.ensurePresent(b, objectStateB, null, 0, 0);
        // Alloc C
        tornadoDevice.ensureAllocated(c, 0, objectStateC);

        // Create stack
        CallStack stack = tornadoDevice.createStack(3);

        // Fill header of call stack with empty values
        stack.setHeader(new HashMap<>());

        stack.push(a, objectStateA);
        stack.push(b, objectStateB);
        stack.push(c, objectStateC);

        // Run the code
        ptxCode.launchWithoutDependencies(stack, null, taskMeta, 0);

        // Obtain the result
        tornadoDevice.streamOutBlocking(c, 0, objectStateC, null);
    }

    public void test() {

        // input data
        final int N = 128;
        int[] a = new int[N];
        int[] b = new int[N];
        double[] c = new double[N];

        Arrays.fill(a, -10);
        Arrays.fill(b, 10);

        PTXTornadoDevice tornadoDevice = PTX.defaultDevice();

        MetaCompilation compileMethod = compileMethod(TestPTXJITCompiler.class, "methodToCompile", tornadoDevice, a, b, c);

        // Check with all internal APIs
        run(tornadoDevice, compileMethod.ptxCode, compileMethod.taskMeta, a, b, c);

        // Check with PTX API
        runWithPTXAPI(tornadoDevice, compileMethod.ptxCode, compileMethod.taskMeta, a, b, c);

        boolean correct = true;
        for (int i = 0; i < c.length; i++) {
            double seq = 0.12 * a[i] * b[i];
            if (Math.abs(c[i] - seq) > 0.01) {
                System.err.println(i + " Fault result = " + seq + " " + c[i]);
                correct = false;
                break;
            }
        }
        if (!correct) {
            System.out.println(" ................ [FAIL]");
        } else {
            System.out.println(" ................ [PASS]");
        }

    }

    public static void main(String[] args) {
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXJITCompiler");
        new TestPTXJITCompiler().test();
    }

}
