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
package uk.ac.manchester.tornado.unittests.clblast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.ac.manchester.tornado.clblast.enums.CLBlastLayout.COLUMN_MAJOR;
import static uk.ac.manchester.tornado.clblast.enums.CLBlastLayout.ROW_MAJOR;
import static uk.ac.manchester.tornado.clblast.enums.CLBlastTranspose.NO;
import static uk.ac.manchester.tornado.clblast.enums.CLBlastTranspose.YES;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.clblast.CLBlast;
import uk.ac.manchester.tornado.clblast.provider.CLBlastLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMOpenCLNotSupported;

/** Run with tornado-test -V uk.ac.manchester.tornado.unittests.clblast.TestCLBlast. */
public class TestCLBlast extends TornadoTestBase {

    @BeforeEach
    public void requireCLBlast() {
        if (getTornadoRuntime().getDefaultDevice().getTornadoVMBackend() != TornadoVMBackendType.OPENCL) {
            throw new TornadoVMOpenCLNotSupported("CLBlast requires the OpenCL backend");
        }
        if (!CLBlastLibraryProvider.isAvailable()) {
            throw new TornadoVMOpenCLNotSupported("CLBlast is not installed");
        }
    }

    @Test
    public void rowMajorSgemmWithBetaAndRepeatedExecution() throws TornadoExecutionPlanException {
        int m = 2;
        int n = 3;
        int k = 4;
        FloatArray a = new FloatArray(m * k);
        FloatArray b = new FloatArray(k * n);
        FloatArray c = new FloatArray(m * n);
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, i + 1);
        }
        for (int i = 0; i < b.getSize(); i++) {
            b.set(i, (i + 1) * 0.5f);
        }
        c.init(2f);
        float[] expected = new float[m * n];
        java.util.Arrays.fill(expected, 2f);
        TaskGraph graph = new TaskGraph("clblastGemm")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c)
                .libraryTask("gemm", CLBlast::sgemm, ROW_MAJOR.value(), NO.value(), NO.value(), m, n, k, 1.5f, a, k, b, n, 0.25f, c, n)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            for (int run = 0; run < 2; run++) {
                plan.execute();
                for (int row = 0; row < m; row++) {
                    for (int col = 0; col < n; col++) {
                        float sum = 0;
                        for (int inner = 0; inner < k; inner++) {
                            sum += a.get(row * k + inner) * b.get(inner * n + col);
                        }
                        int index = row * n + col;
                        expected[index] = 1.5f * sum + 0.25f * expected[index];
                        assertEquals(expected[index], c.get(index), 0.001f);
                    }
                }
            }
        }
    }

    @Test
    public void columnMajorDgemmWithTranspose() throws TornadoExecutionPlanException {
        int m = 2;
        int n = 3;
        int k = 4;
        DoubleArray a = new DoubleArray(k * m);
        DoubleArray b = new DoubleArray(k * n);
        DoubleArray c = new DoubleArray(m * n);
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, i + 0.5);
        }
        for (int i = 0; i < b.getSize(); i++) {
            b.set(i, i + 0.25);
        }
        TaskGraph graph = new TaskGraph("clblastDgemm")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .libraryTask("gemm", CLBlast::dgemm, COLUMN_MAJOR.value(), YES.value(), NO.value(), m, n, k, 1.0, a, k, b, k, 0.0, c, m)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int row = 0; row < m; row++) {
            for (int col = 0; col < n; col++) {
                double sum = 0;
                for (int inner = 0; inner < k; inner++) {
                    sum += a.get(inner + row * k) * b.get(inner + col * k);
                }
                assertEquals(sum, c.get(row + col * m), 1e-9);
            }
        }
    }

    public static void fill(FloatArray x) {
        for (@Parallel int i = 0; i < x.getSize(); i++) {
            x.set(i, i + 1);
        }
    }

    public static void twice(FloatArray input, FloatArray output) {
        output.set(0, 2 * input.get(0));
    }

    @Test
    public void jitProducerAndConsumerAroundStridedVectorOperations() throws TornadoExecutionPlanException {
        FloatArray x = new FloatArray(5);
        FloatArray y = new FloatArray(7);
        FloatArray result = new FloatArray(1);
        FloatArray output = new FloatArray(1);
        y.init(1f);
        TaskGraph graph = new TaskGraph("clblastVectors")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, y)
                .task("fill", TestCLBlast::fill, x)
                .libraryTask("axpy", CLBlast::saxpy, 3, 2f, x, 2, y, 3)
                .libraryTask("dot", CLBlast::sdot, 3, x, 2, y, 3, result)
                .task("twice", TestCLBlast::twice, result, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y, output);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        assertEquals(3f, y.get(0), 0.001f);
        assertEquals(7f, y.get(3), 0.001f);
        assertEquals(11f, y.get(6), 0.001f);
        assertEquals(1f, y.get(1), 0.001f);
        assertEquals(158f, output.get(0), 0.001f);
    }

    @Test
    public void sgemvWithPaddingStridesAndBeta() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(8);
        FloatArray x = new FloatArray(5);
        FloatArray y = new FloatArray(3);
        a.init(99);
        x.init(99);
        y.init(2);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                a.set(row * 4 + col, row * 3 + col + 1);
            }
        }
        x.set(0, 1);
        x.set(2, 2);
        x.set(4, 3);
        TaskGraph graph = new TaskGraph("clblastsgemv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, x, y)
                .libraryTask("gemv", CLBlast::sgemv, ROW_MAJOR.value(), NO.value(), 2, 3, 1f, a, 4, x, 2, 0.5f, y, 2)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        assertEquals(15, y.get(0), 0.001);
        assertEquals(33, y.get(2), 0.001);
        assertEquals(2, y.get(1), 0.001);
    }

    @Test
    public void dgemvWithPaddingStridesAndBeta() throws TornadoExecutionPlanException {
        DoubleArray a = new DoubleArray(8);
        DoubleArray x = new DoubleArray(5);
        DoubleArray y = new DoubleArray(3);
        a.init(99);
        x.init(99);
        y.init(2);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                a.set(row * 4 + col, row * 3 + col + 1);
            }
        }
        x.set(0, 1);
        x.set(2, 2);
        x.set(4, 3);
        TaskGraph graph = new TaskGraph("clblastdgemv")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, x, y)
                .libraryTask("gemv", CLBlast::dgemv, ROW_MAJOR.value(), NO.value(), 2, 3, 1.0, a, 4, x, 2, 0.5, y, 2)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        assertEquals(15, y.get(0), 1e-9);
        assertEquals(33, y.get(2), 1e-9);
        assertEquals(2, y.get(1), 1e-9);
    }

    @Test
    public void doubleAxpyAndDot() throws TornadoExecutionPlanException {
        DoubleArray x = new DoubleArray(3);
        DoubleArray y = new DoubleArray(3);
        DoubleArray result = new DoubleArray(1);
        for (int i = 0; i < 3; i++) {
            x.set(i, i + 1);
        }
        y.init(1);
        TaskGraph graph = new TaskGraph("clblastDoubleVectors")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .libraryTask("axpy", CLBlast::daxpy, 3, 2.0, x, 1, y, 1)
                .libraryTask("dot", CLBlast::ddot, 3, x, 1, y, 1, result)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        assertEquals(34.0, result.get(0), 1e-9);
    }

    private static FloatArray randomArray(int size, long seed) {
        Random random = new Random(seed);
        FloatArray array = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, 2 * random.nextFloat() - 1);
        }
        return array;
    }

    private static int matrixIndex(boolean rowMajor, int row, int col, int ld) {
        return rowMajor ? row * ld + col : col * ld + row;
    }

    private static float matrixElement(FloatArray matrix, boolean rowMajor, boolean transpose, int row, int col, int ld) {
        return matrix.get(matrixIndex(rowMajor, transpose ? col : row, transpose ? row : col, ld));
    }

    @Test
    public void largeOddSgemmAllLayoutsAndTransposes() throws TornadoExecutionPlanException {
        int m = 301;
        int n = 257;
        int k = 513;
        for (boolean rowMajor : new boolean[] { true, false }) {
            for (boolean transA : new boolean[] { false, true }) {
                for (boolean transB : new boolean[] { false, true }) {
                    int aRows = transA ? k : m;
                    int aCols = transA ? m : k;
                    int bRows = transB ? n : k;
                    int bCols = transB ? k : n;
                    int lda = (rowMajor ? aCols : aRows) + 3;
                    int ldb = (rowMajor ? bCols : bRows) + 5;
                    int ldc = (rowMajor ? n : m) + 7;
                    FloatArray a = randomArray((rowMajor ? aRows : aCols) * lda, 1);
                    FloatArray b = randomArray((rowMajor ? bRows : bCols) * ldb, 2);
                    FloatArray c = randomArray((rowMajor ? m : n) * ldc, 3);
                    float[] initialC = new float[c.getSize()];
                    for (int i = 0; i < initialC.length; i++) {
                        initialC[i] = c.get(i);
                    }
                    TaskGraph graph = new TaskGraph("largeGemm")
                            .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c)
                            .libraryTask("gemm", CLBlast::sgemm, (rowMajor ? ROW_MAJOR : COLUMN_MAJOR).value(),
                                    (transA ? YES : NO).value(), (transB ? YES : NO).value(), m, n, k, 0.75f, a, lda, b, ldb, -0.5f, c, ldc)
                            .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
                    try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                        plan.execute();
                    }
                    String configuration = "rowMajor=" + rowMajor + " transA=" + transA + " transB=" + transB;
                    for (int row = 0; row < m; row++) {
                        for (int col = 0; col < n; col++) {
                            double sum = 0;
                            for (int inner = 0; inner < k; inner++) {
                                sum += (double) matrixElement(a, rowMajor, transA, row, inner, lda) * matrixElement(b, rowMajor, transB, inner, col, ldb);
                            }
                            int index = matrixIndex(rowMajor, row, col, ldc);
                            double expected = 0.75 * sum - 0.5 * initialC[index];
                            assertEquals(expected, c.get(index), 2e-3, configuration + " row=" + row + " col=" + col);
                        }
                    }
                    int majorSize = rowMajor ? m : n;
                    int minorSize = rowMajor ? n : m;
                    for (int major = 0; major < majorSize; major++) {
                        for (int padding = minorSize; padding < ldc; padding++) {
                            int index = major * ldc + padding;
                            assertEquals(initialC[index], c.get(index), configuration + " padding index=" + index);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void sgemmBetaZeroIgnoresNaNInC() throws TornadoExecutionPlanException {
        int n = 64;
        FloatArray a = randomArray(n * n, 4);
        FloatArray b = randomArray(n * n, 5);
        FloatArray c = new FloatArray(n * n);
        c.init(Float.NaN);
        TaskGraph graph = new TaskGraph("betaZero")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c)
                .libraryTask("gemm", CLBlast::sgemm, ROW_MAJOR.value(), NO.value(), NO.value(), n, n, n, 1f, a, n, b, n, 0f, c, n)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                double expected = 0;
                for (int inner = 0; inner < n; inner++) {
                    expected += (double) a.get(row * n + inner) * b.get(inner * n + col);
                }
                int index = row * n + col;
                assertTrue(Float.isFinite(c.get(index)), "beta=0 must ignore the initial NaN at index=" + index);
                assertEquals(expected, c.get(index), 1e-3, "row=" + row + " col=" + col);
            }
        }
    }

    public static void slowFill(KernelContext context, FloatArray array, FloatArray value, int spins) {
        int id = context.globalIdx;
        float initial = value.get(0);
        float result = initial;
        for (int spin = 0; spin < spins; spin++) {
            result = result * 1.0000001f - initial * 0.0000001f;
        }
        for (int i = id; i < array.getSize(); i += context.globalGroupSizeX) {
            array.set(i, result);
        }
    }

    @Test
    public void queueOrderingAndQueuePerThread() throws TornadoExecutionPlanException, InterruptedException, ExecutionException {
        int n = 256;
        FloatArray a = new FloatArray(n * n);
        FloatArray identity = new FloatArray(n * n);
        FloatArray c = new FloatArray(n * n);
        FloatArray value = new FloatArray(1);
        for (int i = 0; i < n; i++) {
            identity.set(i * n + i, 1f);
        }
        value.set(0, 1f);
        TaskGraph graph = new TaskGraph("order")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, identity)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, value)
                .task("fill", TestCLBlast::slowFill, new KernelContext(), a, value, 200000)
                .libraryTask("gemm", CLBlast::sgemm, ROW_MAJOR.value(), NO.value(), NO.value(), n, n, n, 1f, identity, n, a, n, 0f, c, n)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        WorkerGrid grid = new WorkerGrid1D(256);
        grid.setLocalWork(256, 1, 1);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("order.fill", grid)).execute();
            for (int i = 0; i < c.getSize(); i++) {
                assertEquals(1f, c.get(i), 1e-3f, "first thread, index=" + i);
            }
            value.set(0, 2f);
            c.init(0f);
            // FutureTask publishes the result and propagates failures from the worker thread.
            FutureTask<Void> execution = new FutureTask<>(() -> {
                plan.execute();
                return null;
            });
            Thread thread = new Thread(execution, "clblast-plan-worker");
            thread.start();
            execution.get();
            for (int i = 0; i < c.getSize(); i++) {
                assertEquals(2f, c.get(i), 1e-3f, "other thread, index=" + i);
            }
        }
    }

    public static void addOne(FloatArray array) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) + 1f);
        }
    }

    @Test
    public void sharedBufferAcrossTaskGraphs() throws TornadoExecutionPlanException {
        int n = 128;
        FloatArray matrix = randomArray(n * n, 6);
        FloatArray vector = randomArray(n, 7);
        FloatArray output = new FloatArray(n);
        float[] original = new float[matrix.getSize()];
        for (int i = 0; i < original.length; i++) {
            original[i] = matrix.get(i);
        }
        TaskGraph producer = new TaskGraph("producer")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrix)
                .task("mutate", TestCLBlast::addOne, matrix)
                .persistOnDevice(matrix);
        TaskGraph consumer = new TaskGraph("consumer")
                .consumeFromDevice(producer.getTaskGraphName(), matrix)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, vector)
                .libraryTask("gemv", CLBlast::sgemv, ROW_MAJOR.value(), NO.value(), n, n, 1f, matrix, n, vector, 1, 0f, output, 1)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
        }
        for (int i = 0; i < original.length; i++) {
            assertEquals(original[i], matrix.get(i), "producer data must stay on the device, index=" + i);
        }
        for (int row = 0; row < n; row++) {
            double expected = 0;
            for (int col = 0; col < n; col++) {
                expected += (double) (original[row * n + col] + 1f) * vector.get(col);
            }
            assertEquals(expected, output.get(row), 1e-3, "row=" + row);
        }
    }

    @Test
    public void repeatedLargeSgemmWithDeferredReadback() throws TornadoExecutionPlanException {
        int n = 2048;
        FloatArray a = randomArray(n * n, 8);
        FloatArray b = randomArray(n * n, 9);
        FloatArray c = new FloatArray(n * n);
        TaskGraph graph = new TaskGraph("repeatedGemm")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
                .libraryTask("gemm", CLBlast::sgemm, ROW_MAJOR.value(), NO.value(), NO.value(), n, n, n, 1f, a, n, b, n, 0f, c, n)
                .transferToHost(DataTransferMode.UNDER_DEMAND, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            for (int run = 0; run < 20; run++) {
                plan.execute();
            }
            TornadoExecutionResult result = plan.execute();
            result.transferToHost(c);
        }
        // Check complete rows at matrix/tile boundaries and in the interior.
        for (int row : new int[] { 0, 31, 32, n / 2, n - 1 }) {
            for (int col = 0; col < n; col++) {
                double expected = 0;
                for (int inner = 0; inner < n; inner++) {
                    expected += (double) a.get(row * n + inner) * b.get(inner * n + col);
                }
                assertEquals(expected, c.get(row * n + col), 1e-2, "row=" + row + " col=" + col);
            }
        }
    }
}
