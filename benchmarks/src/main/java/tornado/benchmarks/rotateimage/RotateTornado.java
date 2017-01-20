package tornado.benchmarks.rotateimage;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float3;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.GraphicsKernels.rotateImage;
import static tornado.collections.types.FloatOps.findMaxULP;
import static tornado.common.Tornado.getProperty;

public class RotateTornado extends BenchmarkDriver {

    private final int numElementsX, numElementsY;

    private ImageFloat3 input, output;
    private Matrix4x4Float m;

    private TaskSchedule graph;

    public RotateTornado(int iterations, int numElementsX, int numElementsY) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
    }

    @Override
    public void setUp() {
        input = new ImageFloat3(numElementsX, numElementsY);
        output = new ImageFloat3(numElementsX, numElementsY);

        m = new Matrix4x4Float();
        m.identity();

        final Float3 value = new Float3(new float[]{1f, 2f, 3f});
        for (int i = 0; i < input.Y(); i++) {
            for (int j = 0; j < input.X(); j++) {
                input.set(j, i, value);
            }
        }

        graph = new TaskSchedule("s0")
                .task("to", GraphicsKernels::rotateImage, output, m,
                        input)
                .streamOut(output);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        input = null;
        output = null;
        m = null;

        graph.getDefaultDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final ImageFloat3 result = new ImageFloat3(numElementsX, numElementsY);

        code();
        graph.clearProfiles();

        rotateImage(result, m, input);

        float maxULP = 0f;
        for (int i = 0; i < input.Y(); i++) {
            for (int j = 0; j < input.X(); j++) {
                final float ulp = findMaxULP(output.get(j, i),
                        result.get(j, i));

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
