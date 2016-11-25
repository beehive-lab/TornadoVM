package tornado.benchmarks.bandwidth;

import tornado.benchmarks.BenchmarkDriver;

import static tornado.benchmarks.LinearAlgebraArrays.ladd;

public class BandwidthJava extends BenchmarkDriver {

    private final int numElements;

    private long[] a, b, c;

    public BandwidthJava(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new long[numElements];
        b = new long[numElements];
        c = new long[numElements];

        for (int i = 0; i < numElements; i++) {
            a[i] = 1;
            b[i] = 2;
            c[i] = 0;
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
        ladd(a, b, c);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate() {
        return true;
    }

    public void printSummary() {
        System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n", getElapsed(), getElapsedPerIteration());
    }

}
