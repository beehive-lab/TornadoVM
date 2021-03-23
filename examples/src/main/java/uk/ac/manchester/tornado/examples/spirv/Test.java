package uk.ac.manchester.tornado.examples.spirv;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * How to run?
 * 
 * <p>
 * tornado --debug uk.ac.manchester.tornado.examples.spirv.Test
 * </p>
 */
public class Test {
    public static void add(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 10;
        }
    }

    public static void main(String[] args) {

        final int numElements = 8;
        int[] a = new int[numElements];

        new TaskSchedule("s0") //
                .task("t0", Test::add, a) //
                .streamOut(a) //
                .execute(); //

        System.out.println("a: " + Arrays.toString(a));
    }
}
