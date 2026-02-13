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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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

import jdk.graal.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.memory.DeviceBufferState;
import uk.ac.manchester.tornado.drivers.common.MetaCompilation;
import uk.ac.manchester.tornado.drivers.common.utils.CompilerUtil;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackendImpl;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDevice;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVProviders;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompiler;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.profiler.EmptyProfiler;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.DataObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * Testing the SPIR-V JIT Compiler and integration with the TornadoVM SPIR-V Runtime.
 *
 * How to run?
 *
 * <code>
 * $ tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVJITCompiler
 * </code>
 */
public class TestSPIRVJITCompiler {

    public static void methodToCompile(int[] a, int[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = 0.12f * a[i] * b[i];
        }
    }

    public static void main(String[] args) {
        System.out.println("Running Native: uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVJITCompiler");
        new TestSPIRVJITCompiler().test();
    }

    public MetaCompilation compileMethod(long executionPlanId, Class<?> klass, String methodName, Object... parameters) {

        // Get the method object to be compiled
        Method methodToCompile = CompilerUtil.getMethodForName(klass, methodName);

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the Graal Resolved Java Method
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);

        // Get the backend from TornadoVM
        SPIRVBackend spirvBackend = tornadoRuntime.getBackend(SPIRVBackendImpl.class).getDefaultBackend();

        // Obtain the SPIR-V device
        TornadoDevice device = tornadoRuntime.getBackend(SPIRVBackendImpl.class).getDefaultDevice();

        // Create a new task for TornadoVM
        ScheduleContext scheduleMetaData = new ScheduleContext("s0");
        // Create a compilable task
        CompilableTask compilableTask = new CompilableTask(scheduleMetaData, "t0", methodToCompile, parameters);
        TaskDataContext taskMeta = compilableTask.meta();
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
        SPIRVInstalledCode spirvInstalledCode = (SPIRVInstalledCode) spirvDevice.getDeviceContext().installBinary(executionPlanId, spirvCompilationResult);

        return new MetaCompilation(taskMeta, spirvInstalledCode);
    }

    public void run(SPIRVTornadoDevice spirvTornadoDevice, SPIRVInstalledCode installedCode, TaskDataContext taskMeta, int[] a, int[] b, float[] c) {
        // First we allocate, A, B and C
        DataObjectState stateA = new DataObjectState();
        XPUDeviceBufferState objectStateA = stateA.getDeviceBufferState(spirvTornadoDevice);

        DataObjectState stateB = new DataObjectState();
        XPUDeviceBufferState objectStateB = stateB.getDeviceBufferState(spirvTornadoDevice);

        DataObjectState stateC = new DataObjectState();
        XPUDeviceBufferState objectStateC = stateC.getDeviceBufferState(spirvTornadoDevice);

        spirvTornadoDevice.allocateObjects(new Object[] { a, b, c }, 0, new DeviceBufferState[] { objectStateA, objectStateB, objectStateC }, new Access[]{Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY});

        final long executionPlanId = 0;

        // Copy-IN A
        spirvTornadoDevice.ensurePresent(executionPlanId, a, objectStateA, null, 0, 0);
        // Copy-IN B
        spirvTornadoDevice.ensurePresent(executionPlanId, b, objectStateB, null, 0, 0);

        // Create call stack wrapper for SPIR-V with 3 arguments
        // TODO: have a look
        KernelStackFrame stackFrame = spirvTornadoDevice.createKernelStackFrame(executionPlanId, 3, Access.NONE);
        stackFrame.setKernelContext(new HashMap<>());

        // Add kernel arguments to the SPIR-V Stack Frame
        stackFrame.addCallArgument(objectStateA.getXPUBuffer().toBuffer(), true);
        stackFrame.addCallArgument(objectStateB.getXPUBuffer().toBuffer(), true);
        stackFrame.addCallArgument(objectStateC.getXPUBuffer().toBuffer(), true);

        // Launch the generated kernel
        installedCode.launchWithoutDependencies(executionPlanId, stackFrame, null, taskMeta, 0);

        // Transfer the result from the device to the host (this is a blocking call)
        spirvTornadoDevice.streamOutBlocking(executionPlanId, c, 0, objectStateC, null);
    }

    public void test() {

        // input data
        final int N = 128;
        int[] a = new int[N];
        int[] b = new int[N];
        float[] c = new float[N];
        final long executionPlanId = 0;

        Arrays.fill(a, -10);
        Arrays.fill(b, 10);

        // Obtain the SPIR-V binary from the Java method
        MetaCompilation compileMethod = compileMethod(executionPlanId, TestSPIRVJITCompiler.class, "methodToCompile", a, b, c);

        TornadoDevice device = TornadoCoreRuntime.getTornadoRuntime().getBackend(SPIRVBackendImpl.class).getDefaultDevice();

        run((SPIRVTornadoDevice) device, (SPIRVInstalledCode) compileMethod.getInstalledCode(), compileMethod.getTaskMeta(), a, b, c);

        boolean correct = true;
        for (int i = 0; i < c.length; i++) {
            float seq = 0.12f * a[i] * b[i];
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
}
