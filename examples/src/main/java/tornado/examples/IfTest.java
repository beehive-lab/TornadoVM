package tornado.examples;

import tornado.drivers.opencl.OpenCL;
import tornado.lang.Debug;
import tornado.runtime.api.TaskGraph;

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
        TaskGraph graph = new TaskGraph()
                .add(IfTest::printHello, a)
                .mapAllTo(OpenCL.defaultDevice());

        graph.schedule().waitOn();

    }
}
