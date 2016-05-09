package tornado.benchmarks.sgemm;


import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.drivers.opencl.runtime.OCLRuntime;
import tornado.runtime.TornadoRuntime;

public class Benchmark {

	private static final String	BENCHMARK_NAME	= "sgemm";

	public static void main(String[] args) {
		if(args.length == 3){
			final int iterations = Integer.parseInt(args[0]);
			final int width = Integer.parseInt(args[1]);
			final int height = Integer.parseInt(args[2]);
			run(iterations, width, height);
		}else {
			run(20, 512,512);
		}	
	}

	public static void run(int iterations, int m, int n) {
		System.out.printf("benchmark=%s, iterations=%d, num elements={%d,%d}\n", BENCHMARK_NAME,
				iterations, m,n);

		final SgemmJava referenceTest = new SgemmJava(iterations, m,n);
		referenceTest.benchmark();

		System.out.printf("bm=%-15s, id=%-20s, %s\n", String.format("%s-%d-%d-%d",BENCHMARK_NAME,iterations,m,n), "java-reference",
				referenceTest.getSummary());

		final double refElapsed = referenceTest.getElapsed();

		final SgemmTornadoDummy tornadoOverhead = new SgemmTornadoDummy(iterations, m,n);
		tornadoOverhead.benchmark();
		System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f\n", String.format("%s-%d-%d-%d",BENCHMARK_NAME,iterations,m,n),
				"tornado-dummy", tornadoOverhead.getSummary(),
				refElapsed / tornadoOverhead.getElapsed());

		final OCLRuntime oclRuntime = (OCLRuntime) TornadoRuntime.runtime;
		for (int platformIndex = 0; platformIndex < oclRuntime.getNumPlatforms(); platformIndex++) {
			for (int deviceIndex = 0; deviceIndex < oclRuntime.getNumDevices(platformIndex); deviceIndex++) {
				final OCLDeviceMapping device = new OCLDeviceMapping(platformIndex, deviceIndex);

				final SgemmTornado deviceTest = new SgemmTornado(iterations, m,n, device);

				deviceTest.benchmark();

				System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f, overhead=%.4f\n",
						String.format("%s-%d-%d-%d",BENCHMARK_NAME,iterations,m,n), "opencl-device-" + platformIndex + "-" + deviceIndex,
						deviceTest.getSummary(), refElapsed / deviceTest.getElapsed(),
						deviceTest.getOverhead());
			}
		}
		
		TornadoRuntime.resetDevices();
	}

}
