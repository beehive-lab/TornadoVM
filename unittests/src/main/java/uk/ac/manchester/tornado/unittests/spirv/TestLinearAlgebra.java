package uk.ac.manchester.tornado.unittests.spirv;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestLinearAlgebra extends TornadoTestBase {

    @Test
    public void vectorAdd() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 200);
        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 300);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorAddCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void vectorMul() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 5);

        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 500);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorMul, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void vectorSub() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 75);

        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 25);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorSub, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void vectorDiv() {

        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(b, 512);
        Arrays.fill(c, 2);
        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 256);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorDiv, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void square() {

        final int numElements = 32;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        int[] expectedResult = new int[numElements];

        for (int i = 0; i < a.length; i++) {
            b[i] = i;
            expectedResult[i] = i * i;
        }

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorSquare, a, b) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void saxpy() {

        final int numElements = 512;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        int[] expectedResult = new int[numElements];

        for (int i = 0; i < a.length; i++) {
            b[i] = i;
            c[i] = i;
            expectedResult[i] = 2 * i + i;
        }

        new TaskSchedule("s0") //
                .task("t0", TestKernels::saxpy, a, b, c, 2) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

}
