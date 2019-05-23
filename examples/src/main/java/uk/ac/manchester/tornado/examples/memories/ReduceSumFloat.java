package uk.ac.manchester.tornado.examples.memories;

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class ReduceSumFloat {
    // private static int size = 65536;

    public static float[] allocResultArray(int numGroups) {
        final TornadoDriver device = TornadoRuntime.getTornadoRuntime().getDriver(0);

        TornadoDeviceType deviceType = device.getTypeDefaultDevice();
        float[] result = null;
        switch (deviceType) {
            case CPU:
                result = new float[Runtime.getRuntime().availableProcessors()];
                break;
            case GPU:
            case ACCELERATOR:
                result = new float[numGroups];
                break;
            default:
                break;
        }
        return result;
    }

    public static void reductionAddFloats(float[] input, @Reduce float[] result) {
        result[0] = 0.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    public static void main(String[] args) {
        int size = 65536;
        String kernelLocation = null;
        boolean preBuilt = false;
        int numGroups = 1;

        if (args.length == 1) {
            kernelLocation = args[0];
            preBuilt = true;
            size = 65536;
        }

        if (args.length == 2) {
            kernelLocation = args[0];
            preBuilt = true;
            size = Integer.parseInt(args[1]);
        }

        float[] input = new float[size];
        if (size > 256) {
            numGroups = size / 256;
        }
        float[] result = allocResultArray(numGroups);

        Random r = new Random();
        IntStream.range(0, size).sequential().forEach(i -> {
            // input[i] = r.nextFloat();
            input[i] = 1;
        });

        TaskSchedule task = new TaskSchedule("s0");

        if (preBuilt) {
        //@formatter:off
            task.prebuiltTask("t0", "reductionAddFloats", kernelLocation,
                    new Object[] { input, result}, new Access[] { Access.READ, Access.READ_WRITE},
                    TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0),
                    new int[] {size})
                    .streamOut(result);
        //formatter:on
        } else {
        //@formatter:off
                task.streamIn(input)
                .task("t0", ReduceSumFloat::reductionAddFloats, input, result)
                .streamOut(result);
        //@formatter:on
        }

        long start = System.nanoTime();

        for (int it = 0; it < 5; it++) {
            task.execute();
        }

        long end = System.nanoTime();

        // System.out.println(Arrays.toString(result));

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        float[] sequential = new float[1];

        long startSequential = System.nanoTime();
        reductionAddFloats(input, sequential);
        long endSequential = System.nanoTime();

        System.out.println("Speedup: " + (double) (endSequential - startSequential) / (double) (end - start));

        System.out.println(sequential[0] + " vs " + result[0]);

    }
}
