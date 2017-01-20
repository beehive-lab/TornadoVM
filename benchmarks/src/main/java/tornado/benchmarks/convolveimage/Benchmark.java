package tornado.benchmarks.convolveimage;

import tornado.benchmarks.BenchmarkRunner;
import tornado.benchmarks.BenchmarkDriver;

public class Benchmark extends BenchmarkRunner {

    private int width;
    private int height;
    private int filtersize;

    @Override
    public void parseArgs(String[] args) {

        if (args.length == 4) {
            iterations = Integer.parseInt(args[0]);
            width = Integer.parseInt(args[1]);
            height = Integer.parseInt(args[2]);
            filtersize = Integer.parseInt(args[3]);

        } else {
            iterations = 100;
            width = 1080;
            height = 1920;
            filtersize = 5;

        }
    }

    @Override
    protected String getName() {
        return "convolve-image";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d-%d-%d", getName(), iterations, width, height, filtersize);
    }

    @Override
    protected String getConfigString() {
        return String.format("width=%d, height=%d, filtersize=%d", width, height, filtersize);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new ConvolveImageJava(iterations, width, height, filtersize);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new ConvolveImageTornado(iterations, width, height, filtersize);
    }

}
