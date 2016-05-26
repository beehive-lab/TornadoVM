package tornado.benchmarks.convolveimage;

import static tornado.benchmarks.BenchmarkUtils.createFilter;
import static tornado.benchmarks.BenchmarkUtils.createImage;
import tornado.api.DeviceMapping;
import tornado.api.Event;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.FloatOps;
import tornado.collections.types.ImageFloat;
import tornado.benchmarks.EventList;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.ExecutableTask;

public class ConvolveImageTornado extends BenchmarkDriver {

    private final int imageSizeX, imageSizeY, filterSize;
    private final DeviceMapping device;

    private ImageFloat input, output, filter;

    private ExecutableTask convolve;

    private Event last;
    private final EventList<Event> events;

    public ConvolveImageTornado(int iterations, int imageSizeX, int imageSizeY,
            int filterSize, DeviceMapping device) {
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
        input = new ImageFloat(imageSizeX, imageSizeY);
        output = new ImageFloat(imageSizeX, imageSizeY);
        filter = new ImageFloat(filterSize, filterSize);

        createImage(input);
        createFilter(filter);

        convolve = TaskUtils.createTask(GraphicsKernels::convolveImage, input,
                filter, output);
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

        if (last == null)
            convolve.schedule();
        else
            convolve.schedule(last);
        last = convolve.getEvent();

        events.add(last);
    }

    @Override
    public void barrier() {
        last.waitOn();
    }

    @Override
    public boolean validate() {

        final ImageFloat result = new ImageFloat(imageSizeX, imageSizeY);

        convolve.execute();

        GraphicsKernels.convolveImage(input, filter, result);

        float maxULP = 0f;
        for (int y = 0; y < output.Y(); y++) {
            for (int x = 0; x < output.X(); x++) {
                final float ulp = FloatOps.findMaxULP(output.get(x, y), result.get(x, y));
                
            	if (ulp > maxULP) {
                    maxULP = ulp;
                }
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
