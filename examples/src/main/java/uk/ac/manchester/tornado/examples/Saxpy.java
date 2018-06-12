package uk.ac.manchester.tornado.examples;


import uk.ac.manchester.tornado.api.*;

import uk.ac.manchester.tornado.examples.vectors.*;
import uk.ac.manchester.tornado.lang.*;
import uk.ac.manchester.tornado.runtime.*;
import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;
import uk.ac.manchester.tornado.runtime.api.*;

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

       TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", Saxpy::saxpy, alpha, x,y)
                .streamOut(y);


        s0.execute();
        for (int i=0; i < y.length; i++) {
            //System.out.print(Arrays.toString(y));
        }
    }

}




