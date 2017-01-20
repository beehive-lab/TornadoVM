package tornado.benchmarks.spmv;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.collections.matrix.SparseMatrixUtils.CSRMatrix;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.LinearAlgebraArrays.spmv;
import static tornado.benchmarks.spmv.Benchmark.populateVector;
import static tornado.collections.math.TornadoMath.findULPDistance;
import static tornado.common.Tornado.getProperty;

public class SpmvTornado extends BenchmarkDriver {

    private final CSRMatrix<float[]> matrix;

    private float[] v, y;

    private TaskSchedule graph;

    public SpmvTornado(int iterations, CSRMatrix<float[]> matrix) {
        super(iterations);
        this.matrix = matrix;
    }

    @Override
    public void setUp() {
        v = new float[matrix.size];
        y = new float[matrix.size];

        populateVector(v);

        graph = new TaskSchedule("s0")
                .task("t0", LinearAlgebraArrays::spmv, matrix.vals,
                        matrix.cols, matrix.rows, v, matrix.size, y)
                .streamOut(y);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpTimes();
        graph.dumpProfiles();

        v = null;
        y = null;

        graph.getDefaultDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] ref = new float[matrix.size];

        code();
        graph.clearProfiles();

        spmv(matrix.vals, matrix.cols, matrix.rows, v, matrix.size, ref);

        final float ulp = findULPDistance(y, ref);
        System.out.printf("ulp is %f\n", ulp);
        return ulp < MAX_ULP;
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
