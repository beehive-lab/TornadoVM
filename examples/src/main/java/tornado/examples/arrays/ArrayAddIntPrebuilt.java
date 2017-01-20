package tornado.examples.arrays;

import java.util.Arrays;
import tornado.api.Parallel;
import tornado.common.enums.Access;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.PrebuiltTask;
import tornado.runtime.api.TaskSchedule;
import tornado.runtime.api.TaskUtils;

public class ArrayAddIntPrebuilt {

    public static void add(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {

        final int numElements = 8;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        final PrebuiltTask add = TaskUtils.createTask("t0",
                "add",
                "opencl/add.cl",
                new Object[]{a, b, c},
                new Access[]{Access.READ, Access.READ, Access.WRITE},
                OpenCL.defaultDevice(),
                new int[]{numElements});

        new TaskSchedule("s0")
                .task(add)
                .streamOut(c)
                .execute();

        System.out.println("a: " + Arrays.toString(a));
        System.out.println("b: " + Arrays.toString(b));
        System.out.println("c: " + Arrays.toString(c));
    }

}
