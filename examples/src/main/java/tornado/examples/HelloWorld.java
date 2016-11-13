package tornado.examples;

import tornado.api.Parallel;
import tornado.api.Read;
import tornado.drivers.opencl.OpenCL;
import tornado.lang.Debug;
import tornado.runtime.api.TaskGraph;

public class HelloWorld {

    public static void printHello(@Read int n) {
        for (@Parallel int i = 0; i < n; i++) {
            Debug.printf("hello\n");
        }
    }

    public static void main(String[] args) {

        /*
         * Simple hello world example which runs on 8 threads
         */
        TaskGraph graph = new TaskGraph()
                .add(HelloWorld::printHello, 8)
                .mapAllTo(OpenCL.defaultDevice());

        graph.schedule().waitOn();

    }
}
