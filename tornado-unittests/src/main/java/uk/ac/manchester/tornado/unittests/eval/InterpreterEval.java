/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat2;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat3;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat8;
import uk.ac.manchester.tornado.api.types.collections.VectorInt2;
import uk.ac.manchester.tornado.api.types.collections.VectorInt4;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat4;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DInt;
import uk.ac.manchester.tornado.api.types.matrix.Matrix3DFloat;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;
import uk.ac.manchester.tornado.api.types.vectors.Int2;
import uk.ac.manchester.tornado.api.types.vectors.Int4;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.batches.TestBatches;
import uk.ac.manchester.tornado.unittests.compute.ComputeTests;
import uk.ac.manchester.tornado.unittests.foundation.TestKernels;
import uk.ac.manchester.tornado.unittests.grid.TestGridScheduler;
import uk.ac.manchester.tornado.unittests.kernelcontext.api.TestCombinedTaskGraph;
import uk.ac.manchester.tornado.unittests.kernelcontext.api.TestVectorAdditionKernelContext;
import uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationKernelContext;
import uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestReductionsIntegersKernelContext;
import uk.ac.manchester.tornado.unittests.math.TestMath;
import uk.ac.manchester.tornado.unittests.math.TestTornadoMathCollection;
import uk.ac.manchester.tornado.unittests.matrices.TestMatrixTypes;
import uk.ac.manchester.tornado.unittests.reductions.MultipleReductions;
import uk.ac.manchester.tornado.unittests.reductions.TestReductionsAutomatic;
import uk.ac.manchester.tornado.unittests.tasks.TestMultipleFunctions;
import uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksSingleDevice;
import uk.ac.manchester.tornado.unittests.vectortypes.TestFloats;
import uk.ac.manchester.tornado.unittests.vectortypes.TestInts;

/**
 * Times {@code execute()} after warmup using kernels already defined in the unit tests.
 *
 * <pre>
 * tornado-eval
 * tornado-eval primitive-arrays
 * tornado-eval vectorAdd matmul2d
 * tornado-eval -J-Dtornado.interpreter.native=true
 * </pre>
 */
public class InterpreterEval {

    private static final int DEFAULT_WARMUP = 20;
    private static final int DEFAULT_ITERS = 100;
    private static final int N = Integer.getInteger("tornado.eval.n", 8192);
    private static final int MATRIX_N = Integer.getInteger("tornado.eval.matrix", 256);
    private static final int BATCH_N = Integer.getInteger("tornado.eval.batch", 1 << 20);

    @FunctionalInterface
    private interface Workload {
        Row run(int warmup, int iters) throws TornadoExecutionPlanException;
    }

    private record Spec(String category, String name, Workload run) {
    }

