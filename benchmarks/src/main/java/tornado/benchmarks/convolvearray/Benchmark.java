package tornado.benchmarks.convolvearray;

import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.drivers.opencl.runtime.OCLDriver;
import tornado.runtime.TornadoRuntime;

public class Benchmark {

	private static final String	BENCHMARK_NAME	= "convolve-image-array";

	public static void main(String[] args) {
		if(args.length == 4){
			final int iterations = Integer.parseInt(args[0]);
			final int width = Integer.parseInt(args[1]);
			final int height = Integer.parseInt(args[2]);
			final int filtersize = Integer.parseInt(args[3]);
			run(iterations, height, width, filtersize);
		}else {
			run(100, 1080, 1920, 5);
		}
	}

	public static void run(int iterations, int imageSizeX, int imageSizeY, int filterSize) {
		System.out.printf("benchmark=%s, iterations=%d, num elements={%d,%d}\n", BENCHMARK_NAME,
				iterations, imageSizeX, imageSizeY, filterSize);

		final ConvolveImageArrayJava referenceTest = new ConvolveImageArrayJava(iterations,
				imageSizeX, imageSizeY, filterSize);
		referenceTest.benchmark();

		System.out.printf("bm=%-15s, id=%-20s, %s\n", BENCHMARK_NAME, "java-reference",
				referenceTest.getSummary());

		final double refElapsed = referenceTest.getElapsed();

		final ConvolveImageArrayTornadoDummy tornadoOverhead = new ConvolveImageArrayTornadoDummy(
				iterations, imageSizeX, imageSizeY, filterSize);
		tornadoOverhead.benchmark();
		System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f\n", BENCHMARK_NAME,
				"tornado-dummy", tornadoOverhead.getSummary(),
				refElapsed / tornadoOverhead.getElapsed());

		final OCLDriver oclRuntime = (OCLDriver) TornadoRuntime.runtime;
		for (int platformIndex = 0; platformIndex < oclRuntime.getNumPlatforms(); platformIndex++) {
			for (int deviceIndex = 0; deviceIndex < oclRuntime.getNumDevices(platformIndex); deviceIndex++) {
				final OCLDeviceMapping device = new OCLDeviceMapping(platformIndex, deviceIndex);

				final ConvolveImageArrayTornado deviceTest = new ConvolveImageArrayTornado(
						iterations, imageSizeX, imageSizeY, filterSize, device);

				deviceTest.benchmark();

				System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f, overhead=%.4f\n",
						BENCHMARK_NAME, "opencl-device-" + platformIndex + "-" + deviceIndex,
						deviceTest.getSummary(), refElapsed / deviceTest.getElapsed(),
						deviceTest.getOverhead());
			}
		}
		
		TornadoRuntime.resetDevices();

	}

}
