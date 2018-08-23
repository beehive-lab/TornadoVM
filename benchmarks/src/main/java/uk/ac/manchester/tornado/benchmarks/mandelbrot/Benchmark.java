package uk.ac.manchester.tornado.benchmarks.mandelbrot;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner;

public class Benchmark extends BenchmarkRunner {
    private int size;

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            size = 2 * Integer.parseInt(args[1]);
        } else if (args.length == 1) {
            System.out.printf("Two arguments are needed: iterations size");
        } else {
            iterations = 131;
            size = 10240;
        }
    }

    @Override
    protected String getName() {
        return "mandelbrot";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d", getName(), iterations, size);
    }

    @Override
    protected String getConfigString() {
        return String.format("size=%d", size);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new MandelbrotJava(iterations, size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new MandelbrotTornado(iterations, size);
    }
}
