package uk.ac.manchester.tornado.benchmarks.mandelbrot;

import uk.ac.manchester.tornado.benchmarks.*;

public class MandelbrotJava extends BenchmarkDriver {
    int size;
    short[] result;

    public MandelbrotJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        result = new short[size * size];
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public void code() {
        ComputeKernels.mandelbrot(size, result);

    }
}
