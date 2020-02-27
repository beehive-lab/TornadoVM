package uk.ac.manchester.tornado.examples.arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Arrays;

//TODO: These examples seem pretty simple and dumb I just used these for testing
// and therefore could be deleted afterwards
public class ArrayInitInt {

    public static void init(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 3;
        }
    }

    public static void main(String[] args) {
        final int numElements = 8;
        int[] test = new int[numElements];

        Arrays.fill(test, 0);

        System.out.println("Before: " + Arrays.toString(test));

        new TaskSchedule("s0")
                .task("t0", ArrayInitInt::init, test)
                .streamOut(test)
                .execute();

        System.out.println("After: " + Arrays.toString(test));
    }
}
