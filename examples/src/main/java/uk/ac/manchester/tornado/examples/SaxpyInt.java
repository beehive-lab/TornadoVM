package uk.ac.manchester.tornado.examples;

import uk.ac.manchester.tornado.api.annotations.*;
import uk.ac.manchester.tornado.runtime.api.*;

public class SaxpyInt {

    public static void saxpy(int alpha, int[] x, int[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];

        }
    }

    public static void main(String[] args) {
        int numElements = 1024;

        int alpha = 2;

        int[] x = new int[numElements];
        int[] y = new int[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = 1;
           // y[i] = 0;
        }
        // new TaskSchedule("s0")
        // │·····························································································
        // .prebuiltTask("t0",
        // │·····························································································
        // "saxpy",
        // │·····························································································
        // "/home/admin/Tornado/tornado/null/var/opencl-codecache/device-2-0/saxpy",
        // │·····························································································
        // new Object[] { alpha, x , y},
        // │·····························································································
        // new Access[] { Access.READ, Access.READ, Access.WRITE },
        // │·····························································································
        // OpenCL.defaultDevice(),
        // │·····························································································
        // new int[] { numElements })
        // │·····························································································
        // .streamOut(y)
        // │·····························································································
        // .execute();
        // │·····························································································
        TaskSchedule s0 = new TaskSchedule("s0").streamIn(x).task("t0", SaxpyInt::saxpy, alpha, x, y).streamOut(y);

        // s0.warmup();

        s0.execute();
        for (int i = 0; i < y.length; i++) {
            // System.out.println(y[i] + "\n");
        }

    }
}

