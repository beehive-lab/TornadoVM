package uk.ac.manchester.tornado.unittests.spirv;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestFloats extends TornadoTestBase {

    @Test
    public void testFloatsCopy() {
        final int numElements = 256;
        float[] a = new float[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestKernels::testFloatCopy, a) //
                .streamOut(a) //
                .execute(); //

        assertEquals(a[0], 50.0f, 0.01f);
    }

    @Test
    public void testVectorFloatAdd() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 100);
        Arrays.fill(c, 200);
        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] + c[i];
        }

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorAddFloatCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }

    @Test
    public void testVectorFloatSub() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 200);
        Arrays.fill(c, 100);
        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] - c[i];
        }

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorSubFloatCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }

    }

    @Test
    public void testVectorFloatMul() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 100.0f);
        Arrays.fill(c, 5.0f);

        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] * c[i];
        }

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorMulFloatCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }

    @Test
    public void testVectorFloatDiv() {

        final int numElements = 256;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        Arrays.fill(b, 100.0f);
        Arrays.fill(c, 5.0f);

        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = b[i] / c[i];
        }

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorDivFloatCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals(expected[i], a[i], 0.01f);
        }
    }
}
