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
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.fuzz.CaseResult;
import uk.ac.manchester.tornado.fuzz.DataGen;
import uk.ac.manchester.tornado.fuzz.ElemType;
import uk.ac.manchester.tornado.fuzz.FuzzConfig;
import uk.ac.manchester.tornado.fuzz.RandomGen;
import uk.ac.manchester.tornado.fuzz.ReproSpec;
import uk.ac.manchester.tornado.fuzz.oracle.Diff;
import uk.ac.manchester.tornado.fuzz.oracle.Oracle;

/**
 * Elementwise arithmetic / bitwise / shift / cast kernels, one output per thread
 * indexed by {@code context.globalIdx}. The device kernel and the JVM reference
 * share the same pure op helpers, so any divergence is a codegen defect. Includes
 * intentionally fragile cases (float->int convert) that ride known bailout edges.
 */
public final class ElementwiseArithKernel implements KernelTemplate {

    private enum Op {
        ADD_INT, MUL_INT, DIV_INT, MOD_INT, XOR_INT, SHL_INT, SHR_INT,
        ADD_FLOAT, MUL_FLOAT, DIV_FLOAT,
        F2I, I2D
    }

    @Override
    public String id() {
        return "elementwise-arith";
    }

    @Override
    public CaseResult run(FuzzConfig cfg, RandomGen rng, TornadoDevice device) {
        Op op = Op.values()[rng.nextInt(Op.values().length)];
        cfg.put("op", op.name());

        KernelContext context = new KernelContext();
        return switch (op) {
            case ADD_INT, MUL_INT, DIV_INT, MOD_INT, XOR_INT, SHL_INT, SHR_INT -> binaryInt(op, cfg, rng, device, context);
            case ADD_FLOAT, MUL_FLOAT, DIV_FLOAT -> binaryFloat(op, cfg, rng, device, context);
            case F2I -> castF2I(cfg, rng, device, context);
            case I2D -> castI2D(cfg, rng, device, context);
        };
    }

    // ---- pure op helpers (single source of truth for device kernel + JVM reference) ----

    private static int addI(int a, int b) {
        return a + b;
    }

    private static int mulI(int a, int b) {
        return a * b;
    }

    private static int divI(int a, int b) {
        return a / b;
    }

    private static int modI(int a, int b) {
        return a % b;
    }

    private static int xorI(int a, int b) {
        return a ^ b;
    }

    private static int shlI(int a, int b) {
        return a << (b & 31);
    }

    private static int shrI(int a, int b) {
        return a >> (b & 31);
    }

    private static float addF(float a, float b) {
        return a + b;
    }

    private static float mulF(float a, float b) {
        return a * b;
    }

    private static float divF(float a, float b) {
        return a / b;
    }

    private static int f2i(float a) {
        return (int) a;
    }

    private static double i2d(int a) {
        return a;
    }

    // ---- device kernels (KernelContext form) ----

