package uk.ac.manchester.tornado.unittests.spirv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * tornado-test.py -V uk.ac.manchester.tornado.unittests.spirv.TestIntegers
 */
public class TestIntegers extends TornadoTestBase {

    @Test
    public void test01() {
        final int numElements = 256;
        int[] a = new int[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestKernels::copyTestZero, a) //
                .streamOut(a) //
                .execute(); //

        assertEquals(a[0], 50);
    }

    @Test
    public void test02() {
        final int numElements = 512;
        int[] a = new int[numElements];

        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 50);

        new TaskSchedule("s1") //
                .streamIn(a) //
                .task("t1", TestKernels::copyTest, a) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void test03() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(b, 100);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::copyTest2, a, b) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(b, a);
    }

    @Test
    public void test04() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(b, 100);
        int[] expectedResult = new int[numElements];
        Arrays.fill(expectedResult, 150);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::compute, a, b) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

}
