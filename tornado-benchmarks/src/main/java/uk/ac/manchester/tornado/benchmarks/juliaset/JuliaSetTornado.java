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
package uk.ac.manchester.tornado.benchmarks.juliaset;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner juliaset
 * </code>
 */
public class JuliaSetTornado extends BenchmarkDriver {

    private final int size;
    private final int iterations;

    private static FloatArray hue;
    private static FloatArray brightness;

    public JuliaSetTornado(int iterations, int size) {
        super(iterations);
        this.iterations = iterations;
        this.size = size;
    }

    @Override
    public void setUp() {
        hue = new FloatArray(size * size);
        brightness = new FloatArray(size * size);

        taskGraph = new TaskGraph("benchmark") //
                .task("juliaSet", GraphicsKernels::juliaSetTornado, size, hue, brightness) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, hue, brightness);
        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        hue = null;
        brightness = null;
        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        final FloatArray hueSeq = new FloatArray(size * size);
        final FloatArray brightnessSeq = new FloatArray(size * size);

        runBenchmark(device);
        executionPlan.clearProfiles();

        GraphicsKernels.juliaSetTornado(size, hueSeq, brightnessSeq);

        boolean isCorrect = true;
        float delta = 0.01f;
        for (int i = 0; i < hueSeq.getSize(); i++) {
            if (Math.abs(hueSeq.get(i) - hue.get(i)) > delta) {
                isCorrect = false;
                break;
            }
            if (Math.abs(brightnessSeq.get(i) - brightness.get(i)) > delta) {
                isCorrect = false;
                break;
            }
        }
        return isCorrect;
    }

    @Override
    public TaskGraph getTaskGraph() {
        return taskGraph;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }
}
