package tornado.benchmarks.spmv;

import java.util.Random;

import tornado.collections.matrix.SparseMatrixUtils;
import tornado.collections.matrix.SparseMatrixUtils.CSRMatrix;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.drivers.opencl.runtime.OCLRuntime;
import tornado.runtime.TornadoRuntime;

public class Benchmark {

	private static final String	BENCHMARK_NAME	= "spmv";

	public static void populateVector(final float[] v) {
		final Random rand = new Random();
		rand.setSeed(7);
		for (int i = 0; i < v.length; i++) {
			v[i] = rand.nextFloat() * 100.0f;
		}
	}
	
	public static void main(String[] args) {
		if(args.length == 2){
			final int iterations = Integer.parseInt(args[0]);
			final String path = args[1];
			run(iterations, path);
		}
	}

	public static void run(int iterations, String fullpath) {
		final CSRMatrix<float[]> matrix = SparseMatrixUtils.loadMatrixF(fullpath);
		System.out.printf("benchmark=%s, iterations=%d, path=%d\n", BENCHMARK_NAME,
				iterations, matrix.size);
		
		String path = fullpath.substring(fullpath.lastIndexOf("/")+1);

		final SpmvJava referenceTest = new SpmvJava(iterations, matrix);
		referenceTest.benchmark();

		System.out.printf("bm=%-15s, id=%-20s, %s\n", String.format("%s-%d-%s",BENCHMARK_NAME,iterations,path), "java-reference",
				referenceTest.getSummary());

		final double refElapsed = referenceTest.getElapsed();

		final SpmvTornadoDummy tornadoOverhead = new SpmvTornadoDummy(iterations, matrix);
		tornadoOverhead.benchmark();
		System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f\n", String.format("%s-%d-%s",BENCHMARK_NAME,iterations,path),
				"tornado-dummy", tornadoOverhead.getSummary(),
				refElapsed / tornadoOverhead.getElapsed());

		final OCLRuntime oclRuntime = (OCLRuntime) TornadoRuntime.runtime;
		for (int platformIndex = 0; platformIndex < oclRuntime.getNumPlatforms(); platformIndex++) {
			for (int deviceIndex = 0; deviceIndex < oclRuntime.getNumDevices(platformIndex); deviceIndex++) {
				final OCLDeviceMapping device = new OCLDeviceMapping(platformIndex, deviceIndex);
				final SpmvTornado deviceTest = new SpmvTornado(iterations, matrix, device);

				deviceTest.benchmark();

				System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f, overhead=%.4f\n",
						String.format("%s-%d-%s",BENCHMARK_NAME,iterations,path), "opencl-device-" + platformIndex + "-" + deviceIndex,
						deviceTest.getSummary(), refElapsed / deviceTest.getElapsed(),
						deviceTest.getOverhead());
			}
		}
		
		TornadoRuntime.resetDevices();

	}

}
