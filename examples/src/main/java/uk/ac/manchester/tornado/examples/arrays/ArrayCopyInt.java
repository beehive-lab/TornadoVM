package uk.ac.manchester.tornado.examples.arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Arrays;

//TODO: These examples seem pretty simple and dumb I just used these for testing
// and therefore could be deleted afterwards
public class ArrayCopyInt {
    public static void copy(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i];
        }
    }

    public static void main(String[] args) {
        final int numElements = 8;
        int[] test = new int[numElements];
        int[] dest = new int[numElements];

        Arrays.fill(test, 3);
        Arrays.fill(dest, 0);

        System.out.println("Before: " + Arrays.toString(dest));

        new TaskSchedule("s0")
                .task("t0", ArrayCopyInt::copy, dest, test)
                .streamOut(dest)
                .execute();

        System.out.println("After: " + Arrays.toString(dest));
    }
}
