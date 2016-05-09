package tornado.benchmarks.rotateimage;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float3;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;

public class RotateTornadoDummy extends BenchmarkDriver {

    private final int numElementsX, numElementsY;

    private ImageFloat3 input, output;
    private Matrix4x4Float m;

    public RotateTornadoDummy(int iterations, int numElementsX, int numElementsY) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
    }

    @Override
    public void setUp() {
        input = new ImageFloat3(numElementsX, numElementsY);
        output = new ImageFloat3(numElementsX, numElementsY);

        m = new Matrix4x4Float();
        m.identity();

        final Float3 value = new Float3(new float[] { 1f, 2f, 3f });
        for (int i = 0; i < input.Y(); i++) {
            for (int j = 0; j < input.X(); j++)
                input.set(j, i, value);
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
        GraphicsKernels.rotateImage(output, m, input);
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
