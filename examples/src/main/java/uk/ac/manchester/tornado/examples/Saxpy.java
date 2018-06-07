package uk.ac.manchester.tornado.examples;

import org.omg.Messaging.*;
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.common.enums.*;
import uk.ac.manchester.tornado.drivers.opencl.*;
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
            x[i] = 1;
            y[i] = 0;
        }
//        new TaskSchedule("s0")
//                .prebuiltTask("t0",
//                        "saxpy",
//                        "/home/admin/Tornado/tornado/null/var/opencl-codecache/device-2-0/saxpy",
//                        new Object[] { alpha, x , y},
//                        new Access[] { Access.READ, Access.READ, Access.WRITE },
//                        OpenCL.defaultDevice(),
//                        new int[] { numElements })
//                .streamOut(y)
//                .execute();
       TaskSchedule s0 = new TaskSchedule("s0")
               .streamIn(x,y)
                .task("t0", Saxpy::saxpy, alpha, x,y)
                .streamOut(y);

        //s0.warmup();

        s0.execute();
        for (int i=0; i < y.length; i++){
          //  System.out.println(y[i] + "\n");
        }

    }

}




