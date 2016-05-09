package tornado.benchmarks.rotatevector;

import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.drivers.opencl.runtime.OCLRuntime;
import tornado.runtime.TornadoRuntime;

public class Benchmark {

	private static final String	BENCHMARK_NAME	= "rotate-vector";

	public static void run(int iterations, int numElements) {
		String id = String.format("%s-%d-%d", BENCHMARK_NAME, iterations, numElements);

		System.out.printf("benchmark=%s, iterations=%d, num elements=%d\n", id, iterations,
				numElements);

		final RotateJava referenceTest = new RotateJava(iterations, numElements);
		referenceTest.benchmark();

		System.out.printf("bm=%-15s, id=%-20s, %s\n", id, "java-reference",
				referenceTest.getSummary());

		final double refElapsed = referenceTest.getElapsed();

		final RotateTornadoDummy tornadoOverhead = new RotateTornadoDummy(iterations, numElements);
		tornadoOverhead.benchmark();
		System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f\n", id, "tornado-dummy",
				tornadoOverhead.getSummary(), refElapsed / tornadoOverhead.getElapsed());

		final OCLRuntime oclRuntime = (OCLRuntime) TornadoRuntime.runtime;
		for (int platformIndex = 0; platformIndex < oclRuntime.getNumPlatforms(); platformIndex++) {
			for (int deviceIndex = 0; deviceIndex < oclRuntime.getNumDevices(platformIndex); deviceIndex++) {
				final OCLDeviceMapping device = new OCLDeviceMapping(platformIndex, deviceIndex);

				final RotateTornado deviceTest = new RotateTornado(iterations, numElements, device);

				deviceTest.benchmark();

				System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f, overhead=%.4f\n", id,
						"opencl-device-" + platformIndex + "-" + deviceIndex,
						deviceTest.getSummary(), refElapsed / deviceTest.getElapsed(),
						deviceTest.getOverhead());
			}
		}
		
		TornadoRuntime.resetDevices();
	}

	public static void main(String[] args) {
		if(args.length == 2){
			final int iterations = Integer.parseInt(args[0]);
			final int size = Integer.parseInt(args[1]);
			run(iterations, size);
		}else {
			run(100, 307200);
		}	
	}

}
