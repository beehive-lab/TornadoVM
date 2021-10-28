package uk.ac.manchester.tornado.benchmarks.juliaset;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

public class JuliaSetJava extends BenchmarkDriver {

    private final int size;
    private final int iterations;

    private static float[] hue;
    private static float[] brightness;

    /**
     * It generates a square image with the fractal.
     */
    public JuliaSetJava(int iterations, int size) {
        super(iterations);
        this.iterations = iterations;
        this.size = size;
    }

    @Override
    public void setUp() {
        hue = new float[size * size];
        brightness = new float[size * size];
    }

    @Override
    public void tearDown() {
        hue = null;
        brightness = null;
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        GraphicsKernels.juliaSetTornado(size, hue, brightness);
    }
}
