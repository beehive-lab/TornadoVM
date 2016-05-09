package tornado.benchmarks.rotateimage;

import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.drivers.opencl.runtime.OCLRuntime;
import tornado.runtime.TornadoRuntime;

public class Benchmark {

	private static final String	BENCHMARK_NAME	= "rotate-image";

	public static void run(int iterations, int numElementsX, int numElementsY) {
		String id = String.format("%s-%d-%d-%d", BENCHMARK_NAME, iterations, numElementsX,
				numElementsY);

		System.out.printf("benchmark=%s, iterations=%d, num elements={%d, %d}\n", id, iterations,
				numElementsX, numElementsY);

		final RotateJava referenceTest = new RotateJava(iterations, numElementsX, numElementsY);
		referenceTest.benchmark();

		System.out.printf("bm=%-15s, id=%-20s, %s\n", id, "java-reference",
				referenceTest.getSummary());

		final double refElapsed = referenceTest.getElapsed();

		final RotateTornadoDummy tornadoOverhead = new RotateTornadoDummy(iterations, numElementsX,
				numElementsY);
		tornadoOverhead.benchmark();
		System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f\n", id, "tornado-dummy",
				tornadoOverhead.getSummary(), refElapsed / tornadoOverhead.getElapsed());

		final OCLRuntime oclRuntime = (OCLRuntime) TornadoRuntime.runtime;
		for (int platformIndex = 0; platformIndex < oclRuntime.getNumPlatforms(); platformIndex++) {
			for (int deviceIndex = 0; deviceIndex < oclRuntime.getNumDevices(platformIndex); deviceIndex++) {
				final OCLDeviceMapping device = new OCLDeviceMapping(platformIndex, deviceIndex);

				final RotateTornado deviceTest = new RotateTornado(iterations, numElementsX,
						numElementsY, device);

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
		if(args.length == 3){
			final int iterations = Integer.parseInt(args[0]);
			final int width = Integer.parseInt(args[1]);
			final int height = Integer.parseInt(args[1]);
			run(iterations, width, height);
		}else {
			run(100, 640, 480);
		}
		
	}

}
