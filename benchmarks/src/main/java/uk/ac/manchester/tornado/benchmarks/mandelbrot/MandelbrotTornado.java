package uk.ac.manchester.tornado.benchmarks.mandelbrot;

import uk.ac.manchester.tornado.benchmarks.*;
import uk.ac.manchester.tornado.runtime.api.*;

public class MandelbrotTornado extends BenchmarkDriver {
    int size;
    short[] output;
    TaskSchedule graph;

    public MandelbrotTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        output = new short[size * size];

        graph = new TaskSchedule("benchmark");
        graph.task("t0", ComputeKernels::mandelbrot, size, output);
        graph.streamOut(output);
        graph.warmup();

    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        output = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public boolean validate() {
        boolean val = true;
        short[] result = new short[size * size];

        graph.syncObject(output);
        graph.clearProfiles();

        ComputeKernels.mandelbrot(size, result);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (Math.abs(output[i * size + j] - result[i * size + j]) > 0.01) {
                    val = false;
                    break;
                }
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

    @Override
    public void code() {
        graph.execute();
    }
}
