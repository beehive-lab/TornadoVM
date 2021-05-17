package uk.ac.manchester.tornado.unittests.spirv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Run:
 * 
 * <code>
 *     tornado-test.py -V uk.ac.manchester.tornado.unittests.spirv.Vectors
 * </code>
 */
public class Vectors extends TornadoTestBase {

    private static void copyTest(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50;
        }
    }

    private static void copyTestZero(int[] a) {
        a[0] = 50;
    }

    private static void vectorAddCompute(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + c[i];
        }
    }

    @Test
    public void test01() {
        final int numElements = 256;
        int[] a = new int[numElements];

        new TaskSchedule("s0") //
                .task("t0", Vectors::copyTestZero, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));
        assertEquals(a[0], 50);
    }

    @Test
    public void test02() {
        final int numElements = 256;
        int[] a = new int[numElements];

        int[] expectedResult = new int[numElements];

        Arrays.fill(expectedResult, 50);

        new TaskSchedule("s0") //
                .task("t0", Vectors::copyTest, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));
        assertArrayEquals(expectedResult, a);
    }

}
