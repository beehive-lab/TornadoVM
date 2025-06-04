/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.benchmarks.renderTrack;

import java.util.Random;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner renderTrack
 * </code>
 */
public class RenderTrackTornado extends BenchmarkDriver {

    private int size;
    private ImageFloat3 input;
    private ImageByte3 output;

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
        taskGraph = new TaskGraph("benchmark")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("renderTrack", ComputeKernels::renderTrack, output, input) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        input = null;
        output = null;
        executionPlan.resetDevice();
        super.tearDown();
    }

    private static boolean validate(ImageFloat3 input, ImageByte3 outputTornado) {
        ImageByte3 validationOutput = new ImageByte3(outputTornado.X(), outputTornado.Y());
        ComputeKernels.renderTrack(validationOutput, input);
        for (int i = 0; i < validationOutput.Y(); i++) {
            for (int j = 0; j < validationOutput.X(); j++) {
                if ((validationOutput.get(i, j).getX() != outputTornado.get(i, j).getX()) || (validationOutput.get(i, j).getY() != outputTornado.get(i, j).getY()) || (validationOutput.get(i, j)
                        .getZ() != outputTornado.get(i, j).getZ())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean validate(TornadoDevice device) {
        ImageByte3 outputTornado = new ImageByte3(size, size);
        ImageFloat3 inputValidation = new ImageFloat3(size, size);
        Random r = new Random();
        for (int i = 0; i < inputValidation.X(); i++) {
            for (int j = 0; j < inputValidation.Y(); j++) {
                float value = (float) r.nextInt(10) * -1;
                inputValidation.set(i, j, new Float3(i, j, value));
            }
        }
        TaskGraph s0 = new TaskGraph("s0")//
                .task("t0", ComputeKernels::renderTrack, outputTornado, inputValidation) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputTornado);

        ImmutableTaskGraph immutableTaskGraph = s0.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.withDevice(device).execute();

        return validate(inputValidation, outputTornado);
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }
}
