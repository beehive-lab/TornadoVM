package tornado.benchmarks.rotatevector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float3;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.VectorFloat3;

public class RotateTornadoDummy extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 input, output;
    private Matrix4x4Float m;

    public RotateTornadoDummy(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        input = new VectorFloat3(numElements);
        output = new VectorFloat3(numElements);

        m = new Matrix4x4Float();
        m.identity();

        final Float3 value = new Float3(new float[] { 1f, 2f, 3f });
        for (int i = 0; i < numElements; i++) {
            input.set(i, value);
        }
    }

    @Override
    public void tearDown() {
        input = null;
        output = null;
        m = null;
        super.tearDown();
    }

    @Override
    public void code() {
        GraphicsKernels.rotateVector(output, m, input);
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
