package tornado.benchmarks.bandwidth;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.BenchmarkRunner;

public class Benchmark extends BenchmarkRunner {

    private int size;

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            size = Integer.parseInt(args[1]);
        } else {
            iterations = 100;
            size = 16777216;
        }
    }

    @Override
    protected String getName() {
        return "bandwidth";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d", getName(), iterations, size);
    }

    @Override
    protected String getConfigString() {
        return String.format("num elements=%d", size);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new BandwidthJava(iterations, size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new BandwidthTornado(iterations, size);
    }

}