    private static final List<Spec> SPECS = List.of( //
            new Spec("primitive-arrays", "vectorAdd", InterpreterEval::vectorAdd), //
            new Spec("primitive-arrays", "vectorAddInt", InterpreterEval::vectorAddInt), //
            new Spec("primitive-arrays", "vectorAddLong", InterpreterEval::vectorAddLong), //
            new Spec("primitive-arrays", "vectorAddShort", InterpreterEval::vectorAddShort), //
            new Spec("primitive-arrays", "vectorAddByte", InterpreterEval::vectorAddByte), //
            new Spec("primitive-arrays", "addAccumulator", InterpreterEval::addAccumulator), //
            new Spec("foundation", "foundationFloat", InterpreterEval::foundationFloat), //
            new Spec("foundation", "foundationInt", InterpreterEval::foundationInt), //
            new Spec("foundation", "foundationMul", InterpreterEval::foundationMul), //
            new Spec("foundation", "foundationSub", InterpreterEval::foundationSub), //
            new Spec("foundation", "foundationSaxpy", InterpreterEval::foundationSaxpy), //
            new Spec("foundation", "foundationCopy", InterpreterEval::foundationCopy), //
            new Spec("vector-types", "vectorFloat2", InterpreterEval::vectorFloat2), //
            new Spec("vector-types", "vectorFloat3", InterpreterEval::vectorFloat3), //
            new Spec("vector-types", "vectorFloat4", InterpreterEval::vectorFloat4), //
            new Spec("vector-types", "vectorFloat8", InterpreterEval::vectorFloat8), //
            new Spec("vector-types", "vectorInt2", InterpreterEval::vectorInt2), //
            new Spec("vector-types", "vectorInt4", InterpreterEval::vectorInt4), //
            new Spec("multi-task", "multiTask2", InterpreterEval::multiTask2), //
            new Spec("multi-task", "multiTask3", InterpreterEval::multiTask3), //
            new Spec("multi-task", "multiFn", InterpreterEval::multiFn), //
            new Spec("multi-task", "combined3", InterpreterEval::combined3), //
            new Spec("reductions", "reduction", InterpreterEval::reduction), //
            new Spec("reductions", "reductionMulti", InterpreterEval::reductionMulti), //
            new Spec("reductions", "reductionSeq", InterpreterEval::reductionSeq), //
            new Spec("reductions", "reductionDot", InterpreterEval::reductionDot), //
            new Spec("kernel-context", "kernelContext", InterpreterEval::kernelContext), //
            new Spec("kernel-context", "kernelContextLast", InterpreterEval::kernelContextLast), //
            new Spec("kernel-context", "kernelThreadId", InterpreterEval::kernelThreadId), //
            new Spec("kernel-context", "kernelLocal", InterpreterEval::kernelLocal), //
            new Spec("kernel-context", "kernelMatmul", InterpreterEval::kernelMatmul), //
            new Spec("matrices", "matmul2d", InterpreterEval::matmul2d), //
            new Spec("matrices", "matrixSum", InterpreterEval::matrixSum), //
            new Spec("matrices", "matrixSumInt", InterpreterEval::matrixSumInt), //
            new Spec("matrices", "matrix3d", InterpreterEval::matrix3d), //
            new Spec("matrices", "matrixFloat4", InterpreterEval::matrixFloat4), //
            new Spec("compute", "hilbert", InterpreterEval::hilbert), //
            new Spec("compute", "dft", InterpreterEval::dft), //
            new Spec("compute", "dftFloat", InterpreterEval::dftFloat), //
            new Spec("compute", "julia", InterpreterEval::julia), //
            new Spec("math", "mathExp", InterpreterEval::mathExp), //
            new Spec("math", "mathAbs", InterpreterEval::mathAbs), //
            new Spec("math", "mathPow", InterpreterEval::mathPow), //
            new Spec("math", "mathMin", InterpreterEval::mathMin), //
            new Spec("math", "mathFma", InterpreterEval::mathFma), //
            new Spec("math", "mathCos", InterpreterEval::mathCos), //
            new Spec("batches", "batch", InterpreterEval::batch), //
            new Spec("batches", "batch3", InterpreterEval::batch3), //
            new Spec("batches", "batchInt", InterpreterEval::batchInt), //
            new Spec("batches", "batchCopy", InterpreterEval::batchCopy));

    private static final Map<String, Spec> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, List<Spec>> BY_CATEGORY = new LinkedHashMap<>();

    static {
        for (Spec spec : SPECS) {
            BY_NAME.put(spec.name, spec);
            BY_CATEGORY.computeIfAbsent(spec.category, key -> new ArrayList<>()).add(spec);
        }
    }

