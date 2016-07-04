package tornado.benchmarks.sadd;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.collections.math.TornadoMath;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class SaddTornado extends BenchmarkDriver {

    private final int numElements;
    private final DeviceMapping device;

    private float[] a, b, c;

   private TaskGraph graph; 

    public SaddTornado(int iterations, int numElements, DeviceMapping device) {
        super(iterations);
        this.numElements = numElements;
        this.device = device;
    }

    @Override
    public void setUp() {
        a = new float[numElements];
        b = new float[numElements];
        c = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            a[i] = 1;
            b[i] = 2;
            c[i] = 0;
        }

        graph = new TaskGraph()
                .add(LinearAlgebraArrays::sadd, a, b, c)
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

        LinearAlgebraArrays.sadd(a, b, result);

        final float ulp = TornadoMath.findULPDistance(c,result);
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
