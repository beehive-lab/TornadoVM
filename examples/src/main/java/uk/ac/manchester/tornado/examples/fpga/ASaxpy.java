package uk.ac.manchester.tornado.examples.fpga;

import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.runtime.api.*;

public class ASaxpy {
//    private int numElements;   // <------------- CHANGE
//
//    public void parseArgs(String[] args) {
//        if (args.length == 1) {
//            numElements = Integer.parseInt(args[0]);
//
//        } else {
//            numElements = 1024;
//
//        }
//    }

    public static void saxpy(float alpha, float[] x, float[] y, float[] b) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i] + b[i];
        }
    }

    public static void main(String[] args) {
        int numElements = Integer.parseInt(args[0]);


        float alpha = 2f;

        float[] x = new float[numElements];
        float[] y = new float[numElements];
        float[] b = new float[numElements];
        float[] result = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = 450;
            y[i] = 0;
            b[i] = 20;
        }

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", ASaxpy::saxpy, alpha, x, y,b).streamOut(y);

        for (int idx = 0; idx < 10; idx++) {
            s0.execute();

            long start = System.nanoTime();
            saxpy(alpha,x, result,b);
            long stop = System.nanoTime();

            long seqTime = stop -start;
            //System.out.println("Sequential time: " + seqTime + "\n");

            System.out.println("Checking result");
            boolean wrongResult = false;
            for (int i = 0; i < y.length; i++) {
                if (Math.abs(y[i] - (alpha * x[i] +b[i])) > 0.01) {
                    wrongResult = true;
                    break;
                }
            }
            if (!wrongResult) {
                System.out.println("Test success");
            } else {
                System.out.println("Result is wrong");
            }

        }
    }

}
