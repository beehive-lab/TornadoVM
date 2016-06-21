package tornado.benchmarks.dotvector;

import tornado.api.Event;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.math.TornadoMath;
import tornado.collections.types.Float3;
import tornado.collections.types.VectorFloat3;
import tornado.common.DeviceMapping;
import tornado.benchmarks.EventList;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.CompilableTask;

public class DotTornado extends BenchmarkDriver {

    private final int numElements;
    private final DeviceMapping device;

    private VectorFloat3 a, b;
    private float[] c;

    private CompilableTask<?> dot;

    private Event last;
    private final EventList<Event> events;

    public DotTornado(int iterations, int numElements, DeviceMapping device) {
        super(iterations);
        this.numElements = numElements;
        this.device = device;
        events = new EventList<Event>(iterations);
        last = null;
    }

    @Override
    public void setUp() {
        a = new VectorFloat3(numElements);
        b = new VectorFloat3(numElements);
        c = new float[numElements];

        final Float3 valueA = new Float3(new float[] { 1f, 1f, 1f });
        final Float3 valueB = new Float3(new float[] { 2f, 2f, 2f });
        for (int i = 0; i < numElements; i++) {
            a.set(i, valueA);
            b.set(i, valueB);
        }

        dot = TaskUtils.createTask(GraphicsKernels::dotVector, a, b, c);
        dot.mapTo(device);
    }

    @Override
    public void tearDown() {
        dot.invalidate();

        a = null;
        b = null;
        c = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {

        if (last == null) {
            dot.schedule();
        } else {
            dot.schedule(last);
        }
        last = dot.getEvent();
        events.add(last);
    }

    @Override
    public void barrier() {
        dot.waitOn();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[numElements];

        dot.execute();

        GraphicsKernels.dotVector(a, b, result);

       final float ulp = TornadoMath.findULPDistance(result, c);
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
