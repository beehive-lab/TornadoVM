package tornado.benchmarks.sadd;

import tornado.api.DeviceMapping;
import tornado.api.Event;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.collections.math.TornadoMath;
import tornado.collections.types.FloatOps;
import tornado.benchmarks.EventList;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.ExecutableTask;

public class SaddTornado extends BenchmarkDriver {

    private final int numElements;
    private final DeviceMapping device;

    private float[] a, b, c;

    private ExecutableTask sadd;

    private Event last;
    private final EventList<Event> events;

    public SaddTornado(int iterations, int numElements, DeviceMapping device) {
        super(iterations);
        this.numElements = numElements;
        this.device = device;
        events = new EventList<Event>(iterations);
        last = null;
    }

    @Override
    public void setUp() {
        a = new float[numElements];
        b = new float[numElements];
        c = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            a[i] = 1;
            b[i] = 2;
            c[i] = 0;
        }

        sadd = TaskUtils.createTask(LinearAlgebraArrays::sadd, a, b, c);
        sadd.mapTo(device);
    }

    @Override
    public void tearDown() {
        sadd.invalidate();

        a = null;
        b = null;
        c = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {

        if (last == null)
            sadd.schedule();
        else
            sadd.schedule(last);
        last = sadd.getEvent();

        events.add(last);
    }

    @Override
    public void barrier() {
        last.waitOn();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[numElements];

        sadd.execute();

        LinearAlgebraArrays.sadd(a, b, result);

        final float ulp = TornadoMath.findULPDistance(c,result);
        return ulp < MAX_ULP;
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
