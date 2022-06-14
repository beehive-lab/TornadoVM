/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

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

        ts = new TaskSchedule("benchmark") //
                .task("juliaSet", GraphicsKernels::juliaSetTornado, size, hue, brightness) //
                .streamOut(hue, brightness);
        ts.warmup();
    }

    @Override
    public void tearDown() {
        ts.dumpProfiles();
        hue = null;
        brightness = null;
        ts.getDevice().reset();
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        final float[] hueSeq = new float[size * size];
        final float[] brightnessSeq = new float[size * size];

        benchmarkMethod(device);
        ts.clearProfiles();

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
    public TaskSchedule getTaskSchedule() {
        return ts;
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        ts.mapAllTo(device);
        ts.execute();
    }
}
