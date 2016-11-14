package tornado.benchmarks.rotateimage;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float3;
import tornado.collections.types.FloatOps;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class RotateTornado extends BenchmarkDriver {

    private final int numElementsX, numElementsY;
    private final DeviceMapping device;

    private ImageFloat3 input, output;
    private Matrix4x4Float m;

    private TaskGraph graph;

    public RotateTornado(int iterations, int numElementsX, int numElementsY,
            DeviceMapping device) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
        this.device = device;
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

        graph = new TaskGraph()
                .add(GraphicsKernels::rotateImage, output, m,
                        input)
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
        m = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.schedule().waitOn();
    }

    @Override
    public boolean validate() {

        final ImageFloat3 result = new ImageFloat3(numElementsX, numElementsY);

        code();

        GraphicsKernels.rotateImage(result, m, input);

        float maxULP = 0f;
        for (int i = 0; i < input.Y(); i++) {
            for (int j = 0; j < input.X(); j++) {
                final float ulp = FloatOps.findMaxULP(output.get(j, i),
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
                    "id=opencl-device-%d, elapsed=%f, per iteration=%f\n",
                    ((OCLDeviceMapping) device).getDeviceIndex(), getElapsed(),
                    getElapsedPerIteration());
        } else {
            System.out.printf("id=opencl-device-%d produced invalid result\n",
                    ((OCLDeviceMapping) device).getDeviceIndex());
        }
    }

}
