package tornado.benchmarks;

import java.util.Arrays;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.OCLDriver;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.TornadoRuntime;

public abstract class BenchmarkRunner {

	protected abstract String getName();
        protected abstract String getIdString();
        protected abstract String getConfigString();
        
        protected abstract BenchmarkDriver getJavaDriver();
        protected abstract BenchmarkDriver getTornadoDriver(DeviceMapping device);
        
        protected int iterations = 0;

	public void run() {
		final String id = getIdString(); 

		System.out.printf("benchmark=%s, iterations=%d, %s\n", id, iterations,
				getConfigString());

		final BenchmarkDriver referenceTest = getJavaDriver();
		referenceTest.benchmark();

		System.out.printf("bm=%-15s, id=%-20s, %s\n", id, "java-reference",
				referenceTest.getSummary());

		final double refElapsed = referenceTest.getElapsed();

		final OCLDriver oclRuntime = (OCLDriver) TornadoRuntime.runtime.getDriver(0);
		for (int platformIndex = 0; platformIndex < oclRuntime.getNumPlatforms(); platformIndex++) {
			for (int deviceIndex = 0; deviceIndex < oclRuntime.getNumDevices(platformIndex); deviceIndex++) {
				final OCLDeviceMapping device = new OCLDeviceMapping(platformIndex, deviceIndex);

				final BenchmarkDriver deviceTest = getTornadoDriver(device);

				deviceTest.benchmark();

				System.out.printf("bm=%-15s, id=%-20s, %s, speedup=%.4f\n", id,
						"opencl-device-" + platformIndex + "-" + deviceIndex,
						deviceTest.getSummary(), refElapsed / deviceTest.getElapsed());
			}
		}
	}

        
        public abstract void parseArgs(String[] args);
        
	public static void main(String[] args) {
            try {
                final BenchmarkRunner bm = (BenchmarkRunner) Class.forName(args[0]).newInstance();
                final String[] bmArgs = Arrays.copyOfRange(args, 1, args.length);
                bm.parseArgs(bmArgs);
                bm.run();
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                e.printStackTrace();
                System.exit(-1);
            }
           
	}

}
