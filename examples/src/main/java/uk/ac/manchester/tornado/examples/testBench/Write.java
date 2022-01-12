package uk.ac.manchester.tornado.examples.testBench;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class Write {

    public static void write(float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = i * 2;
        }
    }

    public static void main(String[] args) {
        boolean VALIDATE_RESULTS = Boolean.parseBoolean(System.getProperty("validate", "False"));
        int BENCH_SIZE_MB = Integer.parseInt(System.getProperty("bench.size.mb", "64")) * 1000000;

        int WARMUP_ITERATIONS = Integer.parseInt(System.getProperty("warmup.iterations", "10"));

        String driverAndDevice = System.getProperty("s0.t0.device", "0:0");
        int driverNo = Integer.parseInt(driverAndDevice.split(":")[0]);
        int deviceNo = Integer.parseInt(driverAndDevice.split(":")[1]);

        int ARRAY_SIZE = BENCH_SIZE_MB / Float.BYTES;
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDriver(driverNo).getDevice(deviceNo);
        float[] y = new float[ARRAY_SIZE];

        TaskSchedule ts;
        ts = new TaskSchedule("s0")
                .task("t0", Write::write, y)
                .streamOut(y);


        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            long before = System.nanoTime();
            ts.execute();
            long after = System.nanoTime();
            System.out.println("WARMUP TIME = " + (after - before));
            device.reset();
        }

        for (int i = 0; i < 100; i++) {
            long before = System.nanoTime();
            ts.execute();
            long after = System.nanoTime();
            System.out.println("EXECUTE TIME = " + (after - before));

            if (i != 99) {
                device.reset();
            }
        }

        device.dumpEvents();

        if (VALIDATE_RESULTS) {
            System.out.println("VALIDATING");
            float[] yVal = new float[ARRAY_SIZE];

            write(yVal);

            for (int i = 0; i < yVal.length; i++) {
                float expect = yVal[i];
                float actual = y[i];

                if (actual != expect) {
                    System.out.println("Wrong result index " + i + " expect " + expect + " actual " + actual);
                    break;
                }
            }
        }

    }
}
