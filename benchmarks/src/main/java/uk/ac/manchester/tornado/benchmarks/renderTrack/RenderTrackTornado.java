package uk.ac.manchester.tornado.benchmarks.renderTrack;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

import java.util.Random;

public class RenderTrackTornado extends BenchmarkDriver {

    private int size;
    private ImageFloat3 input;
    private ImageByte3 output;
    private TaskSchedule s0;

    public RenderTrackTornado(int size, int iterations) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        output = new ImageByte3(size, size);
        input = new ImageFloat3(size, size);
        Random r = new Random();
        for (int i = 0; i < input.X(); i++) {
            for (int j = 0; j < input.Y(); j++) {
                float value = (float) r.nextInt(10) * -1;
                input.set(i, j, new Float3(i, j, value));
            }
        }
        s0 = new TaskSchedule("s0")//
                .task("t0", ComputeKernels::renderTrack, output, input) //
                .streamOut(output);
        s0.warmup();
    }

    @Override
    public void tearDown() {
        s0.dumpProfiles();
        input = null;
        output = null;
        s0.getDevice().reset();
        super.tearDown();
    }

    private static boolean validate(ImageFloat3 input, ImageByte3 outputTornado) {
        ImageByte3 validationOutput = new ImageByte3(outputTornado.X(), outputTornado.Y());
        ComputeKernels.renderTrack(validationOutput, input);
        for (int i = 0; i < validationOutput.Y(); i++) {
            for (int j = 0; j < validationOutput.X(); j++) {
                if ((validationOutput.get(i, j).getX() != outputTornado.get(i, j).getX()) || (validationOutput.get(i, j).getY() != outputTornado.get(i, j).getY())
                        || (validationOutput.get(i, j).getZ() != outputTornado.get(i, j).getZ())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean validate() {
        ImageByte3 outputTornado = new ImageByte3(size, size);
        ImageFloat3 inputValidation = new ImageFloat3(size, size);
        Random r = new Random();
        for (int i = 0; i < inputValidation.X(); i++) {
            for (int j = 0; j < inputValidation.Y(); j++) {
                float value = (float) r.nextInt(10) * -1;
                inputValidation.set(i, j, new Float3(i, j, value));
            }
        }
        TaskSchedule s0 = new TaskSchedule("s0")//
                .task("t0", ComputeKernels::renderTrack, outputTornado, inputValidation) //
                .streamOut(outputTornado);

        return validate(inputValidation, outputTornado);
    }

    @Override
    public void benchmarkMethod() {
        s0.execute();
    }
}
