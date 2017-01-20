package tornado.benchmarks.dotvector;

import tornado.benchmarks.BenchmarkRunner;
import tornado.benchmarks.BenchmarkDriver;

public class Benchmark extends BenchmarkRunner {

    private int size;

    @Override
    public void parseArgs(String[] args) {

        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            size = Integer.parseInt(args[1]);
        } else {
            iterations = 100;
            size = 307200;

        }
    }

    @Override
    protected String getName() {
        return "dot-vector";
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
        return new DotJava(iterations, size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new DotTornado(iterations, size);
    }

}
