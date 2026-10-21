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
import static uk.ac.manchester.tornado.clblast.enums.CLBlastLayout.COLUMN_MAJOR;
import static uk.ac.manchester.tornado.clblast.enums.CLBlastLayout.ROW_MAJOR;
import static uk.ac.manchester.tornado.clblast.enums.CLBlastTranspose.NO;
import static uk.ac.manchester.tornado.clblast.enums.CLBlastTranspose.YES;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.clblast.CLBlast;
import uk.ac.manchester.tornado.clblast.enums.CLBlastDiagonal;
import uk.ac.manchester.tornado.clblast.enums.CLBlastLayout;
import uk.ac.manchester.tornado.clblast.enums.CLBlastSide;
import uk.ac.manchester.tornado.clblast.enums.CLBlastTranspose;
import uk.ac.manchester.tornado.clblast.enums.CLBlastTriangle;
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

    @Test
    public void strmmLayoutsSidesTrianglesTransposesAndDiagonals() throws TornadoExecutionPlanException {
        for (CLBlastLayout layout : CLBlastLayout.values()) {
            for (CLBlastSide side : CLBlastSide.values()) {
                for (CLBlastTriangle triangle : CLBlastTriangle.values()) {
                    for (CLBlastTranspose transpose : CLBlastTranspose.values()) {
                        for (CLBlastDiagonal diagonal : CLBlastDiagonal.values()) {
                            checkStrmm(layout, side, triangle, transpose, diagonal);
                        }
                    }
                }
            }
        }
    }

    private static int matrixIndex(CLBlastLayout layout, int row, int col, int ld) {
        return layout == ROW_MAJOR ? row * ld + col : col * ld + row;
    }

    private static float triangularElement(FloatArray a, int lda, CLBlastLayout layout, CLBlastTriangle triangle,
            CLBlastTranspose transpose, CLBlastDiagonal diagonal, int row, int col) {
        int storedRow = transpose == NO ? row : col;
        int storedCol = transpose == NO ? col : row;
        if (storedRow == storedCol && diagonal == CLBlastDiagonal.UNIT) {
            return 1;
        }
        if ((triangle == CLBlastTriangle.UPPER && storedRow > storedCol) || (triangle == CLBlastTriangle.LOWER && storedRow < storedCol)) {
            return 0;
        }
        return a.get(matrixIndex(layout, storedRow, storedCol, lda));
    }

    private static void checkStrmm(CLBlastLayout layout, CLBlastSide side, CLBlastTriangle triangle,
            CLBlastTranspose transpose, CLBlastDiagonal diagonal) throws TornadoExecutionPlanException {
        int m = 3;
        int n = 2;
        int order = side == CLBlastSide.LEFT ? m : n;
        int lda = order + 2;
        int ldb = (layout == ROW_MAJOR ? n : m) + 1;
        FloatArray a = new FloatArray(order * lda);
        FloatArray b = new FloatArray((layout == ROW_MAJOR ? m : n) * ldb);
        // Poison the unused triangle, unit diagonal and A padding to detect accidental reads.
        a.init(Float.NaN);
        b.init(-777f);
        for (int row = 0; row < order; row++) {
            for (int col = 0; col < order; col++) {
                boolean stored = triangle == CLBlastTriangle.UPPER ? row <= col : row >= col;
                if (stored && (row != col || diagonal == CLBlastDiagonal.NON_UNIT)) {
                    a.set(matrixIndex(layout, row, col, lda), 0.5f * (row + 1) + col);
                }
            }
        }
        for (int row = 0; row < m; row++) {
            for (int col = 0; col < n; col++) {
                b.set(matrixIndex(layout, row, col, ldb), 3 * row + col + 1.25f);
            }
        }
        float alpha = -0.75f;
        TaskGraph graph = new TaskGraph("clblastStrmm")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
                .libraryTask("trmm", CLBlast::strmm, layout.value(), side.value(), triangle.value(), transpose.value(), diagonal.value(),
                        m, n, alpha, a, lda, b, ldb)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        String description = layout + "/" + side + "/" + triangle + "/" + transpose + "/" + diagonal;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            for (int run = 0; run < 2; run++) {
                float[] expected = new float[b.getSize()];
                for (int i = 0; i < expected.length; i++) {
                    expected[i] = b.get(i);
                }
                for (int row = 0; row < m; row++) {
                    for (int col = 0; col < n; col++) {
                        float sum = 0;
                        for (int inner = 0; inner < order; inner++) {
                            if (side == CLBlastSide.LEFT) {
                                sum += triangularElement(a, lda, layout, triangle, transpose, diagonal, row, inner) * b.get(matrixIndex(layout, inner, col, ldb));
                            } else {
                                sum += b.get(matrixIndex(layout, row, inner, ldb)) * triangularElement(a, lda, layout, triangle, transpose, diagonal, inner, col);
                            }
                        }
                        expected[matrixIndex(layout, row, col, ldb)] = alpha * sum;
                    }
                }
                plan.execute();
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], b.get(i), 1e-4f, description + " run=" + run + " index=" + i);
                }
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
}
