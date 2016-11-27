package tornado.benchmarks.convolveimage;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.FloatOps;
import tornado.collections.types.ImageFloat;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

import static tornado.benchmarks.BenchmarkUtils.createFilter;
import static tornado.benchmarks.BenchmarkUtils.createImage;

public class ConvolveImageTornado extends BenchmarkDriver {

    private final int imageSizeX, imageSizeY, filterSize;
    private final DeviceMapping device;

    private ImageFloat input, output, filter;

    private TaskGraph graph;

    public ConvolveImageTornado(int iterations, int imageSizeX, int imageSizeY,
            int filterSize, DeviceMapping device) {
        super(iterations);
        this.imageSizeX = imageSizeX;
        this.imageSizeY = imageSizeY;
        this.filterSize = filterSize;
        this.device = device;

    }

    @Override
    public void setUp() {
        input = new ImageFloat(imageSizeX, imageSizeY);
        output = new ImageFloat(imageSizeX, imageSizeY);
        filter = new ImageFloat(filterSize, filterSize);

        createImage(input);
        createFilter(filter);

        graph = new TaskGraph()
                .add(GraphicsKernels::convolveImage, input,
                        filter, output)
                .streamOut(output)
                .mapAllTo(device);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        input = null;
        output = null;
        filter = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.schedule().waitOn();
    }

    @Override
    public boolean validate() {

        final ImageFloat result = new ImageFloat(imageSizeX, imageSizeY);

        code();
        graph.clearProfiles();

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
