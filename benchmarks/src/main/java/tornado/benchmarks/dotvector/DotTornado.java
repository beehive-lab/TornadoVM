package tornado.benchmarks.dotvector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.math.TornadoMath;
import tornado.collections.types.Float3;
import tornado.collections.types.VectorFloat3;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class DotTornado extends BenchmarkDriver {

    private final int numElements;
    private final DeviceMapping device;

    private VectorFloat3 a, b;
    private float[] c;

    private TaskGraph graph;
    
    public DotTornado(int iterations, int numElements, DeviceMapping device) {
        super(iterations);
        this.numElements = numElements;
        this.device = device;
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

        graph = new TaskGraph()
            .add(GraphicsKernels::dotVector, a, b, c)
            .streamOut(c)
            .mapAllTo(device);
        
        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        a = null;
        b = null;
        c = null;

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

        GraphicsKernels.dotVector(a, b, result);

       final float ulp = TornadoMath.findULPDistance(result, c);
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
