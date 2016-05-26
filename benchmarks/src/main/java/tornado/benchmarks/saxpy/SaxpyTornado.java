package tornado.benchmarks.saxpy;

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

public class SaxpyTornado extends BenchmarkDriver {

    private final int numElements;
    private final DeviceMapping device;

    private float[] x, y;
    private final float alpha = 2f;

    private ExecutableTask saxpy;

    private Event last;
    private final EventList<Event> events;

    public SaxpyTornado(int iterations, int numElements, DeviceMapping device) {
        super(iterations);
        this.numElements = numElements;
        this.device = device;
        events = new EventList<Event>(iterations);
        last = null;
    }

    @Override
    public void setUp() {
        x = new float[numElements];
        y = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = i;
        }

        saxpy = TaskUtils.createTask(LinearAlgebraArrays::saxpy, alpha, x, y);
        saxpy.mapTo(device);
    }

    @Override
    public void tearDown() {
        saxpy.invalidate();

        x = null;
        y = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {

        if (last == null)
            saxpy.schedule();
        else
            saxpy.schedule(last);
        last = saxpy.getEvent();
        events.add(last);
    }

    @Override
    public void barrier() {
        last.waitOn();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[numElements];

        saxpy.execute();

        LinearAlgebraArrays.saxpy(alpha, x, result);

        final float ulp = TornadoMath.findULPDistance(y,result);
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
