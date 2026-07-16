/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.tests;

import java.lang.reflect.Method;

import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.vectors.Half2;
import uk.ac.manchester.tornado.drivers.common.utils.CompilerUtil;
import uk.ac.manchester.tornado.drivers.cuda.CUDABackendImpl;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAProviders;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.CUDABackend;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompiler;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.profiler.EmptyProfiler;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * Compiles kernels that use the packed half2 API and asserts the generated CUDA C
 * actually contains the packed 32-bit accesses and cuda_fp16.h intrinsics, so the
 * codegen cannot silently regress to scalar half code.
 */
public class TestHalf2PackedCodegen {

    public static void dotFloatAccumulate(HalfFloatArray a, HalfFloatArray b, FloatArray result, int rowSize) {
        for (@Parallel int row = 0; row < result.getSize(); row++) {
            float acc = 0.0f;
            int base = row * rowSize;
            for (int d = 0; d < rowSize; d += 2) {
                Half2 pa = a.getHalf2(base + d);
                Half2 pb = b.getHalf2(base + d);
                acc += Half2.lowFloat(pa) * Half2.lowFloat(pb);
                acc += Half2.highFloat(pa) * Half2.highFloat(pb);
            }
            result.set(row, acc);
        }
    }

    public static void mulAddPack(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c, HalfFloatArray d, FloatArray e) {
        for (@Parallel int i = 0; i < d.getSize() / 2; i++) {
            Half2 fma = Half2.fma(a.getHalf2(i * 2), b.getHalf2(i * 2), c.getHalf2(i * 2));
            Half2 sum = Half2.add(fma, Half2.fromFloats(e.get(i * 2), e.get(i * 2 + 1)));
            d.setHalf2(i * 2, Half2.mult(sum, sum));
        }
    }

    public static void localTileDot(KernelContext context, HalfFloatArray k, HalfFloatArray q, FloatArray out, int tileSize) {
        Half2[] kTile = context.allocateHalf2LocalArray(tileSize);
        int localId = context.localIdx;
        kTile[localId] = k.getHalf2(localId * 2);
        context.localBarrier();
        Half2 pair = kTile[localId];
        Half2 qPair = q.getHalf2(localId * 2);
        out.set(context.globalIdx, Half2.lowFloat(pair) * Half2.lowFloat(qPair) + Half2.highFloat(pair) * Half2.highFloat(qPair));
    }

    private static String compileToSource(String methodName, Object... parameters) {
        Method method = CompilerUtil.getMethodForName(TestHalf2PackedCodegen.class, methodName);
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(method);
        CUDABackend backend = tornadoRuntime.getBackend(CUDABackendImpl.class).getDefaultBackend();
        TornadoDevice device = tornadoRuntime.getBackend(CUDABackendImpl.class).getDefaultDevice();

        ScheduleContext scheduleMetaData = new ScheduleContext("s0");
        CompilableTask compilableTask = new CompilableTask(scheduleMetaData, "t0", method, parameters);
        TaskDataContext taskMeta = compilableTask.meta();
        taskMeta.setDevice(device);

        Providers providers = backend.getProviders();
        TornadoSuitesProvider suites = backend.getTornadoSuites();
        Sketch sketch = CompilerUtil.buildSketchForJavaMethod(resolvedJavaMethod, taskMeta, providers, suites);
        CUDACompilationResult result = CUDACompiler.compileSketchForDevice(sketch, compilableTask, (CUDAProviders) providers, backend, new EmptyProfiler());
        return new String(result.getTargetCode());
    }

    private static boolean assertContains(String source, String kernelName, String... snippets) {
        boolean ok = true;
        for (String snippet : snippets) {
            if (!source.contains(snippet)) {
                System.out.println("[FAIL] kernel " + kernelName + " is missing \"" + snippet + "\"");
                ok = false;
            }
        }
        return ok;
    }

    public void test() {
        boolean ok = true;

        final int rowSize = 64;
        HalfFloatArray a = new HalfFloatArray(rowSize * 4);
        HalfFloatArray b = new HalfFloatArray(rowSize * 4);
        HalfFloatArray c = new HalfFloatArray(rowSize * 4);
        HalfFloatArray d = new HalfFloatArray(rowSize * 4);
        FloatArray e = new FloatArray(rowSize * 4);
        FloatArray result = new FloatArray(4);

        String dotSource = compileToSource("dotFloatAccumulate", a, b, result, rowSize);
        ok &= assertContains(dotSource, "dotFloatAccumulate", //
                "#include <cuda_fp16.h>", //
                "__half2", //
                "((__half2 *)", //
                "__low2float(", //
                "__high2float(");

        String fmaSource = compileToSource("mulAddPack", a, b, c, d, e);
        ok &= assertContains(fmaSource, "mulAddPack", //
                "#include <cuda_fp16.h>", //
                "((__half2 *)", //
                "__hfma2(", //
                "__hadd2(", //
                "__hmul2(", //
                "__floats2half2_rn(");

        String localTileSource = compileToSource("localTileDot", new KernelContext(), a, b, result, 64);
        if (Boolean.getBoolean("tornado.test.half2.printKernel")) {
            System.out.println(localTileSource);
        }
        ok &= assertContains(localTileSource, "localTileDot", //
                "#include <cuda_fp16.h>", //
                "__shared__ __half2", //
                "__low2float(", //
                "__high2float(");

        if (!ok) {
            System.out.println("Test failed");
            System.exit(1);
        }
        System.out.println("Test success");
    }

    public static void main(String[] args) {
        System.out.println("Running Native: uk.ac.manchester.tornado.drivers.cuda.tests.TestHalf2PackedCodegen");
        new TestHalf2PackedCodegen().test();
    }
}
