package tornado.benchmarks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.OCLDriver;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;

import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

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

        final Set<Integer> blacklistedPlatforms = new HashSet<Integer>();
        final Set<Integer> blacklistedDevices = new HashSet<Integer>();

        findBlacklisted(blacklistedPlatforms, "tornado.blacklist.platform");
        findBlacklisted(blacklistedDevices, "tornado.blacklist.device");

        final OCLDriver oclRuntime = getTornadoRuntime().getDriver(OCLDriver.class);
        for (int platformIndex = 0; platformIndex < oclRuntime.getNumPlatforms(); platformIndex++) {

            if (blacklistedPlatforms.contains(platformIndex)) {
                continue;
            }

            for (int deviceIndex = 0; deviceIndex < oclRuntime.getNumDevices(platformIndex); deviceIndex++) {

                if (blacklistedDevices.contains(deviceIndex)) {
                    continue;
                }

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

    private void findBlacklisted(Set<Integer> blacklist, String property) {
        final String values = System.getProperty(property, "");
        if (values.isEmpty()) {
            return;
        }

        final String[] ids = values.split(",");
        for (String id : ids) {
            final int value = Integer.parseInt(id);
            blacklist.add(value);
        }
    }

}
