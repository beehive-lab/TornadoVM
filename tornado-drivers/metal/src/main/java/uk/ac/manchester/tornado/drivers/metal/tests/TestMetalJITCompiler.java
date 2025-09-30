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
 * Authors: Juan Fumero, Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.drivers.metal.tests;

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
import uk.ac.manchester.tornado.drivers.metal.MetalBackendImpl;
import uk.ac.manchester.tornado.drivers.metal.Metal;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalInstalledCode;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalProviders;
import uk.ac.manchester.tornado.drivers.metal.graal.backend.MetalBackend;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResult;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompiler;
import uk.ac.manchester.tornado.drivers.metal.runtime.MetalTornadoDevice;
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
 * Test the Metal JIT Compiler and connection with the Tornado Runtime
 * Environment.
 *
 */
public class TestMetalJITCompiler {

    public static void methodToCompile(int[] a, int[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = 0.12f * a[i] * b[i];
        }
    }

    public static void main(String[] args) {
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.metal.tests.TestMetalJITCompiler");
        new TestMetalJITCompiler().test();
    }

    public MetaCompilation compileMethod(long executionPlanId, Class<?> klass, String methodName, MetalTornadoDevice tornadoDevice, Object... parameters) {

        // Get the method object to be compiled
        Method methodToCompile = CompilerUtil.getMethodForName(klass, methodName);

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the Graal Resolved Java Method
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);

        // Get the backend from TornadoVM
        MetalBackend openCLBackend = tornadoRuntime.getBackend(MetalBackendImpl.class).getDefaultBackend();

        // Get the default Metal device
        TornadoDevice device = tornadoRuntime.getBackend(MetalBackendImpl.class).getDefaultDevice();

        // Create a new task for TornadoVM
        ScheduleContext scheduleMetaData = new ScheduleContext("s0");
        // Create a compilable task
        CompilableTask compilableTask = new CompilableTask(scheduleMetaData, "t0", methodToCompile, parameters);
        TaskDataContext taskMeta = compilableTask.meta();
        taskMeta.setDevice(device);

        // 1. Build Common Compiler Phase (Sketcher)
        // Utility to build a sketcher and insert into the HashMap for fast LookUps
        Providers providers = openCLBackend.getProviders();
        TornadoSuitesProvider suites = openCLBackend.getTornadoSuites();
        Sketch sketch = CompilerUtil.buildSketchForJavaMethod(resolvedJavaMethod, taskMeta, providers, suites);

        MetalCompilationResult compilationResult = MetalCompiler.compileSketchForDevice(sketch, compilableTask, (MetalProviders) providers, openCLBackend, new EmptyProfiler());

        // Install the Metal Code in the VM
        MetalInstalledCode openCLCode = tornadoDevice.getDeviceContext().installCode(executionPlanId, compilationResult);

        return new MetaCompilation(taskMeta, openCLCode);
    }

    public void runWithMetalAPI(Long executionPlanId, MetalTornadoDevice tornadoDevice, MetalInstalledCode openCLCode, TaskDataContext taskMeta, int[] a, int[] b, float[] c) {
        Metal.run(executionPlanId, tornadoDevice, openCLCode, taskMeta, new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY }, a, b, c);
    }

    public void run(MetalTornadoDevice tornadoDevice, MetalInstalledCode openCLCode, TaskDataContext taskMeta, int[] a, int[] b, float[] c) {
        // First we allocate, A, B and C
        DataObjectState stateA = new DataObjectState();
        XPUDeviceBufferState objectStateA = stateA.getDeviceBufferState(tornadoDevice);

        DataObjectState stateB = new DataObjectState();
        XPUDeviceBufferState objectStateB = stateB.getDeviceBufferState(tornadoDevice);

        DataObjectState stateC = new DataObjectState();
        XPUDeviceBufferState objectStateC = stateC.getDeviceBufferState(tornadoDevice);

        tornadoDevice.allocateObjects(new Object[] { a, b, c }, 0, new DeviceBufferState[] { objectStateA, objectStateB, objectStateC }, new Access[] {Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY});

        long executionPlanId = 0;

        // Copy-IN A
        tornadoDevice.ensurePresent(executionPlanId, a, objectStateA, null, 0, 0);
        // Copy-IN B
        tornadoDevice.ensurePresent(executionPlanId, b, objectStateB, null, 0, 0);

        // Create call wrapper
        KernelStackFrame callWrapper = tornadoDevice.createKernelStackFrame(executionPlanId, 3, Access.NONE);

        // Fill header of call callWrapper with empty values
        callWrapper.setKernelContext(new HashMap<>());

        callWrapper.addCallArgument(objectStateA.getXPUBuffer().toBuffer(), true);
        callWrapper.addCallArgument(objectStateB.getXPUBuffer().toBuffer(), true);
        callWrapper.addCallArgument(objectStateC.getXPUBuffer().toBuffer(), true);

        // Run the code
        openCLCode.launchWithoutDependencies(executionPlanId, callWrapper, null, taskMeta, 0);

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
        long executionPlanId = 0;

        MetalTornadoDevice tornadoDevice = (MetalTornadoDevice) TornadoCoreRuntime.getTornadoRuntime().getBackend(MetalBackendImpl.class).getDefaultDevice();

        MetaCompilation compileMethod = compileMethod(executionPlanId, TestMetalJITCompiler.class, "methodToCompile", tornadoDevice, a, b, c);

        // Check with all internal APIs
        run(tornadoDevice, (MetalInstalledCode) compileMethod.getInstalledCode(), compileMethod.getTaskMeta(), a, b, c);


        // Check with Metal API
        runWithMetalAPI(executionPlanId, tornadoDevice, (MetalInstalledCode) compileMethod.getInstalledCode(), compileMethod.getTaskMeta(), a, b, c);

        boolean correct = true;
        for (int i = 0; i < c.length; i++) {
            float seq = 0.12f * a[i] * b[i];
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

}
