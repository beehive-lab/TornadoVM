package tornado.benchmarks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import tornado.runtime.TornadoDriver;

import static tornado.common.Tornado.setProperty;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public abstract class BenchmarkRunner {

    protected abstract String getName();

    protected abstract String getIdString();

    protected abstract String getConfigString();

    protected abstract BenchmarkDriver getJavaDriver();

    protected abstract BenchmarkDriver getTornadoDriver();

    protected BenchmarkDriver getStreamsDriver() {
        return null;
    }

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

        final BenchmarkDriver streamsTest = getStreamsDriver();
        if (streamsTest != null) {
            streamsTest.benchmark();

            System.out.printf("bm=%-15s, id=%-20s, %s\n", id, "java-streams",
                    streamsTest.getSummary());

            // final double refElapsed = streamsTest.getElapsed();
        }

        final Set<Integer> blacklistedDrivers = new HashSet<>();
        final Set<Integer> blacklistedDevices = new HashSet<>();

        findBlacklisted(blacklistedDrivers, "tornado.blacklist.drivers");
        findBlacklisted(blacklistedDevices, "tornado.blacklist.device");

        final int numDrivers = getTornadoRuntime().getNumDrivers();
        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {
            if (blacklistedDrivers.contains(driverIndex)) {
                continue;
            }

            final TornadoDriver driver = getTornadoRuntime().getDriver(driverIndex);
            final int numDevices = driver.getDeviceCount();

            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                if (blacklistedDevices.contains(deviceIndex)) {
                    continue;
                }

                setProperty("s0.device", driverIndex + ":" + deviceIndex);
                final BenchmarkDriver deviceTest = getTornadoDriver();

                deviceTest.benchmark();

                System.out.printf("bm=%-15s, device=%-5s, %s, speedup=%.4f\n", id,
                        driverIndex + ":" + deviceIndex,
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
