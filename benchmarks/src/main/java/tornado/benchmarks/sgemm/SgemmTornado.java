package tornado.benchmarks.sgemm;

import java.util.Random;

import tornado.api.DeviceMapping;
import tornado.api.Event;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.collections.types.FloatOps;
import tornado.benchmarks.EventList;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.ExecutableTask;

public class SgemmTornado extends BenchmarkDriver {

    private final DeviceMapping device;
    private final int m, n;

    private float[] a, b, c;

    private ExecutableTask sgemm;

    private Event last;
    private final EventList<Event> events;

    public SgemmTornado(int iterations, int m, int n, DeviceMapping device) {
        super(iterations);
        this.m = m;
        this.n = n;
        this.device = device;
        events = new EventList<Event>(iterations);
        last = null;
    }

    @Override
    public void setUp() {
        a = new float[m * n];
        b = new float[m * n];
        c = new float[m * n];

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 1;
        }

        for (int i = 0; i < m * n; i++) {
            b[i] = random.nextFloat();
        }

        sgemm = TaskUtils.createTask(LinearAlgebraArrays::sgemm, m, n, n, a, b,
                c);
        sgemm.mapTo(device);
    }

    @Override
    public void tearDown() {
        sgemm.invalidate();

        a = null;
        b = null;
        c = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {

        if (last == null)
            sgemm.schedule();
        else
            sgemm.schedule(last);
        last = sgemm.getEvent();
        events.add(last);
    }

    @Override
    public void barrier() {
        last.waitOn();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[m * n];

        sgemm.execute();

        LinearAlgebraArrays.sgemm(m, n, m, a, b, result);

        int errors = 0;
        for (int i = 0; i < c.length; i++) {
            if (!FloatOps.compareBits(result[i], c[i])) {
                errors++;
            }
        }

        return (errors == 0);
    }

    public void printSummary() {
        if (isValid())
            System.out.printf(
                    "id=opencl-device-%d, elapsed=%f, per iteration=%f, %s\n",
                    ((OCLDeviceMapping) device).getDeviceIndex(), getElapsed(),
                    getElapsedPerIteration(), events.summeriseEvents());
        else
            System.out.printf("id=opencl-device-%d produced invalid result\n",
                    ((OCLDeviceMapping) device).getDeviceIndex());
    }

    public double getOverhead() {
        return 1.0 - events.getMeanExecutionTime() / getElapsedPerIteration();
    }

}
