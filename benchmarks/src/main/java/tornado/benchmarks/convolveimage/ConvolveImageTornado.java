package tornado.benchmarks.convolveimage;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.FloatOps;
import tornado.collections.types.ImageFloat;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.BenchmarkUtils.createFilter;
import static tornado.benchmarks.BenchmarkUtils.createImage;
import static tornado.common.Tornado.getProperty;

public class ConvolveImageTornado extends BenchmarkDriver {

    private final int imageSizeX, imageSizeY, filterSize;

    private ImageFloat input, output, filter;

    private TaskSchedule graph;

    public ConvolveImageTornado(int iterations, int imageSizeX, int imageSizeY,
            int filterSize) {
        super(iterations);
        this.imageSizeX = imageSizeX;
        this.imageSizeY = imageSizeY;
        this.filterSize = filterSize;
    }

    @Override
    public void setUp() {
        input = new ImageFloat(imageSizeX, imageSizeY);
        output = new ImageFloat(imageSizeX, imageSizeY);
        filter = new ImageFloat(filterSize, filterSize);

        createImage(input);
        createFilter(filter);

        graph = new TaskSchedule("s0")
                .task("t0", GraphicsKernels::convolveImage, input,
                        filter, output)
                .streamOut(output);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        input = null;
        output = null;
        filter = null;

        graph.getDefaultDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
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
                    "id=%s, elapsed=%f, per iteration=%f\n",
                    getProperty("s0.device"), getElapsed(),
                    getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n",
                    getProperty("s0.device"));
        }
    }

}
