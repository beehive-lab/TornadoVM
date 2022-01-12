package uk.ac.manchester.tornado.examples.testBench;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

import java.util.Arrays;

public class Nbody {

    private static void nBody(int numBodies, float[] refPos, float[] refVel) {
        float DELTA = 0.005f;
        float ESP_SQR = 500.0f;

        for (@Parallel int i = 0; i < numBodies; i++) {
            int body = 4 * i;

            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < numBodies; j++) {
                float[] r = new float[3];
                int index = 4 * j;

                float distSqr = 0.0f;
                for (int k = 0; k < 3; k++) {
                    r[k] = refPos[index + k] - refPos[body + k];
                    distSqr += r[k] * r[k];
                }

                float invDist = (float) (1.0f / Math.sqrt(distSqr + ESP_SQR));

                float invDistCube = invDist * invDist * invDist;
                float s = refPos[index + 3] * invDistCube;

                for (int k = 0; k < 3; k++) {
                    acc[k] += s * r[k];
                }
            }
            for (int k = 0; k < 3; k++) {
                refPos[body + k] += refVel[body + k] * DELTA + 0.5f * acc[k] * DELTA * DELTA;
                refVel[body + k] += acc[k] * DELTA;
            }
        }
    }

    public static void main(String[] args) {
        boolean VALIDATE_RESULTS = Boolean.parseBoolean(System.getProperty("validate", "False"));
        int BENCH_SIZE_MB = Integer.parseInt(System.getProperty("bench.size.mb", "64")) * 1000000;

        int WARMUP_ITERATIONS = Integer.parseInt(System.getProperty("warmup.iterations", "10"));

        String driverAndDevice = System.getProperty("s0.t0.device", "0:0");
        int driverNo = Integer.parseInt(driverAndDevice.split(":")[0]);
        int deviceNo = Integer.parseInt(driverAndDevice.split(":")[1]);

        int ARRAY_SIZE = BENCH_SIZE_MB / Float.BYTES / 2;
        int NUM_BODIES = ARRAY_SIZE / 4;

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDriver(driverNo).getDevice(deviceNo);
        float[] posTor = new float[ARRAY_SIZE];
        float[] velTor = new float[ARRAY_SIZE];

        for (int i = 0; i < posTor.length; i++) {
            posTor[i] = (float) Math.random();
        }
        Arrays.fill(velTor, 0.0f);

        TaskSchedule ts;
        ts = new TaskSchedule("s0")
                .streamIn(posTor, velTor)
                .task("t0", Nbody::nBody, NUM_BODIES, posTor, velTor)
                .streamOut(posTor, velTor);


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
            float[] posSeq = new float[ARRAY_SIZE];
            float[] velSeq = new float[ARRAY_SIZE];

            for (int i = 0; i < posSeq.length; i++) {
                posSeq[i] = (float) Math.random();
            }
            Arrays.fill(velSeq, 0.0f);

            nBody(NUM_BODIES, posSeq, velSeq);

            for (int i = 0; i < velSeq.length; i++) {
                float expect = velSeq[i];
                float actual = velTor[i];

                if (actual != expect) {
                    System.out.println("Wrong result index " + i + " expect " + expect + " actual " + actual);
                    break;
                }

                expect = posSeq[i];
                actual = posTor[i];
                if (actual != expect) {
                    System.out.println("Wrong result index " + i + " expect " + expect + " actual " + actual);
                    break;
                }
            }
        }

    }
}
