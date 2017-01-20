package tornado.examples;

import tornado.lang.Debug;
import tornado.runtime.api.TaskSchedule;

public class IfTest {

    public static void printHello(int[] a) {
        Debug.printf("hello: %d\n", a[0]);
        if (a[0] > 1) {
            Debug.printf("hello\n");
        }
    }

    public static void main(String[] args) {

        /*
         * Simple hello world example which runs on 8 threads
         */
        int[] a = new int[]{8};
        new TaskSchedule("s0")
                .task("t0", IfTest::printHello, a)
                .execute();

    }
}
