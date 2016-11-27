package tornado.benchmarks.bandwidth;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class BandwidthTornado extends BenchmarkDriver {

    private final int numElements;
    private final DeviceMapping device;

    private long[] a, b, c;

    private TaskGraph graph;

    public BandwidthTornado(int iterations, int numElements, DeviceMapping device) {
        super(iterations);
        this.numElements = numElements;
        this.device = device;
    }

    @Override
    public void setUp() {
        a = new long[numElements];
        b = new long[numElements];
        c = new long[numElements];

        for (int i = 0; i < numElements; i++) {
            a[i] = 1;
            b[i] = 2;
            c[i] = 0;
        }

        graph = new TaskGraph()
                .add(LinearAlgebraArrays::ladd, a, b, c)
                .streamOut(c)
                .mapAllTo(device);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        a = null;
        b = null;
        c = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.schedule().waitOn();
    }

    @Override
    public boolean validate() {

        final long[] result = new long[numElements];

        code();
        graph.clearProfiles();

        LinearAlgebraArrays.ladd(a, b, result);

        int errors = 0;
        for (int i = 0; i < numElements; i++) {
            if (result[i] != c[i]) {
                errors++;
            }
        }

        return errors == 0;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf(
                    "id=opencl-device-%d, elapsed=%f, per iteration=%f\n",
                    ((OCLDeviceMapping) device).getDeviceIndex(), getElapsed(),
                    getElapsedPerIteration());
        } else {
            System.out.printf("id=opencl-device-%d produced invalid result\n",
                    ((OCLDeviceMapping) device).getDeviceIndex());
        }
    }
}
