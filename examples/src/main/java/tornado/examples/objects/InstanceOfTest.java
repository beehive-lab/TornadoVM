/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.examples.objects;

import tornado.runtime.api.TaskSchedule;

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

    public static boolean instanceOf(Object a) {
        return a instanceof Foo;
    }

    public static void main(final String[] args) {

        Foo foo = new Foo(1);

        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", InstanceOfTest::instanceOf, foo);

        s0.execute();

//        s0.getTask("t0").
        /*
         * First step is to create a reference to the method invocation This
         * involves finding the methods called and the arguments used in each
         * call.
         */
//        final CompilableTask instanceOfInvocation = TaskUtils.createTask("t0",
//                InstanceOfTest::instanceOf, foo);

        /*
         * Next we map each invocation onto a specific compute device
         */
//        instanceOfInvocation.mapTo(new OCLDeviceMapping(0, 0));

        /*
         * schedule execution of a instanceof Foo
         */
//        instanceOfInvocation.execute();

        /*
         * wait for stack to be updated and retrive return value
         */
//        instanceOfInvocation.getStack().getEvent().waitOn();
//        final long returnValue = instanceOfInvocation.getStack()
//                .getReturnValue();
//        System.out.printf("object 0x%x %s instanceof Foo\n", foo.hashCode(),
//                (returnValue == 1) ? "is" : "is not");
    }
}