    public static void main(String[] args) {
        int warmup = Integer.parseInt(System.getProperty("tornado.eval.warmup", Integer.toString(DEFAULT_WARMUP)));
        int iters = Integer.parseInt(System.getProperty("tornado.eval.iters", Integer.toString(DEFAULT_ITERS)));
        List<Spec> selected = new ArrayList<>();
        for (String arg : args) {
            if (arg.isEmpty()) {
                continue;
            }
            if (BY_CATEGORY.containsKey(arg)) {
                selected.addAll(BY_CATEGORY.get(arg));
            } else if (BY_NAME.containsKey(arg)) {
                selected.add(BY_NAME.get(arg));
            } else {
                System.err.println("unknown: " + arg);
                System.err.println("categories: " + String.join(" ", BY_CATEGORY.keySet()));
                System.err.println("workloads: " + String.join(" ", BY_NAME.keySet()));
                System.exit(2);
            }
        }
        if (selected.isEmpty()) {
            selected.addAll(SPECS);
        }

        boolean nativeOn = Boolean.parseBoolean(System.getProperty("tornado.interpreter.native", "false"));
        System.out.printf("native=%s warmup=%d iters=%d workloads=%d%n", nativeOn, warmup, iters, selected.size());

        boolean allOk = true;
        String lastCategory = null;
        for (Spec spec : selected) {
            if (!spec.category.equals(lastCategory)) {
                System.out.println();
                System.out.println("== " + spec.category);
                System.out.printf("%-22s %12s %12s %12s %s%n", "workload", "median_us", "p10_us", "p90_us", "ok");
                lastCategory = spec.category;
            }
            Row row;
            try {
                row = spec.run.run(warmup, iters);
            } catch (Exception e) {
                row = new Row(spec.name, 0, 0, 0, false, e.getClass().getSimpleName());
            }
            System.out.printf("%-22s %12.1f %12.1f %12.1f %s%n", row.name, nsToUs(row.medianNs), nsToUs(row.p10Ns), nsToUs(row.p90Ns), row.ok ? "yes" : "NO " + row.reason);
            allOk &= row.ok;
        }
        if (!allOk) {
            System.exit(1);
        }
    }

    private static Row vectorAdd(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray a = floats(N, 1.0f);
        FloatArray b = floats(N, 2.0f);
        FloatArray c = new FloatArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorAdd", warmup, iters, graph, null, null, () -> close(c.get(0), 3.0f));
    }

    private static Row vectorAddInt(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = ints(N, 1);
        IntArray b = ints(N, 2);
        IntArray c = new IntArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddInteger, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorAddInt", warmup, iters, graph, null, null, () -> c.get(0) == 3 ? null : "c=" + c.get(0));
    }

    private static Row vectorAddLong(int warmup, int iters) throws TornadoExecutionPlanException {
        LongArray a = new LongArray(N);
        LongArray b = new LongArray(N);
        LongArray c = new LongArray(N);
        a.init(1L);
        b.init(2L);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddLong, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorAddLong", warmup, iters, graph, null, null, () -> c.get(0) == 3L ? null : "c=" + c.get(0));
    }

    private static Row vectorAddShort(int warmup, int iters) throws TornadoExecutionPlanException {
        ShortArray a = new ShortArray(N);
        ShortArray b = new ShortArray(N);
        ShortArray c = new ShortArray(N);
        a.init((short) 1);
        b.init((short) 2);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddShort, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorAddShort", warmup, iters, graph, null, null, () -> c.get(0) == 3 ? null : "c=" + c.get(0));
    }

    private static Row vectorAddByte(int warmup, int iters) throws TornadoExecutionPlanException {
        ByteArray a = new ByteArray(N);
        ByteArray b = new ByteArray(N);
        ByteArray c = new ByteArray(N);
        a.init((byte) 1);
        b.init((byte) 2);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddByte, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorAddByte", warmup, iters, graph, null, null, () -> c.get(0) == 3 ? null : "c=" + c.get(0));
    }

