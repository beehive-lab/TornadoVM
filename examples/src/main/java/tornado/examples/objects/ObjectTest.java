package tornado.examples.objects;

import tornado.runtime.api.TaskSchedule;

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

    public static void add(Foo a, Foo b, Foo c) {
        c.setValue(a.getValue() + b.getValue());
    }

    public static void main(final String[] args) {

        Foo a = new Foo(1);
        Foo b = new Foo(2);
        Foo c = new Foo(0);

        /*
         * Next we insert the task into a task graph and specify that we want
         * the value of c updated on completion.
         */
        final TaskSchedule schedule = new TaskSchedule("s0")
                .task("t0", ObjectTest::add, a, b, c)
                .streamOut(c);

        /*
         * Calculate a (3) + b (2) = c (5)
         */
        schedule.execute();


        /*
         * Check to make sure result is correct
         */
        if (c.getValue() != 3) {
            System.out.printf("Invalid result: c = %d (expected 3)\n",
                    c.getValue());
        }

    }
}
