package tornado.benchmarks.rotatevector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float3;
import tornado.collections.types.FloatOps;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.VectorFloat3;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class RotateTornado extends BenchmarkDriver {

    private final int numElements;
    private final DeviceMapping device;

    private VectorFloat3 input, output;
    private Matrix4x4Float m;

    private TaskGraph graph;

    public RotateTornado(int iterations, int numElements, DeviceMapping device) {
        super(iterations);
        this.numElements = numElements;
        this.device = device;
    }

    @Override
    public void setUp() {
        input = new VectorFloat3(numElements);
        output = new VectorFloat3(numElements);

        m = new Matrix4x4Float();
        m.identity();

        final Float3 value = new Float3(new float[]{1f, 2f, 3f});
        for (int i = 0; i < numElements; i++) {
            input.set(i, value);
        }

        graph = new TaskGraph()
                .add(GraphicsKernels::rotateVector, output, m,
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

        final VectorFloat3 result = new VectorFloat3(numElements);

        code();
        graph.clearProfiles();

        GraphicsKernels.rotateVector(result, m, input);

        float maxULP = 0f;
        for (int i = 0; i < numElements; i++) {
            final float ulp = FloatOps.findMaxULP(output.get(i), result.get(i));

            if (ulp > maxULP) {
                maxULP = ulp;
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
