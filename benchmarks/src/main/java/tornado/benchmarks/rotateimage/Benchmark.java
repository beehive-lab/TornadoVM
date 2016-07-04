package tornado.benchmarks.rotateimage;

import tornado.benchmarks.BenchmarkRunner;
import tornado.benchmarks.BenchmarkDriver;
import tornado.common.DeviceMapping;

public class Benchmark extends BenchmarkRunner{

	private int width;
        private int height;

        @Override
	public  void parseArgs(String[] args) {
		if(args.length == 3){
			iterations = Integer.parseInt(args[0]);
			width = Integer.parseInt(args[1]);
			height = Integer.parseInt(args[1]);
	
		}else {
                    iterations = 100;
                    width = 640;
                    height = 480;
		}
		
	}

    @Override
    protected String getName() {
       return "rotate-image";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d",iterations,width,height);
    }

    @Override
    protected String getConfigString() {
       return String.format("width=%d, height=%d", width,height);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
      return new RotateJava(iterations,width, height);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver(DeviceMapping device) {
     return new RotateTornado(iterations,width,height,device);   
    }

}
