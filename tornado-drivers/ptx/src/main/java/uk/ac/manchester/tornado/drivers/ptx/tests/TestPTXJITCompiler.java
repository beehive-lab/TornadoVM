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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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

import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.memory.DeviceBufferState;
import uk.ac.manchester.tornado.drivers.common.MetaCompilation;
import uk.ac.manchester.tornado.drivers.common.utils.CompilerUtil;
import uk.ac.manchester.tornado.drivers.ptx.PTX;
import uk.ac.manchester.tornado.drivers.ptx.PTXBackendImpl;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXProviders;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompiler;
import uk.ac.manchester.tornado.drivers.ptx.runtime.PTXTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.KernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.profiler.EmptyProfiler;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.DataObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * Test the PTX JIT Compiler and connection with the Tornado Runtime
 * Environment.
 *
 */
public class TestPTXJITCompiler {

    public static void methodToCompile(int[] a, int[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = 0.12f * a[i] * b[i];
        }
    }

    public static void main(String[] args) {
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXJITCompiler");
        new TestPTXJITCompiler().test();
    }

    public MetaCompilation compileMethod(long executionPlanId, Class<?> klass, String methodName, PTXTornadoDevice tornadoDevice, Object... parameters) {

        // Get the method object to be compiled
        Method methodToCompile = CompilerUtil.getMethodForName(klass, methodName);

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the Graal Resolved Java Method
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);

        // Get the backend from TornadoVM
        PTXBackend ptxBackend = tornadoRuntime.getBackend(PTXBackendImpl.class).getDefaultBackend();

        TornadoDevice device = tornadoRuntime.getBackend(PTXBackendImpl.class).getDefaultDevice();

        // Create a new task for TornadoVM
        ScheduleContext scheduleMetaData = new ScheduleContext("s0");
        // Create a compilable task
        CompilableTask compilableTask = new CompilableTask(scheduleMetaData, "t0", methodToCompile, parameters);
        TaskDataContext taskMeta = compilableTask.meta();
        taskMeta.setDevice(device);

        // 1. Build Common Compiler Phase (Sketcher)
        // Utility to build a sketcher and insert into the HashMap for fast LookUps
        Providers providers = ptxBackend.getProviders();
        TornadoSuitesProvider suites = ptxBackend.getTornadoSuites();
        Sketch sketch = CompilerUtil.buildSketchForJavaMethod(resolvedJavaMethod, taskMeta, providers, suites);

        // Compile the PTX code
        PTXCompilationResult compilationResult = PTXCompiler.compileSketchForDevice(sketch, compilableTask, (PTXProviders) providers, ptxBackend, new EmptyProfiler());

        // Install the PTX Code in the VM
        TornadoInstalledCode ptxCode = tornadoDevice.getDeviceContext().installCode(executionPlanId, compilationResult, resolvedJavaMethod.getName());

        return new MetaCompilation(taskMeta, (PTXInstalledCode) ptxCode);
    }

    public void runWithPTXAPI(PTXTornadoDevice tornadoDevice, PTXInstalledCode ptxCode, TaskDataContext taskMeta, int[] a, int[] b, float[] c) {
        PTX.run(tornadoDevice, ptxCode, taskMeta, new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY }, a, b, c);
    }

    public void run(PTXTornadoDevice tornadoDevice, PTXInstalledCode ptxCode, TaskDataContext taskMeta, int[] a, int[] b, float[] c) {
        // First we allocate, A, B and C
        DataObjectState stateA = new DataObjectState();
        XPUDeviceBufferState objectStateA = stateA.getDeviceBufferState(tornadoDevice);

        DataObjectState stateB = new DataObjectState();
        XPUDeviceBufferState objectStateB = stateB.getDeviceBufferState(tornadoDevice);

        DataObjectState stateC = new DataObjectState();
        XPUDeviceBufferState objectStateC = stateC.getDeviceBufferState(tornadoDevice);

        tornadoDevice.allocateObjects(new Object[] { a, b, c }, 0, new DeviceBufferState[] { objectStateA, objectStateB, objectStateC }, new Access[]{Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY});

        final long executionPlanId = 0;
        // Copy-IN A
        tornadoDevice.ensurePresent(executionPlanId, a, objectStateA, null, 0, 0);
        // Copy-IN B
        tornadoDevice.ensurePresent(executionPlanId, b, objectStateB, null, 0, 0);

        // Create call wrapper
        KernelStackFrame callWrapper = tornadoDevice.createKernelStackFrame(executionPlanId, 3, Access.NONE);

        callWrapper.setKernelContext(new HashMap<>());

        callWrapper.addCallArgument(objectStateA.getXPUBuffer().toBuffer(), true);
        callWrapper.addCallArgument(objectStateB.getXPUBuffer().toBuffer(), true);
        callWrapper.addCallArgument(objectStateC.getXPUBuffer().toBuffer(), true);

        // Run the code
        ptxCode.launchWithoutDependencies(executionPlanId, callWrapper, null, taskMeta, 0);

        // Obtain the result
        tornadoDevice.streamOutBlocking(executionPlanId, c, 0, objectStateC, null);
    }

    public void test() {

        // input data
        final int N = 128;
        int[] a = new int[N];
        int[] b = new int[N];
        float[] c = new float[N];

        Arrays.fill(a, -10);
        Arrays.fill(b, 10);
        final long executionPlanId = 0;

        PTXTornadoDevice tornadoDevice = (PTXTornadoDevice) TornadoCoreRuntime.getTornadoRuntime().getBackend(PTXBackendImpl.class).getDefaultDevice();

        MetaCompilation compileMethod = compileMethod(executionPlanId, TestPTXJITCompiler.class, "methodToCompile", tornadoDevice, a, b, c);

        // Check with all internal APIs
        run(tornadoDevice, (PTXInstalledCode) compileMethod.getInstalledCode(), compileMethod.getTaskMeta(), a, b, c);

        // Check with PTX API
        runWithPTXAPI(tornadoDevice, (PTXInstalledCode) compileMethod.getInstalledCode(), compileMethod.getTaskMeta(), a, b, c);

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
