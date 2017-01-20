package tornado.benchmarks.sgemm;

import tornado.benchmarks.BenchmarkRunner;
import tornado.benchmarks.BenchmarkDriver;

public class Benchmark extends BenchmarkRunner {

    private int width;
    private int height;

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 3) {
            iterations = Integer.parseInt(args[0]);
            width = Integer.parseInt(args[1]);
            height = Integer.parseInt(args[2]);

        } else {
            iterations = 20;
            width = 512;
            height = 512;
        }
    }

    @Override
    protected String getName() {
        return "sgemm";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d-%d", getName(), iterations, width, height);
    }

    @Override
    protected String getConfigString() {
        return String.format("width=%d, height=%d", width, height);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new SgemmJava(iterations, width, height);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new SgemmTornado(iterations, width, height);
    }

}