    public static void kAddInt(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, addI(a.get(i), b.get(i)));
    }

    public static void kMulInt(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, mulI(a.get(i), b.get(i)));
    }

    public static void kDivInt(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, divI(a.get(i), b.get(i)));
    }

    public static void kModInt(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, modI(a.get(i), b.get(i)));
    }

    public static void kXorInt(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, xorI(a.get(i), b.get(i)));
    }

    public static void kShlInt(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, shlI(a.get(i), b.get(i)));
    }

    public static void kShrInt(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, shrI(a.get(i), b.get(i)));
    }

    public static void kAddFloat(KernelContext c, FloatArray a, FloatArray b, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, addF(a.get(i), b.get(i)));
    }

    public static void kMulFloat(KernelContext c, FloatArray a, FloatArray b, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, mulF(a.get(i), b.get(i)));
    }

    public static void kDivFloat(KernelContext c, FloatArray a, FloatArray b, FloatArray out) {
        int i = c.globalIdx;
        out.set(i, divF(a.get(i), b.get(i)));
    }

    public static void kF2I(KernelContext c, FloatArray in, IntArray out) {
        int i = c.globalIdx;
        out.set(i, f2i(in.get(i)));
    }

    public static void kI2D(KernelContext c, IntArray in, DoubleArray out) {
        int i = c.globalIdx;
        out.set(i, i2d(in.get(i)));
    }

    // ---- case builders ----

    private CaseResult binaryInt(Op op, FuzzConfig cfg, RandomGen rng, TornadoDevice device, KernelContext context) {
        int size = cfg.size;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray out = new IntArray(size);
        DataGen.fill(a, rng, cfg.dataProfile);
        boolean protect = op == Op.DIV_INT || op == Op.MOD_INT;
        if (protect) {
            DataGen.fillNonZero(b, rng, cfg.dataProfile);
        } else {
            DataGen.fill(b, rng, cfg.dataProfile);
        }

        TaskGraph tg = new TaskGraph(Kernels.GRAPH).transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b);
        String expr;
        switch (op) {
            case ADD_INT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kAddInt, context, a, b, out);
                expr = "a.get(i) + b.get(i)";
            }
            case MUL_INT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kMulInt, context, a, b, out);
                expr = "a.get(i) * b.get(i)";
            }
            case DIV_INT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kDivInt, context, a, b, out);
                expr = "a.get(i) / b.get(i)";
            }
            case MOD_INT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kModInt, context, a, b, out);
                expr = "a.get(i) % b.get(i)";
            }
            case XOR_INT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kXorInt, context, a, b, out);
                expr = "a.get(i) ^ b.get(i)";
            }
            case SHL_INT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kShlInt, context, a, b, out);
                expr = "a.get(i) << (b.get(i) & 31)";
            }
            case SHR_INT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kShrInt, context, a, b, out);
                expr = "a.get(i) >> (b.get(i) & 31)";
            }
            default -> throw new IllegalStateException();
        }
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        // JVM golden reference
        IntArray expected = new IntArray(size);
        for (int i = 0; i < size; i++) {
            expected.set(i, switch (op) {
                case ADD_INT -> addI(a.get(i), b.get(i));
                case MUL_INT -> mulI(a.get(i), b.get(i));
                case DIV_INT -> divI(a.get(i), b.get(i));
                case MOD_INT -> modI(a.get(i), b.get(i));
                case XOR_INT -> xorI(a.get(i), b.get(i));
                case SHL_INT -> shlI(a.get(i), b.get(i));
                case SHR_INT -> shrI(a.get(i), b.get(i));
                default -> 0;
            });
        }

        String kernelSource = "public static void fuzzedKernel(KernelContext context, IntArray a, IntArray b, IntArray out) {\n" //
                + "    int i = context.globalIdx;\n" //
                + "    out.set(i, " + expr + ");\n" //
                + "}";
        ReproSpec repro = new ReproSpec(kernelSource, ElemType.INT, size, cfg.localSize) //
                .addInput("a", ElemType.INT, a).addInput("b", ElemType.INT, b) //
                .setOutput("out", ElemType.INT, out, expected);

        try {
            Kernels.run(tg, Kernels.gridScheduler(size, cfg.localSize), device);
        } catch (Throwable e) {
            return CaseResult.exception(e, kernelSource, repro);
        }
        Diff diff = Oracle.compare(expected, out);
        return diff == null ? CaseResult.pass(kernelSource) : CaseResult.mismatch(diff, kernelSource, repro);
    }

    private CaseResult binaryFloat(Op op, FuzzConfig cfg, RandomGen rng, TornadoDevice device, KernelContext context) {
        int size = cfg.size;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray out = new FloatArray(size);
        DataGen.fill(a, rng, cfg.dataProfile);
        DataGen.fill(b, rng, cfg.dataProfile);

        TaskGraph tg = new TaskGraph(Kernels.GRAPH).transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b);
        String expr;
        switch (op) {
            case ADD_FLOAT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kAddFloat, context, a, b, out);
                expr = "a.get(i) + b.get(i)";
            }
            case MUL_FLOAT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kMulFloat, context, a, b, out);
                expr = "a.get(i) * b.get(i)";
            }
            case DIV_FLOAT -> {
                tg.task(Kernels.TASK, ElementwiseArithKernel::kDivFloat, context, a, b, out);
                expr = "a.get(i) / b.get(i)";
            }
            default -> throw new IllegalStateException();
        }
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        FloatArray expected = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            expected.set(i, switch (op) {
                case ADD_FLOAT -> addF(a.get(i), b.get(i));
                case MUL_FLOAT -> mulF(a.get(i), b.get(i));
                case DIV_FLOAT -> divF(a.get(i), b.get(i));
                default -> 0f;
            });
        }

        String kernelSource = "public static void fuzzedKernel(KernelContext context, FloatArray a, FloatArray b, FloatArray out) {\n" //
                + "    int i = context.globalIdx;\n" //
                + "    out.set(i, " + expr + ");\n" //
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

    private CaseResult castF2I(FuzzConfig cfg, RandomGen rng, TornadoDevice device, KernelContext context) {
        int size = cfg.size;
        FloatArray in = new FloatArray(size);
        IntArray out = new IntArray(size);
        DataGen.fill(in, rng, cfg.dataProfile);

        TaskGraph tg = new TaskGraph(Kernels.GRAPH).transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task(Kernels.TASK, ElementwiseArithKernel::kF2I, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        IntArray expected = new IntArray(size);
        for (int i = 0; i < size; i++) {
            expected.set(i, f2i(in.get(i)));
        }

        String kernelSource = "public static void fuzzedKernel(KernelContext context, FloatArray in, IntArray out) {\n" //
                + "    int i = context.globalIdx;\n" //
                + "    out.set(i, (int) in.get(i));\n" //
                + "}";
        ReproSpec repro = new ReproSpec(kernelSource, ElemType.INT, size, cfg.localSize) //
                .addInput("in", ElemType.FLOAT, in) //
                .setOutput("out", ElemType.INT, out, expected);

        try {
            Kernels.run(tg, Kernels.gridScheduler(size, cfg.localSize), device);
        } catch (Throwable e) {
            return CaseResult.exception(e, kernelSource, repro);
        }
        Diff diff = Oracle.compare(expected, out);
        return diff == null ? CaseResult.pass(kernelSource) : CaseResult.mismatch(diff, kernelSource, repro);
    }

    private CaseResult castI2D(FuzzConfig cfg, RandomGen rng, TornadoDevice device, KernelContext context) {
        int size = cfg.size;
        IntArray in = new IntArray(size);
        DoubleArray out = new DoubleArray(size);
        DataGen.fill(in, rng, cfg.dataProfile);

        TaskGraph tg = new TaskGraph(Kernels.GRAPH).transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task(Kernels.TASK, ElementwiseArithKernel::kI2D, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        DoubleArray expected = new DoubleArray(size);
        for (int i = 0; i < size; i++) {
            expected.set(i, i2d(in.get(i)));
        }

        String kernelSource = "public static void fuzzedKernel(KernelContext context, IntArray in, DoubleArray out) {\n" //
                + "    int i = context.globalIdx;\n" //
                + "    out.set(i, (double) in.get(i));\n" //
                + "}";
        ReproSpec repro = new ReproSpec(kernelSource, ElemType.DOUBLE, size, cfg.localSize) //
                .addInput("in", ElemType.INT, in) //
                .setOutput("out", ElemType.DOUBLE, out, expected);

        try {
            Kernels.run(tg, Kernels.gridScheduler(size, cfg.localSize), device);
        } catch (Throwable e) {
            return CaseResult.exception(e, kernelSource, repro);
        }
        Diff diff = Oracle.compare(expected, out);
        return diff == null ? CaseResult.pass(kernelSource) : CaseResult.mismatch(diff, kernelSource, repro);
    }
}
