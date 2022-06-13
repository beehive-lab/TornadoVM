/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
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
import java.util.HashMap;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.drivers.opencl.OCLDriver;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.KernelArgs;
import uk.ac.manchester.tornado.runtime.profiler.EmptyProfiler;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * Test the OpenCL JIT Compiler and connection with the Tornado Runtime
 * Environment.
 *
 */
public class TestOpenCLJITCompiler {

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
        OCLInstalledCode openCLCode;

        public MetaCompilation(TaskMetaData taskMeta, OCLInstalledCode openCLCode) {
            this.taskMeta = taskMeta;
            this.openCLCode = openCLCode;
        }

        public TaskMetaData getTaskMeta() {
            return taskMeta;
        }

        public OCLInstalledCode getOpenCLCode() {
            return openCLCode;
        }

    }

    public MetaCompilation compileMethod(Class<?> klass, String methodName, OCLTornadoDevice tornadoDevice, int[] a, int[] b, double[] c) {

        // Get the method object to be compiled
        Method methodToCompile = getMethodForName(klass, methodName);

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the Graal Resolved Java Method
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);

        // Get the backend from TornadoVM
        OCLBackend openCLBackend = tornadoRuntime.getDriver(OCLDriver.class).getDefaultBackend();

        // Create a new task for Tornado
        TaskMetaData taskMeta = TaskMetaData.create(new ScheduleMetaData("S0"), methodToCompile.getName(), methodToCompile);
        taskMeta.setDevice(OpenCL.defaultDevice());

        // Compile the code for OpenCL
        OCLCompilationResult compilationResult = OCLCompiler.compileCodeForDevice(resolvedJavaMethod, new Object[] { a, b, c }, taskMeta, (OCLProviders) openCLBackend.getProviders(), openCLBackend,
                new EmptyProfiler());

        // Install the OpenCL Code in the VM
        OCLInstalledCode openCLCode = tornadoDevice.getDeviceContext().installCode(compilationResult);

        return new MetaCompilation(taskMeta, openCLCode);
    }

    public void runWithOpenCLAPI(OCLTornadoDevice tornadoDevice, OCLInstalledCode openCLCode, TaskMetaData taskMeta, int[] a, int[] b, double[] c) {
        OpenCL.run(tornadoDevice, openCLCode, taskMeta, new Access[] { Access.READ, Access.READ, Access.WRITE }, a, b, c);
    }

    public void run(OCLTornadoDevice tornadoDevice, OCLInstalledCode openCLCode, TaskMetaData taskMeta, int[] a, int[] b, double[] c) {
        // First we allocate, A, B and C
        GlobalObjectState stateA = new GlobalObjectState();
        DeviceObjectState objectStateA = stateA.getDeviceState(tornadoDevice);

        GlobalObjectState stateB = new GlobalObjectState();
        DeviceObjectState objectStateB = stateB.getDeviceState(tornadoDevice);

        GlobalObjectState stateC = new GlobalObjectState();
        DeviceObjectState objectStateC = stateC.getDeviceState(tornadoDevice);

        tornadoDevice.allocateBulk(new Object[] { a, b, c }, 0, new TornadoDeviceObjectState[] { objectStateA, objectStateB, objectStateC });

        // Copy-IN A
        tornadoDevice.ensurePresent(a, objectStateA, null, 0, 0);
        // Copy-IN B
        tornadoDevice.ensurePresent(b, objectStateB, null, 0, 0);

        // Create call wrapper
        KernelArgs callWrapper = tornadoDevice.createCallWrapper(3);

        // Fill header of call callWrapper with empty values
        callWrapper.setKernelContext(new HashMap<>());

        callWrapper.addCallArgument(objectStateA.getObjectBuffer().toBuffer(), true);
        callWrapper.addCallArgument(objectStateB.getObjectBuffer().toBuffer(), true);
        callWrapper.addCallArgument(objectStateC.getObjectBuffer().toBuffer(), true);

        // Run the code
        openCLCode.launchWithoutDependencies(callWrapper, null, taskMeta, 0);

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

        OCLTornadoDevice tornadoDevice = OpenCL.defaultDevice();

        MetaCompilation compileMethod = compileMethod(TestOpenCLJITCompiler.class, "methodToCompile", tornadoDevice, a, b, c);

        // Check with all internal APIs
        run(tornadoDevice, compileMethod.openCLCode, compileMethod.taskMeta, a, b, c);

        // Check with OpenCL API
        runWithOpenCLAPI(tornadoDevice, compileMethod.openCLCode, compileMethod.taskMeta, a, b, c);

        boolean correct = true;
        for (int i = 0; i < c.length; i++) {
            double seq = 0.12 * a[i] * b[i];
            if (Math.abs(c[i] - seq) > 0.01) {
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
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler");
        new TestOpenCLJITCompiler().test();
    }

}
