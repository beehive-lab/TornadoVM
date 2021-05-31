package uk.ac.manchester.tornado.unittests.spirv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Run:
 * 
 * <code>
 *     tornado-test.py -V -f uk.ac.manchester.tornado.unittests.spirv.TestIf
 * </code>
 */
public class TestIf extends TornadoTestBase {

    @Test
    public void test01() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] expectedResult = new int[numElements];

        Arrays.fill(a, 0);
        Arrays.fill(expectedResult, 50);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::testIfInt, a) //
                .streamOut(a) //
                .execute(); //

        assertEquals(50, a[0]);
    }

    @Test
    public void test02() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] expectedResult = new int[numElements];

        Arrays.fill(a, 0);
        Arrays.fill(expectedResult, 50);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::testIfInt2, a) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

    @Test
    public void test03() {
        final int numElements = 256;
        int[] a = new int[numElements];
        int[] expectedResult = new int[numElements];

        Arrays.fill(a, -1);
        Arrays.fill(expectedResult, 100);

        new TaskSchedule("s0") //
                .task("t0", TestKernels::testIfInt3, a) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(expectedResult, a);
    }

}
