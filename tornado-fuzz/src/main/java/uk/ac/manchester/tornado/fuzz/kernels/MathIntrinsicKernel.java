/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.fuzz.kernels;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.fuzz.CaseResult;
import uk.ac.manchester.tornado.fuzz.DataGen;
import uk.ac.manchester.tornado.fuzz.ElemType;
import uk.ac.manchester.tornado.fuzz.FuzzConfig;
import uk.ac.manchester.tornado.fuzz.RandomGen;
import uk.ac.manchester.tornado.fuzz.ReproSpec;
import uk.ac.manchester.tornado.fuzz.oracle.Diff;
import uk.ac.manchester.tornado.fuzz.oracle.Oracle;

/**
 * Applies a randomly-chosen {@code TornadoMath} intrinsic that is actually
 * reachable on the CUDA backend (registered in {@code CUDAMathPlugins}). The JVM
 * reference calls the same {@code TornadoMath} method, which delegates to
 * {@link java.lang.Math} on the host.
 */
public final class MathIntrinsicKernel implements KernelTemplate {

    private enum Fn {
        SQRT, EXP, FLOOR, LOG, CEIL, ABS, SIN, COS, TAN, // unary
        MIN, MAX, POW, ATAN2 // binary
    }

    @Override
    public String id() {
        return "math-intrinsic";
    }

    @Override
    public CaseResult run(FuzzConfig cfg, RandomGen rng, TornadoDevice device) {
        Fn fn = Fn.values()[rng.nextInt(Fn.values().length)];
        cfg.put("fn", fn.name());
        boolean binary = fn.ordinal() >= Fn.MIN.ordinal();
        KernelContext context = new KernelContext();
        return binary ? binary(fn, cfg, rng, device, context) : unary(fn, cfg, rng, device, context);
    }

    // ---- device kernels ----

