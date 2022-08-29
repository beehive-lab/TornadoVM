/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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
import java.util.HashMap;

import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.drivers.common.CompilerUtil;
import uk.ac.manchester.tornado.drivers.common.MetaCompilation;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDriver;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVProviders;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompiler;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.KernelArgs;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.profiler.EmptyProfiler;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * Testing the SPIRV JIT Compiler and integration with the TornadoVM SPIRV
 * Runtime.
 *
 * How to run?
 *
 * <code>
 *     $ tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVJITCompiler
 * </code>
 */
public class TestSPIRVJITCompiler {

    public static void methodToCompile(int[] a, int[] b, double[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = 0.12 * a[i] * b[i];
        }
    }

    public MetaCompilation compileMethod(Class<?> klass, String methodName, Object... parameters) {

        // Get the method object to be compiled
        Method methodToCompile = CompilerUtil.getMethodForName(klass, methodName);

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the Graal Resolved Java Method
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);

        // Get the backend from TornadoVM
        SPIRVBackend spirvBackend = tornadoRuntime.getDriver(SPIRVDriver.class).getDefaultBackend();

        // Obtain the SPIR-V device
        TornadoDevice device = tornadoRuntime.getDriver(SPIRVDriver.class).getDefaultDevice();

        // Create a new task for TornadoVM
        ScheduleMetaData scheduleMetaData = new ScheduleMetaData("s0");
        // Create a compilable task
        CompilableTask compilableTask = new CompilableTask(scheduleMetaData, "t0", methodToCompile, parameters);
        TaskMetaData taskMeta = compilableTask.meta();
        taskMeta.setDevice(device);

        // 1. Build Common Compiler Phase (Sketcher)
        // Utility to build a sketcher and insert into the HashMap for fast LookUps
        Providers providers = spirvBackend.getProviders();
        TornadoSuitesProvider suites = spirvBackend.getTornadoSuites();
        Sketch sketch = CompilerUtil.buildSketchForJavaMethod(resolvedJavaMethod, taskMeta, providers, suites);

        // 2. Function f: Sketch -> SPIR-V Compiled Code
        SPIRVCompilationResult spirvCompilationResult = SPIRVCompiler.compileSketchForDevice(sketch, compilableTask, (SPIRVProviders) spirvBackend.getProviders(), spirvBackend, new EmptyProfiler());

        // 3. Install the SPIR-V code into the VM
        SPIRVDevice spirvDevice = (SPIRVDevice) device.getDeviceContext().getDevice();
        SPIRVInstalledCode spirvInstalledCode = (SPIRVInstalledCode) spirvDevice.getDeviceContext().installBinary(spirvCompilationResult);

        return new MetaCompilation(taskMeta, spirvInstalledCode);
    }

    public void run(SPIRVTornadoDevice spirvTornadoDevice, SPIRVInstalledCode installedCode, TaskMetaData taskMeta, int[] a, int[] b, double[] c) {
        // First we allocate, A, B and C
        GlobalObjectState stateA = new GlobalObjectState();
        DeviceObjectState objectStateA = stateA.getDeviceState(spirvTornadoDevice);

        GlobalObjectState stateB = new GlobalObjectState();
        DeviceObjectState objectStateB = stateB.getDeviceState(spirvTornadoDevice);

        GlobalObjectState stateC = new GlobalObjectState();
        DeviceObjectState objectStateC = stateC.getDeviceState(spirvTornadoDevice);

        spirvTornadoDevice.allocateBulk(new Object[] { a, b, c }, 0, new TornadoDeviceObjectState[] { objectStateA, objectStateB, objectStateC });

        // Copy-IN A
        spirvTornadoDevice.ensurePresent(a, objectStateA, null, 0, 0);
        // Copy-IN B
        spirvTornadoDevice.ensurePresent(b, objectStateB, null, 0, 0);

        // Create call stack wrapper for SPIR-V with 3 arguments
        KernelArgs callWrapper = spirvTornadoDevice.createCallWrapper(3);
        callWrapper.setKernelContext(new HashMap<>());

        // Add kernel arguments to the SPIR-V Call Stack
        callWrapper.addCallArgument(objectStateA.getObjectBuffer().toBuffer(), true);
        callWrapper.addCallArgument(objectStateB.getObjectBuffer().toBuffer(), true);
        callWrapper.addCallArgument(objectStateC.getObjectBuffer().toBuffer(), true);

        // Launch the generated kernel
        installedCode.launchWithoutDependencies(callWrapper, null, taskMeta, 0);

        // Transfer the result from the device to the host (this is a blocking call)
        spirvTornadoDevice.streamOutBlocking(c, 0, objectStateC, null);
    }

    public void test() {

        // input data
        final int N = 128;
        int[] a = new int[N];
        int[] b = new int[N];
        double[] c = new double[N];

        Arrays.fill(a, -10);
        Arrays.fill(b, 10);

        // Obtain the SPIR-V binary from the Java method
        MetaCompilation compileMethod = compileMethod(TestSPIRVJITCompiler.class, "methodToCompile", a, b, c);

        TornadoDevice device = TornadoCoreRuntime.getTornadoRuntime().getDriver(SPIRVDriver.class).getDefaultDevice();

        run((SPIRVTornadoDevice) device, (SPIRVInstalledCode) compileMethod.getInstalledCode(), compileMethod.getTaskMeta(), a, b, c);

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
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVJITCompiler");
        new TestSPIRVJITCompiler().test();
    }
}
