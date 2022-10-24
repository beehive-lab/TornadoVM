/*
 * Copyright (c) 2021-2022 APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner juliaset
 * </code>
 */
public class JuliaSetTornado extends BenchmarkDriver {

    private final int size;
    private final int iterations;

    private static float[] hue;
    private static float[] brightness;

    public JuliaSetTornado(int iterations, int size) {
        super(iterations);
        this.iterations = iterations;
        this.size = size;
    }

    @Override
    public void setUp() {
        hue = new float[size * size];
        brightness = new float[size * size];

        taskGraph = new TaskGraph("benchmark") //
                .task("juliaSet", GraphicsKernels::juliaSetTornado, size, hue, brightness) //
                .transferToHost(hue, brightness);
        taskGraph.warmup();
    }

    @Override
    public void tearDown() {
        taskGraph.dumpProfiles();
        hue = null;
        brightness = null;
        taskGraph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        final float[] hueSeq = new float[size * size];
        final float[] brightnessSeq = new float[size * size];

        benchmarkMethod(device);
        taskGraph.clearProfiles();

        GraphicsKernels.juliaSetTornado(size, hueSeq, brightnessSeq);

        boolean isCorrect = true;
        float delta = 0.01f;
        for (int i = 0; i < hueSeq.length; i++) {
            if (Math.abs(hueSeq[i] - hue[i]) > delta) {
                isCorrect = false;
                break;
            }
            if (Math.abs(brightnessSeq[i] - brightness[i]) > delta) {
                isCorrect = false;
                break;
            }
        }
        return isCorrect;
    }

    @Override
    public TaskGraph getTaskSchedule() {
        return taskGraph;
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        taskGraph.mapAllTo(device);
        taskGraph.execute();
    }
}