    public static void kSqrt(KernelContext c, FloatArray a, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.sqrt(a.get(i)));
    }

    public static void kExp(KernelContext c, FloatArray a, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.exp(a.get(i)));
    }

    public static void kFloor(KernelContext c, FloatArray a, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.floor(a.get(i)));
    }

    public static void kLog(KernelContext c, FloatArray a, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.log(a.get(i)));
    }

    public static void kCeil(KernelContext c, FloatArray a, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.ceil(a.get(i)));
    }

    public static void kAbs(KernelContext c, FloatArray a, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.abs(a.get(i)));
    }

    public static void kSin(KernelContext c, FloatArray a, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.sin(a.get(i)));
    }

    public static void kCos(KernelContext c, FloatArray a, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.cos(a.get(i)));
    }

    public static void kTan(KernelContext c, FloatArray a, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.tan(a.get(i)));
    }

    public static void kMin(KernelContext c, FloatArray a, FloatArray b, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.min(a.get(i), b.get(i)));
    }

    public static void kMax(KernelContext c, FloatArray a, FloatArray b, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.max(a.get(i), b.get(i)));
    }

    public static void kPow(KernelContext c, FloatArray a, FloatArray b, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.pow(a.get(i), b.get(i)));
    }

    public static void kAtan2(KernelContext c, FloatArray a, FloatArray b, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, TornadoMath.atan2(a.get(i), b.get(i)));
    }

    private static float applyUnary(Fn fn, float x) {
        return switch (fn) {
            case SQRT -> TornadoMath.sqrt(x);
            case EXP -> TornadoMath.exp(x);
            case FLOOR -> TornadoMath.floor(x);
            case LOG -> TornadoMath.log(x);
            case CEIL -> TornadoMath.ceil(x);
            case ABS -> TornadoMath.abs(x);
            case SIN -> TornadoMath.sin(x);
            case COS -> TornadoMath.cos(x);
            case TAN -> TornadoMath.tan(x);
            default -> throw new IllegalStateException();
        };
    }

    private static float applyBinary(Fn fn, float a, float b) {
        return switch (fn) {
            case MIN -> TornadoMath.min(a, b);
            case MAX -> TornadoMath.max(a, b);
            case POW -> TornadoMath.pow(a, b);
            case ATAN2 -> TornadoMath.atan2(a, b);
            default -> throw new IllegalStateException();
        };
    }

    private CaseResult unary(Fn fn, FuzzConfig cfg, RandomGen rng, TornadoDevice device, KernelContext context) {
        int size = cfg.size;
        FloatArray a = new FloatArray(size);
        FloatArray out = new FloatArray(size);
        DataGen.fill(a, rng, cfg.dataProfile);

        TaskGraph tg = new TaskGraph(Kernels.GRAPH).transferToDevice(DataTransferMode.FIRST_EXECUTION, a);
        switch (fn) {
            case SQRT -> tg.task(Kernels.TASK, MathIntrinsicKernel::kSqrt, context, a, out);
            case EXP -> tg.task(Kernels.TASK, MathIntrinsicKernel::kExp, context, a, out);
            case FLOOR -> tg.task(Kernels.TASK, MathIntrinsicKernel::kFloor, context, a, out);
            case LOG -> tg.task(Kernels.TASK, MathIntrinsicKernel::kLog, context, a, out);
            case CEIL -> tg.task(Kernels.TASK, MathIntrinsicKernel::kCeil, context, a, out);
            case ABS -> tg.task(Kernels.TASK, MathIntrinsicKernel::kAbs, context, a, out);
            case SIN -> tg.task(Kernels.TASK, MathIntrinsicKernel::kSin, context, a, out);
            case COS -> tg.task(Kernels.TASK, MathIntrinsicKernel::kCos, context, a, out);
            case TAN -> tg.task(Kernels.TASK, MathIntrinsicKernel::kTan, context, a, out);
            default -> throw new IllegalStateException();
        }
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        FloatArray expected = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            expected.set(i, applyUnary(fn, a.get(i)));
        }

        String kernelSource = "public static void fuzzedKernel(KernelContext context, FloatArray a, FloatArray out) {\n" //
                + "    int i = context.globalIdx;\n" //
                + "    out.set(i, TornadoMath." + fn.name().toLowerCase() + "(a.get(i)));\n" //
                + "}";
        ReproSpec repro = new ReproSpec(kernelSource, ElemType.FLOAT, size, cfg.localSize) //
                .addInput("a", ElemType.FLOAT, a).setOutput("out", ElemType.FLOAT, out, expected);

        try {
            Kernels.run(tg, Kernels.gridScheduler(size, cfg.localSize), device);
        } catch (Throwable e) {
            return CaseResult.exception(e, kernelSource, repro);
        }
        Diff diff = Oracle.compare(expected, out);
        return diff == null ? CaseResult.pass(kernelSource) : CaseResult.mismatch(diff, kernelSource, repro);
    }

    private CaseResult binary(Fn fn, FuzzConfig cfg, RandomGen rng, TornadoDevice device, KernelContext context) {
        int size = cfg.size;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray out = new FloatArray(size);
        DataGen.fill(a, rng, cfg.dataProfile);
        DataGen.fill(b, rng, cfg.dataProfile);

        TaskGraph tg = new TaskGraph(Kernels.GRAPH).transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b);
        switch (fn) {
            case MIN -> tg.task(Kernels.TASK, MathIntrinsicKernel::kMin, context, a, b, out);
            case MAX -> tg.task(Kernels.TASK, MathIntrinsicKernel::kMax, context, a, b, out);
            case POW -> tg.task(Kernels.TASK, MathIntrinsicKernel::kPow, context, a, b, out);
            case ATAN2 -> tg.task(Kernels.TASK, MathIntrinsicKernel::kAtan2, context, a, b, out);
            default -> throw new IllegalStateException();
        }
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        FloatArray expected = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            expected.set(i, applyBinary(fn, a.get(i), b.get(i)));
        }

        String kernelSource = "public static void fuzzedKernel(KernelContext context, FloatArray a, FloatArray b, FloatArray out) {\n" //
                + "    int i = context.globalIdx;\n" //
                + "    out.set(i, TornadoMath." + fn.name().toLowerCase() + "(a.get(i), b.get(i)));\n" //
                + "}";
        ReproSpec repro = new ReproSpec(kernelSource, ElemType.FLOAT, size, cfg.localSize) //
                .addInput("a", ElemType.FLOAT, a).addInput("b", ElemType.FLOAT, b) //
                .setOutput("out", ElemType.FLOAT, out, expected);

        try {
            Kernels.run(tg, Kernels.gridScheduler(size, cfg.localSize), device);
        } catch (Throwable e) {
            return CaseResult.exception(e, kernelSource, repro);
        }
        Diff diff = Oracle.compare(expected, out);
        return diff == null ? CaseResult.pass(kernelSource) : CaseResult.mismatch(diff, kernelSource, repro);
    }
}
