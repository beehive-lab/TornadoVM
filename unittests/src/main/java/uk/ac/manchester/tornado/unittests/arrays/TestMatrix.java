package uk.ac.manchester.tornado.unittests.arrays;

import org.junit.Assert;
import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackend;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Random;

public class TestMatrix extends TornadoTestBase {

    public static void testAdd(long[][] matrix) {
        for (@Parallel int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                matrix[i][j] = matrix[i][j] + i;
            }
        }
    }

    @Test
    public void testAddMatrix() {
        assertNotBackend(TornadoVMBackend.PTX);

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

        TaskSchedule ts = new TaskSchedule("s0")
                .task("t0", TestMatrix::testAdd, matrix)
                // Wrap the matrix in a 1D array because the streamOut varargs will automatically unwrap it.
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
        assertNotBackend(TornadoVMBackend.PTX);

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

        TaskSchedule ts = new TaskSchedule("s0")
                .task("t0", TestMatrix::testAddMultiple, firstMatrix, secondMatrix)
                // Wrap the matrix in a 1D array because the streamOut varargs will automatically unwrap it.
                .streamOut(new float[][][] { firstMatrix });
        ts.execute();

        for (int i = 0; i < firstMatrix.length; i++) {
            Assert.assertArrayEquals(firstMatrixSeq[i], firstMatrix[i], 0.01f);
        }
    }


}
