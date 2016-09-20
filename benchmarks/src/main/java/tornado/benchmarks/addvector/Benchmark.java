package tornado.benchmarks.addvector;

import tornado.benchmarks.BenchmarkRunner;
import tornado.benchmarks.BenchmarkDriver;
import tornado.common.DeviceMapping;

public class Benchmark extends BenchmarkRunner {

    private int size;

    @Override
    protected String getName() {
        return "add-vector";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d",getName(),iterations,size);
    }

    @Override
    protected String getConfigString() {
     return String.format("size=%d", size);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new AddJava(iterations, size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver(DeviceMapping device) {
        return new AddTornado(iterations, size, device);
    }

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            size = Integer.parseInt(args[1]);

        } else {
            iterations = 100;
            size = 76800; //4194304
        }
    }

}
