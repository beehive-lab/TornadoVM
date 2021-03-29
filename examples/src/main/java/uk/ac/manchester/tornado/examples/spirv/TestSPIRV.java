package uk.ac.manchester.tornado.examples.spirv;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.exceptions.Debug;

/**
 * How to run?
 * 
 * <p>
 * tornado --igv --debug uk.ac.manchester.tornado.examples.spirv.TestSPIRV
 * </p>
 */
public class TestSPIRV {
    public static void copyTest(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            Debug.printf("Hello: %d\n", i);
            b[i] = a[i];
        }
    }

    public static void main(String[] args) {

        final int numElements = 256;
        int[] a = new int[numElements];
        Arrays.fill(a, 100);
        int[] b = new int[numElements];

        new TaskSchedule("s0") //
                .task("t0", TestSPIRV::copyTest, a, b) //
                .streamOut(b) //
                .execute(); //

        System.out.println("b: " + Arrays.toString(b));
    }
}
