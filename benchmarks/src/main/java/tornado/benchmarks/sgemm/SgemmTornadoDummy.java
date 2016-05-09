package tornado.benchmarks.sgemm;

import java.util.Random;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;

public class SgemmTornadoDummy extends BenchmarkDriver {

    private final int m, n;

    private float[] a, b, c;

    public SgemmTornadoDummy(int iterations, int m, int n) {
        super(iterations);
        this.m = m;
        this.n = n;
    }

    @Override
    public void setUp() {
        a = new float[m * n];
        b = new float[m * n];
        c = new float[m * n];

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 1;
        }

        for (int i = 0; i < m * n; i++) {
            b[i] = random.nextFloat();
        }

    }

    @Override
    public void tearDown() {
        a = null;
        b = null;
        c = null;
        super.tearDown();
    }

    @Override
    public void code() {
        LinearAlgebraArrays.sgemm(m, n, m, a, b, c);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate() {

        return true;
    }

    public void printSummary() {
        System.out.printf("id=tornado-dummy, elapsed=%f, per iteration=%f\n",
                getElapsed(), getElapsedPerIteration());
    }

}
