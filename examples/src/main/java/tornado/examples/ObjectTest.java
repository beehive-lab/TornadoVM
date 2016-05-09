package tornado.examples;

import tornado.api.Read;
import tornado.api.Write;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.ExecutableTask;

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
         * First step is to create a reference to the method invocation
         * This involves finding the methods called and the arguments used
         * in each call.
         */
        final ExecutableTask<?> addInvocation = TaskUtils.createTask(
                ObjectTest::add, a, b, c);

        /*
         * Next we map each invocation onto a specific compute device
         */
        addInvocation.mapTo(new OCLDeviceMapping(0, 0));

        /*
         * Calculate a (3) + b (2) = c (5)
         */
        addInvocation.execute();

        /*
         * Check to make sure result is correct
         */
        if (c.getValue() != 3)
            System.out.printf("Invalid result: c = %d (expected 3)\n",
                    c.getValue());

    }
}
