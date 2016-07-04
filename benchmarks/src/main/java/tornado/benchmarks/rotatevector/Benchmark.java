package tornado.benchmarks.rotatevector;

import tornado.benchmarks.BenchmarkRunner;
import tornado.benchmarks.BenchmarkDriver;
import tornado.common.DeviceMapping;

public class Benchmark extends BenchmarkRunner {

    private int size;

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
     return "rotate-vector";  
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d", getName(),iterations,size);
    }

    @Override
    protected String getConfigString() {
      return String.format("size=%d",size);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
       return new RotateJava(iterations,size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver(DeviceMapping device) {
        return new RotateTornado(iterations,size,device);
    }

}
