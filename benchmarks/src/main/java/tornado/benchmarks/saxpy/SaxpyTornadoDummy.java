package tornado.benchmarks.saxpy;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;

public class SaxpyTornadoDummy extends BenchmarkDriver {

    private final int numElements;

    private float[] x, y;
    private final float alpha = 2f;

    public SaxpyTornadoDummy(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        x = new float[numElements];
        y = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = i;
        }

    }

    @Override
    public void tearDown() {
        x = null;
        y = null;
        super.tearDown();
    }

    @Override
    public void code() {
        LinearAlgebraArrays.saxpy(alpha, x, y);
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
