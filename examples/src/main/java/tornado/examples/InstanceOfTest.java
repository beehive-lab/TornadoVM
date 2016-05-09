package tornado.examples;

import tornado.api.Read;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.ExecutableTask;

public class InstanceOfTest {

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

    public static boolean instanceOf(@Read Object a) {
        return a instanceof Foo;
    }

    public static void main(final String[] args) {

        Foo foo = new Foo(1);

        /*
         * First step is to create a reference to the method invocation
         * This involves finding the methods called and the arguments used
         * in each call.
         */
        final ExecutableTask<?> instanceOfInvocation = TaskUtils.createTask(
                InstanceOfTest::instanceOf, foo);

        /*
         * Next we map each invocation onto a specific compute device
         */
        instanceOfInvocation.mapTo(new OCLDeviceMapping(0, 0));

        /*
         * schedule execution of a instanceof Foo
         */
        instanceOfInvocation.execute();

        /*
         * wait for stack to be updated and retrive return value
         */
        instanceOfInvocation.getStack().getEvent().waitOn();
        final long returnValue = instanceOfInvocation.getStack()
                .getReturnValue();

        System.out.printf("object 0x%x %s instanceof Foo\n", foo.hashCode(),
                (returnValue == 1) ? "is" : "is not");

    }
}
