package uk.ac.manchester.tornado.benchmarks.juliaset;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner;

public class Benchmark extends BenchmarkRunner {

    private int size;

    @Override
    protected String getName() {
        return "juliaset";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d-%d", getName(), iterations, size, size);
    }

    @Override
    protected String getConfigString() {
        return String.format("size=%d, size=%d", size, size);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new JuliaSetJava(iterations, size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new JuliaSetTornado(iterations, size);
    }

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            size = Integer.parseInt(args[1]);
        } else {
            iterations = 50;
            size = 8192;
        }
    }
}