    private static Row addAccumulator(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = ints(N, 5);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestArrays::addAccumulator, a, 7) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("addAccumulator", warmup, iters, graph, null, null, () -> a.init(5), () -> a.get(0) == 12 ? null : "a=" + a.get(0));
    }

    private static Row foundationFloat(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        FloatArray b = floats(N, 100.0f);
        FloatArray c = floats(N, 200.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorAddFloatCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("foundationFloat", warmup, iters, graph, null, null, () -> close(a.get(0), 300.0f));
    }

    private static Row foundationInt(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = new IntArray(N);
        IntArray b = ints(N, 100);
        IntArray c = ints(N, 200);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorAddCompute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("foundationInt", warmup, iters, graph, null, null, () -> a.get(0) == 300 ? null : "a=" + a.get(0));
    }

    private static Row foundationMul(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = new IntArray(N);
        IntArray b = ints(N, 6);
        IntArray c = ints(N, 7);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorMul, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("foundationMul", warmup, iters, graph, null, null, () -> a.get(0) == 42 ? null : "a=" + a.get(0));
    }

    private static Row foundationSub(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = new IntArray(N);
        IntArray b = ints(N, 200);
        IntArray c = ints(N, 50);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, b, c) //
                .task("t0", TestKernels::vectorSub, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("foundationSub", warmup, iters, graph, null, null, () -> a.get(0) == 150 ? null : "a=" + a.get(0));
    }

    private static Row foundationSaxpy(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = new IntArray(N);
        IntArray b = ints(N, 2);
        IntArray c = ints(N, 3);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, b, c) //
                .task("t0", TestKernels::saxpy, a, b, c, 4) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("foundationSaxpy", warmup, iters, graph, null, null, () -> a.get(0) == 11 ? null : "a=" + a.get(0));
    }

    private static Row foundationCopy(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = new IntArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .task("t0", TestKernels::copyTest, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("foundationCopy", warmup, iters, graph, null, null, () -> a.get(0) == 50 ? null : "a=" + a.get(0));
    }

    private static Row vectorFloat2(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 1024;
        VectorFloat2 a = new VectorFloat2(n);
        VectorFloat2 b = new VectorFloat2(n);
        VectorFloat2 c = new VectorFloat2(n);
        Float2 one = new Float2(1.0f, 1.0f);
        Float2 two = new Float2(2.0f, 2.0f);
        for (int i = 0; i < n; i++) {
            a.set(i, one);
            b.set(i, two);
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat2, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorFloat2", warmup, iters, graph, null, null, () -> close(c.get(0).getX(), 3.0f));
    }

    private static Row vectorFloat3(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 1024;
        VectorFloat3 a = new VectorFloat3(n);
        VectorFloat3 b = new VectorFloat3(n);
        VectorFloat3 c = new VectorFloat3(n);
        Float3 one = new Float3(1.0f, 1.0f, 1.0f);
        Float3 two = new Float3(2.0f, 2.0f, 2.0f);
        for (int i = 0; i < n; i++) {
            a.set(i, one);
            b.set(i, two);
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat3, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorFloat3", warmup, iters, graph, null, null, () -> close(c.get(0).getX(), 3.0f));
    }

    private static Row vectorFloat4(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 1024;
        VectorFloat4 a = new VectorFloat4(n);
        VectorFloat4 b = new VectorFloat4(n);
        VectorFloat4 c = new VectorFloat4(n);
        Float4 one = new Float4(1.0f, 1.0f, 1.0f, 1.0f);
        Float4 two = new Float4(2.0f, 2.0f, 2.0f, 2.0f);
        for (int i = 0; i < n; i++) {
            a.set(i, one);
            b.set(i, two);
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat4, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorFloat4", warmup, iters, graph, null, null, () -> close(c.get(0).getX(), 3.0f));
    }

    private static Row vectorFloat8(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 1024;
        VectorFloat8 a = new VectorFloat8(n);
        VectorFloat8 b = new VectorFloat8(n);
        VectorFloat8 c = new VectorFloat8(n);
        Float8 one = new Float8(1, 1, 1, 1, 1, 1, 1, 1);
        Float8 two = new Float8(2, 2, 2, 2, 2, 2, 2, 2);
        for (int i = 0; i < n; i++) {
            a.set(i, one);
            b.set(i, two);
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestFloats::addVectorFloat8, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorFloat8", warmup, iters, graph, null, null, () -> close(c.get(0).getS0(), 3.0f));
    }

    private static Row vectorInt2(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 1024;
        VectorInt2 a = new VectorInt2(n);
        VectorInt2 b = new VectorInt2(n);
        VectorInt2 c = new VectorInt2(n);
        Int2 one = new Int2(1, 1);
        Int2 two = new Int2(2, 2);
        for (int i = 0; i < n; i++) {
            a.set(i, one);
            b.set(i, two);
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestInts::addVectorInt2, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorInt2", warmup, iters, graph, null, null, () -> c.get(0).getX() == 3 ? null : "c=" + c.get(0).getX());
    }

    private static Row vectorInt4(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 1024;
        VectorInt4 a = new VectorInt4(n);
        VectorInt4 b = new VectorInt4(n);
        VectorInt4 c = new VectorInt4(n);
        Int4 one = new Int4(1, 1, 1, 1);
        Int4 two = new Int4(2, 2, 2, 2);
        for (int i = 0; i < n; i++) {
            a.set(i, one);
            b.set(i, two);
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestInts::addVectorInt4, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("vectorInt4", warmup, iters, graph, null, null, () -> c.get(0).getX() == 3 ? null : "c=" + c.get(0).getX());
    }

    private static Row multiTask2(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = new IntArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a) //
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("multiTask2", warmup, iters, graph, null, null, () -> a.get(0) == 120 ? null : "a=" + a.get(0));
    }

    private static Row multiTask3(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = new IntArray(N);
        IntArray b = new IntArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, a) //
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12) //
                .task("t2", TestMultipleTasksSingleDevice::task2Saxpy, a, a, b, 12) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        return time("multiTask3", warmup, iters, graph, null, null, () -> b.get(0) == 1560 ? null : "b=" + b.get(0));
    }

    private static Row multiFn(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = ints(N, 1);
        IntArray b = ints(N, 2);
        IntArray c = new IntArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMultipleFunctions::vectorAddInteger, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("multiFn", warmup, iters, graph, null, null, () -> c.get(0) == 3 ? null : "c=" + c.get(0));
    }

    private static Row combined3(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = ints(N, 2);
        IntArray b = ints(N, 3);
        IntArray c = new IntArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestCombinedTaskGraph::vectorAddV1, a, b, c) //
                .task("t1", TestCombinedTaskGraph::vectorMulV1, c, b, c) //
                .task("t2", TestCombinedTaskGraph::vectorSubV1, c, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("combined3", warmup, iters, graph, null, null, () -> c.get(0) == 12 ? null : "c=" + c.get(0));
    }

    private static Row reduction(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray input = ints(N, 1);
        IntArray result = ints(1, 0);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsAutomatic::test, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        return time("reduction", warmup, iters, graph, null, null, () -> result.get(0) == N ? null : "sum=" + result.get(0));
    }

    private static Row reductionMulti(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray input = ints(N, 1);
        IntArray out1 = ints(1, 0);
        IntArray out2 = ints(1, 0);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, out1, out2) //
                .task("t0", MultipleReductions::test, input, out1, out2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out1);
        return time("reductionMulti", warmup, iters, graph, null, null, () -> out1.get(0) == N ? null : "sum=" + out1.get(0));
    }

    private static Row reductionSeq(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray array = floats(N, 1.0f);
        WorkerGrid1D worker = new WorkerGrid1D(1);
        worker.setGlobalWork(1, 1, 1);
        worker.setLocalWork(1, 1, 1);
        GridScheduler grid = new GridScheduler("s0.t0", worker);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, array) //
                .task("t0", TestGridScheduler::reduceAdd, array, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, array);
        return time("reductionSeq", warmup, iters, graph, grid, null, () -> array.init(1.0f), () -> close(array.get(0), (float) N));
    }

    private static Row reductionDot(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray input = floats(N, 1.0f);
        FloatArray result = floats(1, 0.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestFloats::dotProductFunctionReduce, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        return time("reductionDot", warmup, iters, graph, null, null, () -> close(result.get(0), (float) N));
    }

    private static Row kernelContext(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = ints(N, 10);
        IntArray b = ints(N, 20);
        IntArray c = new IntArray(N);
        KernelContext context = new KernelContext();
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, context, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("kernelContext", warmup, iters, graph, grid1D("s0.t0", N), null, () -> c.get(0) == 30 ? null : "c=" + c.get(0));
    }

    private static Row kernelContextLast(int warmup, int iters) throws TornadoExecutionPlanException {
        IntArray a = ints(N, 10);
        IntArray b = ints(N, 20);
        IntArray c = new IntArray(N);
        KernelContext context = new KernelContext();
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestVectorAdditionKernelContext::vectorAdd, a, b, c, context) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("kernelContextLast", warmup, iters, graph, grid1D("s0.t0", N), null, () -> c.get(0) == 30 ? null : "c=" + c.get(0));
    }

    private static Row kernelThreadId(int warmup, int iters) throws TornadoExecutionPlanException {
        final int size = 1024;
        final int local = 256;
        IntArray a = new IntArray(size);
        KernelContext context = new KernelContext();
        TaskGraph graph = new TaskGraph("s0") //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds, context, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("kernelThreadId", warmup, iters, graph, grid1DLocal("s0.t0", size, local), null, () -> a.get(0) == 0 && a.get(size - 1) == size - 1 ? null : "a=" + a.get(size - 1));
    }

    private static Row kernelLocal(int warmup, int iters) throws TornadoExecutionPlanException {
        final int size = 1024;
        final int local = 256;
        IntArray a = ints(size, 0);
        for (int i = 0; i < size; i++) {
            a.set(i, i);
        }
        IntArray b = new IntArray(size);
        KernelContext context = new KernelContext();
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds02, context, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        return time("kernelLocal", warmup, iters, graph, grid1DLocal("s0.t0", size, local), null, () -> b.get(0) == 0 && b.get(size - 1) == size - 1 ? null : "b=" + b.get(size - 1));
    }

    private static Row kernelMatmul(int warmup, int iters) throws TornadoExecutionPlanException {
        final int size = 32;
        FloatArray a = floats(size * size, 1.0f);
        FloatArray b = floats(size * size, 1.0f);
        FloatArray c = new FloatArray(size * size);
        KernelContext context = new KernelContext();
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMatrixMultiplicationKernelContext::matrixMultiplication1D, context, a, b, c, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("kernelMatmul", warmup, iters, graph, grid1D("s0.t0", size), null, () -> close(c.get(0), (float) size));
    }

    private static Row matmul2d(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = MATRIX_N;
        Matrix2DFloat a = new Matrix2DFloat(n, n);
        Matrix2DFloat b = new Matrix2DFloat(n, n);
        Matrix2DFloat c = new Matrix2DFloat(n, n);
        fillMatrix2D(a, n, 1.0f);
        fillMatrix2D(b, n, 1.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMatrixTypes::computeMatrixMultiplication, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("matmul2d", warmup, iters, graph, null, null, () -> close(c.get(0, 0), n * 2.0f));
    }

    private static Row matrixSum(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 256;
        Matrix2DFloat a = new Matrix2DFloat(n, n);
        Matrix2DFloat b = new Matrix2DFloat(n, n);
        fillMatrix2D(a, n, 1.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestMatrixTypes::computeMatrixSum, a, b, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        return time("matrixSum", warmup, iters, graph, null, null, () -> close(b.get(0, 0), 2.0f));
    }

    private static Row matrixSumInt(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 256;
        Matrix2DInt a = new Matrix2DInt(n, n);
        Matrix2DInt b = new Matrix2DInt(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a.set(i, j, 1);
            }
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestMatrixTypes::computeMatrixSum, a, b, n, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        return time("matrixSumInt", warmup, iters, graph, null, null, () -> b.get(0, 0) == 2 ? null : "b=" + b.get(0, 0));
    }

    private static Row matrix3d(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 32;
        Matrix3DFloat a = new Matrix3DFloat(n, n, n);
        Matrix3DFloat b = new Matrix3DFloat(n, n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    a.set(i, j, k, 1.0f);
                }
            }
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestMatrixTypes::computeMatrixSum, a, b, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        return time("matrix3d", warmup, iters, graph, null, null, () -> close(b.get(0, 0, 0), 2.0f));
    }

    private static Row matrixFloat4(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 64;
        Matrix2DFloat4 a = new Matrix2DFloat4(n, n);
        Matrix2DFloat4 b = new Matrix2DFloat4(n, n);
        Float4 one = new Float4(1.0f, 1.0f, 1.0f, 1.0f);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a.set(i, j, one);
            }
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestMatrixTypes::computeMatrixSum, a, b, n, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        return time("matrixFloat4", warmup, iters, graph, null, null, () -> close(b.get(0, 0).getX(), 2.0f));
    }

    private static Row hilbert(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 256;
        FloatArray output = new FloatArray(n * n);
        TaskGraph graph = new TaskGraph("s0") //
                .task("t0", ComputeTests::hilbertComputation, output, n, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        return time("hilbert", warmup, iters, graph, null, null, () -> close(output.get(0), 1.0f));
    }

    private static Row dft(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 64;
        FloatArray inReal = floats(n, 1.0f);
        FloatArray inImag = floats(n, 0.0f);
        FloatArray outReal = new FloatArray(n);
        FloatArray outImag = new FloatArray(n);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inReal, inImag) //
                .task("t0", ComputeTests::computeDFT, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal);
        return time("dft", warmup, iters, graph, null, null, () -> close(outReal.get(0), (float) n));
    }

    private static Row dftFloat(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 64;
        FloatArray inReal = floats(n, 1.0f);
        FloatArray inImag = floats(n, 0.0f);
        FloatArray outReal = new FloatArray(n);
        FloatArray outImag = new FloatArray(n);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inReal, inImag) //
                .task("t0", ComputeTests::computeDFTFloat, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal);
        return time("dftFloat", warmup, iters, graph, null, null, () -> close(outReal.get(0), (float) n));
    }

    private static Row julia(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = 32;
        FloatArray hue = new FloatArray(n * n);
        FloatArray brightness = new FloatArray(n * n);
        TaskGraph graph = new TaskGraph("s0") //
                .task("t0", ComputeTests::juliaSetTornado, n, hue, brightness) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, brightness);
        return time("julia", warmup, iters, graph, null, null, () -> brightness.get(0) == 0.0f || brightness.get(0) == 1.0f ? null : "b=" + brightness.get(0));
    }

    private static Row mathExp(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray a = floats(N, 1.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestMath::testExpFloat, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("mathExp", warmup, iters, graph, null, null, () -> a.init(1.0f), () -> close(a.get(0), (float) Math.exp(1.0f)));
    }

    private static Row mathAbs(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray a = floats(N, -2.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestMath::testAbs, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("mathAbs", warmup, iters, graph, null, null, () -> a.init(-2.0f), () -> close(a.get(0), 2.0f));
    }

    private static Row mathPow(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray a = floats(N, 1.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestMath::testPow, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("mathPow", warmup, iters, graph, null, null, () -> a.init(1.0f), () -> close(a.get(0), 2.0f));
    }

    private static Row mathMin(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray a = floats(N, 1.0f);
        FloatArray b = floats(N, 2.0f);
        FloatArray c = new FloatArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMath::testMin, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("mathMin", warmup, iters, graph, null, null, () -> close(c.get(0), 1.0f));
    }

    private static Row mathFma(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray a = floats(N, 2.0f);
        FloatArray b = floats(N, 3.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMath::testFMA, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        return time("mathFma", warmup, iters, graph, null, null, () -> {
            a.init(2.0f);
            b.init(3.0f);
        }, () -> close(b.get(0), 8.0f));
    }

    private static Row mathCos(int warmup, int iters) throws TornadoExecutionPlanException {
        FloatArray a = floats(N, 0.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestTornadoMathCollection::testTornadoCos, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("mathCos", warmup, iters, graph, null, null, () -> a.init(0.0f), () -> close(a.get(0), 1.0f));
    }

    private static Row batch(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = BATCH_N;
        FloatArray a = floats(n, 1.0f);
        FloatArray b = new FloatArray(n);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestBatches::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        return time("batch", warmup, iters, graph, null, "1MB", () -> close(b.get(0), 101.0f));
    }

    private static Row batch3(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = BATCH_N;
        FloatArray a = floats(n, 1.0f);
        FloatArray b = floats(n, 2.0f);
        FloatArray c = new FloatArray(n);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBatches::compute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("batch3", warmup, iters, graph, null, "1MB", () -> close(c.get(0), 3.0f));
    }

    private static Row batchInt(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = BATCH_N;
        IntArray a = ints(n, 1);
        IntArray b = ints(n, 2);
        IntArray c = new IntArray(n);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBatches::compute, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        return time("batchInt", warmup, iters, graph, null, "1MB", () -> c.get(0) == 3 ? null : "c=" + c.get(0));
    }

    private static Row batchCopy(int warmup, int iters) throws TornadoExecutionPlanException {
        final int n = BATCH_N;
        FloatArray a = floats(n, 7.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestBatches::compute, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        return time("batchCopy", warmup, iters, graph, null, "1MB", () -> close(a.get(0), 7.0f));
    }

    private static Row time(String name, int warmup, int iters, TaskGraph graph, GridScheduler grid, String batch, Validator validator) throws TornadoExecutionPlanException {
        return time(name, warmup, iters, graph, grid, batch, null, validator);
    }

    private static Row time(String name, int warmup, int iters, TaskGraph graph, GridScheduler grid, String batch, Runnable restore, Validator validator) throws TornadoExecutionPlanException {
        ImmutableTaskGraph snapshot = graph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(snapshot)) {
            if (grid != null) {
                plan.withGridScheduler(grid);
            }
            if (batch != null) {
                plan.withBatch(batch);
            }
            for (int i = 0; i < warmup; i++) {
                if (restore != null) {
                    restore.run();
                }
                plan.execute();
            }
            long[] samples = new long[iters];
            for (int i = 0; i < iters; i++) {
                if (restore != null) {
                    restore.run();
                }
                long t0 = System.nanoTime();
                plan.execute();
                samples[i] = System.nanoTime() - t0;
            }
            String reason = validator.check();
            Stats stats = stats(samples);
            return new Row(name, stats.median, stats.p10, stats.p90, reason == null, reason == null ? "" : reason);
        }
    }

    private static GridScheduler grid1D(String task, int n) {
        WorkerGrid1D worker = new WorkerGrid1D(n);
        worker.setGlobalWork(n, 1, 1);
        worker.setLocalWorkToNull();
        return new GridScheduler(task, worker);
    }

    private static GridScheduler grid1DLocal(String task, int n, int local) {
        WorkerGrid1D worker = new WorkerGrid1D(n);
        worker.setGlobalWork(n, 1, 1);
        worker.setLocalWork(local, 1, 1);
        return new GridScheduler(task, worker);
    }

    private static FloatArray floats(int n, float value) {
        FloatArray array = new FloatArray(n);
        array.init(value);
        return array;
    }

    private static IntArray ints(int n, int value) {
        IntArray array = new IntArray(n);
        array.init(value);
        return array;
    }

    private static void fillMatrix2D(Matrix2DFloat matrix, int n, float value) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                matrix.set(i, j, value);
            }
        }
    }

    private static String close(float actual, float expected) {
        return Math.abs(actual - expected) < 1e-3f ? null : "got=" + actual;
    }

    private static Stats stats(long[] samples) {
        long[] copy = Arrays.copyOf(samples, samples.length);
        Arrays.sort(copy);
        return new Stats(percentile(copy, 0.5), percentile(copy, 0.10), percentile(copy, 0.90));
    }

    private static double percentile(long[] sorted, double p) {
        if (sorted.length == 1) {
            return sorted[0];
        }
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sorted[lo];
        }
        double w = idx - lo;
        return sorted[lo] * (1.0 - w) + sorted[hi] * w;
    }

    private static double nsToUs(double ns) {
        return ns / 1000.0;
    }

    @FunctionalInterface
    private interface Validator {
        String check();
    }

    private record Row(String name, double medianNs, double p10Ns, double p90Ns, boolean ok, String reason) {
    }

    private record Stats(double median, double p10, double p90) {
    }
}
