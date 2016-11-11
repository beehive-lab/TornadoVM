package tornado.examples;

import tornado.api.Read;
import tornado.api.Write;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.TaskGraph;
import tornado.runtime.api.TaskUtils;

public class ObjectTest {

    public static class Foo {

        int value;

        public Foo(int v) {
            value = v;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int v) {
            value = v;
        }
    }

    public static void add(@Read Foo a, @Read Foo b, @Write Foo c) {
        c.setValue(a.getValue() + b.getValue());
    }

    public static void main(final String[] args) {

        Foo a = new Foo(1);
        Foo b = new Foo(2);
        Foo c = new Foo(0);

        /*
         * First step is to create a reference to the method invocation This
         * involves finding the methods called and the arguments used in each
         * call.
         */
        final CompilableTask addInvocation = TaskUtils.createTask(
                ObjectTest::add, a, b, c);

        /*
         * Next we insert the task into a task graph and specify that we want
         * the value of c updated on completion.
         */
        final TaskGraph graph = new TaskGraph()
                .add(addInvocation)
                .streamOut(c);

        /*
         * Next we map each invocation onto a specific compute device
         */
        graph.mapAllTo(OpenCL.defaultDevice());

        /*
         * Calculate a (3) + b (2) = c (5)
         */
        graph.schedule().waitOn();


        /*
         * Check to make sure result is correct
         */
        if (c.getValue() != 3) {
            System.out.printf("Invalid result: c = %d (expected 3)\n",
                    c.getValue());
        }

    }
}
