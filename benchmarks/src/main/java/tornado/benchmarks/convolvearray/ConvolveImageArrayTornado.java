package tornado.benchmarks.convolvearray;

import static tornado.benchmarks.BenchmarkUtils.createFilter;
import static tornado.benchmarks.BenchmarkUtils.createImage;
import tornado.api.Event;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.FloatOps;
import tornado.common.DeviceMapping;
import tornado.benchmarks.EventList;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.CompilableTask;

public class ConvolveImageArrayTornado extends BenchmarkDriver {

    private final int imageSizeX, imageSizeY, filterSize;
    private final DeviceMapping device;

    private float[] input, output, filter;

    private CompilableTask convolve;

    private Event last;
    private final EventList<Event> events;

    public ConvolveImageArrayTornado(int iterations, int imageSizeX,
            int imageSizeY, int filterSize, DeviceMapping device) {
        super(iterations);
        this.imageSizeX = imageSizeX;
        this.imageSizeY = imageSizeY;
        this.filterSize = filterSize;
        this.device = device;
        events = new EventList<Event>(iterations);
        last = null;
    }

    @Override
    public void setUp() {
        input = new float[imageSizeX * imageSizeY];
        output = new float[imageSizeX * imageSizeY];
        filter = new float[filterSize * filterSize];

        createImage(input, imageSizeX, imageSizeY);
        createFilter(filter, filterSize, filterSize);

        convolve = TaskUtils.createTask(GraphicsKernels::convolveImageArray,
                input, filter, output, imageSizeX, imageSizeY, filterSize,
                filterSize);
        convolve.mapTo(device);

    }

    @Override
    public void tearDown() {
        convolve.invalidate();

        input = null;
        output = null;
        filter = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {

        if (last == null) {
            convolve.schedule();
        } else {
            convolve.schedule(last);
        }

        last = convolve.getEvent();
        events.add(last);
    }

    @Override
    public void barrier() {
        last.waitOn();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[imageSizeX * imageSizeY];

        convolve.execute();

        GraphicsKernels.convolveImageArray(input, filter, result, imageSizeX,
                imageSizeY, filterSize, filterSize);

        float maxULP = 0f;
        for (int i = 0; i < output.length; i++) {
            final float ulp = FloatOps.findMaxULP(result[i],output[i]);
      
        	if (ulp > maxULP) {
                maxULP = ulp;
            }
        }
        return maxULP < MAX_ULP;
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
