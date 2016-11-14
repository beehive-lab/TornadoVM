package tornado.benchmarks.spmv;

import java.util.Random;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.BenchmarkRunner;
import tornado.collections.matrix.SparseMatrixUtils;
import tornado.collections.matrix.SparseMatrixUtils.CSRMatrix;
import tornado.common.DeviceMapping;

public class Benchmark extends BenchmarkRunner {

    private CSRMatrix<float[]> matrix;
    private String path;

    public static void populateVector(final float[] v) {
        final Random rand = new Random();
        rand.setSeed(7);
        for (int i = 0; i < v.length; i++) {
            v[i] = rand.nextFloat() * 100.0f;
        }
    }

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            final String fullpath = args[1];
            path = fullpath.substring(fullpath.lastIndexOf("/") + 1);
            matrix = SparseMatrixUtils.loadMatrixF(fullpath);
        } else {
            path = System.getProperty("spmv.matrix", "/bcsstk32.mtx");
            matrix = SparseMatrixUtils.loadMatrixF(Benchmark.class.getResourceAsStream(path));
            iterations = Integer.parseInt(System.getProperty("spmv.iterations", "1400"));
        }
    }

    @Override
    protected String getName() {
        return "spmv";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%s", getName(), iterations, path);
    }

    @Override
    protected String getConfigString() {
        return String.format("matrix=%s", path);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new SpmvJava(iterations, matrix);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver(DeviceMapping device) {
        return new SpmvTornado(iterations, matrix, device);
    }

}
