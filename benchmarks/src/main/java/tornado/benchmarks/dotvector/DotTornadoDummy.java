package tornado.benchmarks.dotvector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float3;
import tornado.collections.types.VectorFloat3;

public class DotTornadoDummy extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 a, b;
    private float[] c;

    public DotTornadoDummy(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
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
        GraphicsKernels.dotVector(a, b, c);
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
