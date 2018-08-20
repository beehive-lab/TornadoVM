package uk.ac.manchester.tornado.examples;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class Saxpy {

    public static void saxpy(float alpha, float[] x, float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];
        }
    }

    public static void main(String[] args) {
        int numElements = 10240;

        float alpha = 2f;

        float[] x = new float[numElements];
        float[] y = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = 450;
            y[i] = 0;
        }

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", Saxpy::saxpy, alpha, x, y).streamOut(y);

        s0.execute();

        System.out.println("Checking result");
        boolean wrongResult = false;
        for (int i = 0; i < y.length; i++) {
            if (Math.abs(y[i] - (alpha * x[i])) > 0.01) {
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
