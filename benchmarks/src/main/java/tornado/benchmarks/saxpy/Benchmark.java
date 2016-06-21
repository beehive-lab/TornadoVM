package tornado.benchmarks.saxpy;

import tornado.benchmarks.saxpy.SaxpyJava;
import tornado.benchmarks.saxpy.SaxpyTornado;
import tornado.benchmarks.saxpy.SaxpyTornadoDummy;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.drivers.opencl.runtime.OCLDriver;
import tornado.runtime.TornadoRuntime;

public class Benchmark {

	private static final String	BENCHMARK_NAME	= "saxpy";

	public static void main(String[] args) {
		if(args.length == 2){
			final int iterations = Integer.parseInt(args[0]);
			final int size = Integer.parseInt(args[1]);
			run(iterations, size);
		}else {
			run(100, 16777216);
		}
	}

	public static void run(int iterations, int numElements) {
		System.out.printf("benchmark=%s, iterations=%d, num elements=%d\n", BENCHMARK_NAME,
				iterations, numElements);

		final SaxpyJava referenceTest = new SaxpyJava(iterations, numElements);
		referenceTest.benchmark();

		System.out.printf("bm=%-15s, id=%-20s, %s\n", String.format("%s-%d-%d",BENCHMARK_NAME,iterations,numElements), "java-reference",
				referenceTest.getSummary());

		final double refElapsed = referenceTest.getElapsed();

		final SaxpyTornadoDummy tornadoOverhead = new SaxpyTornadoDummy(iterations, numElements);
		tornadoOverhead.benchmark();
		System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f\n", String.format("%s-%d-%d",BENCHMARK_NAME,iterations,numElements),
				"tornado-dummy", tornadoOverhead.getSummary(),
				refElapsed / tornadoOverhead.getElapsed());

		final OCLDriver oclRuntime = (OCLDriver) TornadoRuntime.runtime;
		for (int platformIndex = 0; platformIndex < oclRuntime.getNumPlatforms(); platformIndex++) {
			for (int deviceIndex = 0; deviceIndex < oclRuntime.getNumDevices(platformIndex); deviceIndex++) {
				final OCLDeviceMapping device = new OCLDeviceMapping(platformIndex, deviceIndex);

				final SaxpyTornado deviceTest = new SaxpyTornado(iterations, numElements, device);

				deviceTest.benchmark();

				System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f, overhead=%.4f\n",
						String.format("%s-%d-%d",BENCHMARK_NAME,iterations,numElements), "opencl-device-" + platformIndex + "-" + deviceIndex,
						deviceTest.getSummary(), refElapsed / deviceTest.getElapsed(),
						deviceTest.getOverhead());
			}
		}
		
		TornadoRuntime.resetDevices();
	}

}
