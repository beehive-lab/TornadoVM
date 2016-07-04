package tornado.benchmarks.saxpy;

import tornado.benchmarks.BenchmarkRunner;
import tornado.benchmarks.BenchmarkDriver;
import tornado.common.DeviceMapping;

public class Benchmark extends BenchmarkRunner {

	private int size;
        
	public  void parseArgs(String[] args) {
		if(args.length == 2){
			iterations = Integer.parseInt(args[0]);
			size = Integer.parseInt(args[1]);
	
		}else {
                    iterations = 100;
                    size = 16777216;

		}
	}

    @Override
    protected String getName() {
      return "saxpy";
    }

    @Override
    protected String getIdString() {
      return String.format("%s-%d-%d",getName(),iterations,size);
    }

    @Override
    protected String getConfigString() {
       return String.format("num elements=%d",size);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
      return new SaxpyJava(iterations,size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver(DeviceMapping device) {
         return new SaxpyTornado(iterations,size,device);
    }

	

}
