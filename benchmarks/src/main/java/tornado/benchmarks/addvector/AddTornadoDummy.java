package tornado.benchmarks.addvector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float4;
import tornado.collections.types.VectorFloat4;

public class AddTornadoDummy extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat4 a, b, c;

    public AddTornadoDummy(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new VectorFloat4(numElements);
        b = new VectorFloat4(numElements);
        c = new VectorFloat4(numElements);

        final Float4 valueA = new Float4(new float[] { 1f, 1f, 1f, 1f });
        final Float4 valueB = new Float4(new float[] { 2f, 2f, 2f, 2f });
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
        GraphicsKernels.addVector(a, b, c);
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
