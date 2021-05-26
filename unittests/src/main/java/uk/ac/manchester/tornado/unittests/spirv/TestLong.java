package uk.ac.manchester.tornado.unittests.spirv;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestLong extends TornadoTestBase {

    @Test
    public void testLongsCopy() {
        final int numElements = 256;
        long[] a = new long[numElements];

        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = 50;
        }

        new TaskSchedule("s0") //
                .task("t0", TestKernels::testLongsCopy, a) //
                .streamOut(a) //
                .execute(); //

        assertEquals(expected, a);
    }

    @Test
    public void testLongsAdd() {
        final int numElements = 256;
        long[] a = new long[numElements];
        long[] b = new long[numElements];
        long[] c = new long[numElements];

        Arrays.fill(b, Integer.MAX_VALUE);
        Arrays.fill(c, 1);

        float[] expected = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            expected[i] = a[i] + b[i];
        }

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorSumLongCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertEquals(expected, a);

    }

}
