/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.unittests.matrices;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestMatrices extends TornadoTestBase {

    public static void fillMatrix(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            Arrays.fill(values[i], i);
        }
    }

    public static void fillMatrix2(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            for (int j = 0; j < values.length; j++) {
                values[i][j] = i;
            }
        }
    }

    public static void fillMatrix3(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            for (@Parallel int j = 0; j < values.length; j++) {
                values[i][j] = i;
            }
        }
    }

    public static void matrixVector(float[] matrix, float[] vector, float[] result, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            float sum = 0.0f;
            for (int j = 0; j < size; j++) {
                sum += matrix[i * size + j] * vector[j];
            }
            result[i] = sum;
        }
    }

    public static void matrixInit1D(float[] result, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result[i * size + j] = 100 + i;
            }
        }
    }

    public static void matrixInit2D(float[] result, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                result[i * size + j] = 120;
            }
        }
    }

    public static void matrixAddition1D(float[] matrixA, float[] matrixB, float[] result, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int position = i * size + j;
                result[position] = matrixA[position] + matrixB[position];
            }
        }
    }

    public static void matrixAddition2D(float[] matrixA, float[] matrixB, float[] result, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                result[i * size + j] = matrixA[i * size + j] + matrixB[i * size + j];
            }
        }
    }

    public static void matrixMultiplication(final float[] A, final float[] B, final float[] C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A[(i * size) + k] * B[(k * size) + j];
                }
                C[(i * size) + j] = sum;
            }
        }
    }

    public static void matrixMultiplicationParallelInduction(final float[] A, final float[] B, final float[] C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (int j = 0; j < i; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A[(i * size) + k] * B[(k * size) + j];
                }
                C[(i * size) + j] = sum;
            }
        }
    }

    public static void matrixUsageOfParallelInduction(final float[] A, final float[] B, final float[] C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (int j = 0; j < i; j++) {
                float sum = 0.0f;
                for (int k = 0; k < i; k++) {
                    sum += A[(i * size) + k] * B[(k * size) + j];
                }
                C[(i * size) + j] = sum;
            }
        }
    }

    @Ignore
    @Test
    public void testFillMatrix() {
        final int numElements = 16;
        int[][] a = new int[numElements][numElements];

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::fillMatrix, a)
                .streamOut(new Object[]{a});
	    //@formatter:on
        t.warmup();
        t.execute();

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(i, a[i][j]);
            }
        }
    }

    @Test
    public void testFillMatrix2() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int numElements = 4;
        int[][] a = new int[numElements][numElements];

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
		        .task("t0", TestMatrices::fillMatrix2, a)
		        .streamOut(new Object[] { a });
	    //@formatter:on

        t.warmup();
        t.execute();

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(i, a[i][j]);
            }
        }
    }

    @Test
    public void testFillMatrix3() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int numElements = 16;
        int[][] a = new int[numElements][numElements];

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
		        .task("t0", TestMatrices::fillMatrix3, a)
		        .streamOut(new Object[] { a });
		//@formatter:on

        t.warmup();
        t.execute();

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(i, a[i][j]);
            }
        }
    }

    @Test
    public void testMatrixVectorSmall() {
        final int N = 4;
        float[] matrix = new float[N * N];
        float[] vector = new float[N];
        float[] result = new float[N];
        float[] resultSeq = new float[N];

        Random r = new Random();
        IntStream.range(0, N).parallel().forEach(idx -> {
            vector[idx] = r.nextFloat();
        });
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrix[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixVector, matrix, vector, result, N)
                .streamOut(result);
        //@formatter:on
        t.execute();

        matrixVector(matrix, vector, resultSeq, N);

        for (int i = 0; i < vector.length; i++) {
            assertEquals(resultSeq[i], result[i], 0.01f);
        }
    }

    @Test
    public void testMatrixVector() {
        final int N = 256;
        float[] matrix = new float[N * N];
        float[] vector = new float[N];
        float[] result = new float[N];
        float[] resultSeq = new float[N];

        Random r = new Random();
        IntStream.range(0, N).parallel().forEach(idx -> {
            vector[idx] = r.nextFloat();
        });
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrix[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixVector, matrix, vector, result, N)
                .streamOut(result);
        //@formatter:on
        t.execute();

        matrixVector(matrix, vector, resultSeq, N);

        for (int i = 0; i < vector.length; i++) {
            assertEquals(resultSeq[i], result[i], 0.01f);
        }
    }

    @Test
    public void testMatrixInit1DSmall() {
        final int N = 4;
        float[] matrix = new float[N * N];
        float[] resultSeq = new float[N * N];

        TaskSchedule t = new TaskSchedule("s0") //
                .task("t0", TestMatrices::matrixInit1D, matrix, N) //
                .streamOut(matrix); //

        t.execute();

        matrixInit1D(resultSeq, N);

        for (int i = 0; i < matrix.length; i++) {
            assertEquals(resultSeq[i], matrix[i], 0.01f);
        }
    }

    @Test
    public void testMatrixInit1D() {
        final int N = 256;
        float[] matrix = new float[N * N];
        float[] resultSeq = new float[N * N];

        TaskSchedule t = new TaskSchedule("s0") //
                .task("t0", TestMatrices::matrixInit1D, matrix, N) //
                .streamOut(matrix); //

        t.execute();

        matrixInit1D(resultSeq, N);

        for (int i = 0; i < matrix.length; i++) {
            assertEquals(resultSeq[i], matrix[i], 0.01f);
        }
    }

    @Test
    public void testMatrixInit2DSmall() {
        final int N = 4;
        float[] matrix = new float[N * N];
        float[] resultSeq = new float[N * N];

        TaskSchedule t = new TaskSchedule("s0") //
                .task("t0", TestMatrices::matrixInit2D, matrix, N) //
                .streamOut(matrix); //

        t.execute();

        matrixInit2D(resultSeq, N);

        for (int i = 0; i < matrix.length; i++) {
            assertEquals(resultSeq[i], matrix[i], 0.01f);
        }
    }

    @Test
    public void testMatrixInit2D() {
        final int N = 1024;
        float[] matrix = new float[N * N];
        float[] resultSeq = new float[N * N];

        TaskSchedule t = new TaskSchedule("s0") //
                .task("t0", TestMatrices::matrixInit2D, matrix, N) //
                .streamOut(matrix); //

        t.execute();

        matrixInit2D(resultSeq, N);

        for (int i = 0; i < matrix.length; i++) {
            assertEquals(resultSeq[i], matrix[i], 0.01f);
        }
    }

    @Test
    public void testMatrixAddition1DSmall() {
        final int N = 4;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] result = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixB[idx] = r.nextFloat();
        });
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
        });

        TaskSchedule t = new TaskSchedule("s0") //
                .task("t0", TestMatrices::matrixAddition1D, matrixA, matrixB, result, N) //
                .streamOut(result); //

        t.execute();

        matrixAddition1D(matrixA, matrixB, resultSeq, N);

        for (int i = 0; i < matrixB.length; i++) {
            assertEquals(resultSeq[i], result[i], 0.01f);
        }
    }

    @Test
    public void testMatrixAddition1D() {
        final int N = 1024;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] result = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixB[idx] = r.nextFloat();
        });
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
        });

        TaskSchedule t = new TaskSchedule("s0") //
                .task("t0", TestMatrices::matrixAddition1D, matrixA, matrixB, result, N) //
                .streamOut(result); //

        t.execute();

        matrixAddition1D(matrixA, matrixB, resultSeq, N);

        for (int i = 0; i < matrixB.length; i++) {
            assertEquals(resultSeq[i], result[i], 0.01f);
        }
    }

    @Test
    public void testMatrixAddition2DSmall() {
        final int N = 4;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] result = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N).parallel().forEach(idx -> {
            matrixB[idx] = r.nextFloat();
        });
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixAddition2D, matrixA, matrixB, result, N)
                .streamOut(result);
        //@formatter:on
        t.execute();

        matrixAddition2D(matrixA, matrixB, resultSeq, N);

        for (int i = 0; i < matrixB.length; i++) {
            assertEquals(resultSeq[i], result[i], 0.01f);
        }
    }

    @Test
    public void testMatrixAddition2D() {
        final int N = 1024;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] result = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N).parallel().forEach(idx -> {
            matrixB[idx] = r.nextFloat();
        });
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixAddition2D, matrixA, matrixB, result, N)
                .streamOut(result);
        //@formatter:on
        t.execute();

        matrixAddition2D(matrixA, matrixB, resultSeq, N);

        for (int i = 0; i < matrixB.length; i++) {
            assertEquals(resultSeq[i], result[i], 0.01f);
        }
    }

    public static void copyMatrix2D(final float[][] matrixA, final float[][] matrixB) {
        for (@Parallel int i = 0; i < matrixA.length; i++) {
            for (@Parallel int j = 0; j < matrixA[i].length; j++) {
                matrixB[i][j] = matrixA[i][j];
            }
        }
    }

    @Test
    public void testCopyMatrix2D() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int N = 32;
        float[][] matrixA = new float[N][N];
        float[][] matrixB = new float[N][N];

        Random random = new Random();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matrixA[i][j] = random.nextFloat();
            }
        }

        TaskSchedule ts = new TaskSchedule("s0").task("s0", TestMatrices::copyMatrix2D, matrixA, matrixB).streamOut(new float[][][] { matrixB });
        ts.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(matrixA[i][j], matrixB[i][j], 0.01);
            }
        }
    }

    @Test
    public void testMatrixMultiplicationSmall() {
        final int N = 32;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixC = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
            matrixB[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixMultiplication, matrixA, matrixB, matrixC, N)
                .streamOut(matrixC);
        //@formatter:on
        t.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                float sum = 0.0f;
                for (int k = 0; k < N; k++) {
                    sum += matrixA[(i * N) + k] * matrixB[(k * N) + j];
                }
                resultSeq[(i * N) + j] = sum;
            }
        }

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(resultSeq[i * N + j], matrixC[i * N + j], 0.1);
            }
        }
    }

    @Test
    public void testMatrixMultiplication() {
        final int N = 256;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixC = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
            matrixB[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixMultiplication, matrixA, matrixB, matrixC, N)
                .streamOut(matrixC);
        //@formatter:on
        t.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                float sum = 0.0f;
                for (int k = 0; k < N; k++) {
                    sum += matrixA[(i * N) + k] * matrixB[(k * N) + j];
                }
                resultSeq[(i * N) + j] = sum;
            }
        }

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(resultSeq[i * N + j], matrixC[i * N + j], 0.1);
            }
        }
    }

    @Test
    public void testParallelInductionVariablesAsBounds() {
        final int N = 256;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixC = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
            matrixB[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixMultiplicationParallelInduction, matrixA, matrixB, matrixC, N)
                .streamOut(matrixC);
        //@formatter:on
        t.execute();

        matrixMultiplicationParallelInduction(matrixA, matrixB, resultSeq, N);

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(resultSeq[i * N + j], matrixC[i * N + j], 0.1f);
            }
        }
    }

    @Test
    public void testMultipleParallelInductionVariableLoopUsage() {
        final int N = 256;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixC = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
            matrixB[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixUsageOfParallelInduction, matrixA, matrixB, matrixC, N)
                .streamOut(matrixC);
        //@formatter:on
        t.execute();

        matrixUsageOfParallelInduction(matrixA, matrixB, resultSeq, N);

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(resultSeq[i * N + j], matrixC[i * N + j], 0.1f);
            }
        }
    }

    public static void testAdd(long[][] matrix) {
        for (@Parallel int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                matrix[i][j] = matrix[i][j] + i;
            }
        }
    }

    @Test
    public void testAddMatrix() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        int N = 128;
        Random random = new Random();
        int[] secondDimSizes = new int[] { 10, 400, 7, 29, 44, 1001 };
        long[][] matrix = new long[N][];
        long[][] matrixSeq = new long[N][];
        int counter = 0;
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = new long[secondDimSizes[counter % secondDimSizes.length]];
            matrixSeq[i] = new long[secondDimSizes[counter % secondDimSizes.length]];
            counter++;

            for (int j = 0; j < matrix[i].length; j++) {
                int someNumber = random.nextInt();
                matrix[i][j] = someNumber;
                matrixSeq[i][j] = someNumber;
            }
        }

        testAdd(matrixSeq);

        TaskSchedule ts = new TaskSchedule("s0").task("t0", TestMatrices::testAdd, matrix)
                // Wrap the matrix in a 1D array because the streamOut varargs will
                // automatically unwrap it.
                .streamOut(new long[][][] { matrix });
        ts.execute();

        for (int i = 0; i < matrix.length; i++) {
            Assert.assertArrayEquals(matrixSeq[i], matrix[i]);
        }
    }

    public static void testAddMultiple(float[][] first, float[][] second) {
        for (@Parallel int i = 0; i < first.length; i++) {
            for (@Parallel int j = 0; j < first.length; j++) {
                first[i][j] = first[i][j] + second[i][j];
            }
        }
    }

    @Test
    public void testAddMatrixMultiple() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        int N = 128;
        Random random = new Random();
        float[][] firstMatrix = new float[N][];
        float[][] secondMatrix = new float[N][];
        float[][] firstMatrixSeq = new float[N][];
        float[][] secondMatrixSeq = new float[N][];
        for (int i = 0; i < firstMatrix.length; i++) {
            firstMatrix[i] = new float[N];
            secondMatrix[i] = new float[N];
            firstMatrixSeq[i] = new float[N];
            secondMatrixSeq[i] = new float[N];

            for (int j = 0; j < firstMatrix[i].length; j++) {
                float someNumber = random.nextFloat() * 10;
                firstMatrix[i][j] = someNumber;
                firstMatrixSeq[i][j] = someNumber;

                someNumber = random.nextFloat() * 100;
                secondMatrix[i][j] = someNumber;
                secondMatrixSeq[i][j] = someNumber;
            }
        }

        testAddMultiple(firstMatrixSeq, secondMatrixSeq);

        TaskSchedule ts = new TaskSchedule("s0").task("t0", TestMatrices::testAddMultiple, firstMatrix, secondMatrix)
                // Wrap the matrix in a 1D array because the streamOut varargs will
                // automatically unwrap it.
                .streamOut(new float[][][] { firstMatrix });
        ts.execute();

        for (int i = 0; i < firstMatrix.length; i++) {
            Assert.assertArrayEquals(firstMatrixSeq[i], firstMatrix[i], 0.01f);
        }
    }

}
