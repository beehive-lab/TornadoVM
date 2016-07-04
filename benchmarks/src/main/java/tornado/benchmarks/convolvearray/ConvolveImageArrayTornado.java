package tornado.benchmarks.convolvearray;

import tornado.benchmarks.BenchmarkDriver;
import static tornado.benchmarks.BenchmarkUtils.createFilter;
import static tornado.benchmarks.BenchmarkUtils.createImage;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.FloatOps;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class ConvolveImageArrayTornado extends BenchmarkDriver {

    private final int imageSizeX, imageSizeY, filterSize;
    private final DeviceMapping device;

    private float[] input, output, filter;

    private TaskGraph graph;

    public ConvolveImageArrayTornado(int iterations, int imageSizeX,
            int imageSizeY, int filterSize, DeviceMapping device) {
        super(iterations);
        this.imageSizeX = imageSizeX;
        this.imageSizeY = imageSizeY;
        this.filterSize = filterSize;
        this.device = device;
    }

    @Override
    public void setUp() {
        input = new float[imageSizeX * imageSizeY];
        output = new float[imageSizeX * imageSizeY];
        filter = new float[filterSize * filterSize];

        createImage(input, imageSizeX, imageSizeY);
        createFilter(filter, filterSize, filterSize);

        graph = new TaskGraph()
                .add(GraphicsKernels::convolveImageArray,
                input, filter, output, imageSizeX, imageSizeY, filterSize,
                filterSize)
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

        final float[] result = new float[imageSizeX * imageSizeY];

        code();

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
                    "id=opencl-device-%d, elapsed=%f, per iteration=%f\n",
                    ((OCLDeviceMapping) device).getDeviceIndex(), getElapsed(),
                    getElapsedPerIteration());
        else
            System.out.printf("id=opencl-device-%d produced invalid result\n",
                    ((OCLDeviceMapping) device).getDeviceIndex());
    }
}
