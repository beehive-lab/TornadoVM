package uk.ac.manchester.tornado.examples.spirv;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * How to run?
 * 
 * <p>
 * tornado --igv --debug uk.ac.manchester.tornado.examples.spirv.TestSPIRV
 * </p>
 */
public class TestSPIRV {
    public static void copyTest(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50;
        }
    }

    public static void main(String[] args) {

        final int numElements = 256;
        int[] a = new int[numElements];
        // Arrays.fill(a, 100);
        int[] b = new int[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::copyTest, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));
    }
}
