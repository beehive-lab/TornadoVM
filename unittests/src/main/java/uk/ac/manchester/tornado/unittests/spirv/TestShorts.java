package uk.ac.manchester.tornado.unittests.spirv;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Run:
 * 
 * <code>
 *     tornado-test.py -V uk.ac.manchester.tornado.unittests.spirv.Vectors
 * </code>
 */
public class TestShorts extends TornadoTestBase {

    @Test
    public void testShortAdd() {
        final int numElements = 256;
        short[] a = new short[numElements];
        short[] b = new short[numElements];
        short[] c = new short[numElements];

        Arrays.fill(b, (short) 1);
        Arrays.fill(c, (short) 3);

        short[] expectedResult = new short[numElements];
        Arrays.fill(expectedResult, (short) 4);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::vectorSumShortCompute, a, b, c) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

}
