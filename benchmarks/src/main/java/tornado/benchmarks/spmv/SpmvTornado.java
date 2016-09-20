package tornado.benchmarks.spmv;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.collections.math.TornadoMath;
import tornado.collections.matrix.SparseMatrixUtils.CSRMatrix;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class SpmvTornado extends BenchmarkDriver {

    private final CSRMatrix<float[]> matrix;

    private float[] v, y;

    private final DeviceMapping device;

    private TaskGraph graph;

    public SpmvTornado(int iterations, CSRMatrix<float[]> matrix,
            DeviceMapping device) {
        super(iterations);
        this.matrix = matrix;
        this.device = device;

    }

    @Override
    public void setUp() {
        v = new float[matrix.size];
        y = new float[matrix.size];

        Benchmark.populateVector(v);

        graph = new TaskGraph()
                .add(LinearAlgebraArrays::spmv, matrix.vals,
                        matrix.cols, matrix.rows, v, matrix.size, y)
                .streamOut(y)
                .mapAllTo(device);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        v = null;
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

        final float[] ref = new float[matrix.size];

        code();

//        System.out.printf("spmv: status=%s %s\n", spmv.getEvent().getStatus(),
//                spmv.getEvent().toString());
        LinearAlgebraArrays.spmv(matrix.vals, matrix.cols, matrix.rows, v,
                matrix.size, ref);

        final float ulp = TornadoMath.findULPDistance(y, ref);
        return ulp < MAX_ULP;
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
