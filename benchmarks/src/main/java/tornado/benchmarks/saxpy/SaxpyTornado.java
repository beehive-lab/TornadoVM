package tornado.benchmarks.saxpy;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.collections.math.TornadoMath;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class SaxpyTornado extends BenchmarkDriver {

    private final int numElements;
    private final DeviceMapping device;

    private float[] x, y;
    private final float alpha = 2f;

    private TaskGraph graph;

    public SaxpyTornado(int iterations, int numElements, DeviceMapping device) {
        super(iterations);
        this.numElements = numElements;
        this.device = device;
    }

    @Override
    public void setUp() {
        x = new float[numElements];
        y = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = i;
        }

        graph = new TaskGraph()
                .add(LinearAlgebraArrays::saxpy, alpha, x, y)
                .streamOut(y)
                .mapAllTo(device);
        
        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        x = null;
        y = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {

        graph.schedule().waitOn();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[numElements];

        code();

        LinearAlgebraArrays.saxpy(alpha, x, result);

        final float ulp = TornadoMath.findULPDistance(y,result);
        return ulp < MAX_ULP;
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
