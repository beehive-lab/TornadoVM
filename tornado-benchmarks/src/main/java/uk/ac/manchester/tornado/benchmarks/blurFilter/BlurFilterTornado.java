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
package uk.ac.manchester.tornado.benchmarks.blurFilter;

import java.util.Random;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner blurFilter
 * </code>
 */
public class BlurFilterTornado extends BenchmarkDriver {

    private int size;
    public static final int FILTER_WIDTH = 31;
    IntArray redChannel;
    IntArray greenChannel;
    IntArray blueChannel;
    IntArray alphaChannel;
    IntArray redFilter;
    IntArray greenFilter;
    IntArray blueFilter;
    FloatArray filter;

    public BlurFilterTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        int w = size;
        int h = size;

        redChannel = new IntArray(w * h);
        greenChannel = new IntArray(w * h);
        blueChannel = new IntArray(w * h);
        alphaChannel = new IntArray(w * h);

        greenFilter = new IntArray(w * h);
        redFilter = new IntArray(w * h);
        blueFilter = new IntArray(w * h);

        filter = new FloatArray(w * h);
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                filter.set(i * h + j, 1.f / (FILTER_WIDTH * FILTER_WIDTH));
            }
        }

        Random r = new Random();
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int rgb = r.nextInt(255);
                alphaChannel.set(i * h + j, (rgb >> 24) & 0xFF);
                redChannel.set(i * h + j, (rgb >> 16) & 0xFF);
                greenChannel.set(i * h + j, (rgb >> 8) & 0xFF);
                blueChannel.set(i * h + j, (rgb & 0xFF));
            }
        }

        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, redChannel, greenChannel, blueChannel, filter) //
                .task("blurRed", ComputeKernels::channelConvolution, redChannel, redFilter, w, h, filter, FILTER_WIDTH) //
                .task("blurGreen", ComputeKernels::channelConvolution, greenChannel, greenFilter, w, h, filter, FILTER_WIDTH) //
                .task("blurBlue", ComputeKernels::channelConvolution, blueChannel, blueFilter, w, h, filter, FILTER_WIDTH) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, redFilter, greenFilter, blueFilter);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDefaultScheduler() //
                .withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        redChannel = null;
        greenChannel = null;
        blueChannel = null;
        alphaChannel = null;
        greenFilter = null;
        redFilter = null;
        blueFilter = null;
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean valid = true;
        int w = size;
        int h = size;

        IntArray redChannel = new IntArray(w * h);
        IntArray greenChannel = new IntArray(w * h);
        IntArray blueChannel = new IntArray(w * h);
        IntArray alphaChannel = new IntArray(w * h);

        IntArray greenFilter = new IntArray(w * h);
        IntArray redFilter = new IntArray(w * h);
        IntArray blueFilter = new IntArray(w * h);

        IntArray greenFilterSeq = new IntArray(w * h);
        IntArray redFilterSeq = new IntArray(w * h);
        IntArray blueFilterSeq = new IntArray(w * h);

        FloatArray filter = new FloatArray(w * h);
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                filter.set(i * h + j, 1.f / (FILTER_WIDTH * FILTER_WIDTH));
            }
        }

        Random r = new Random();
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int rgb = r.nextInt(255);
                alphaChannel.set(i * h + j, (rgb >> 24) & 0xFF);
                redChannel.set(i * h + j, (rgb >> 16) & 0xFF);
                greenChannel.set(i * h + j, (rgb >> 8) & 0xFF);
                blueChannel.set(i * h + j, (rgb & 0xFF));
            }
        }

        TaskGraph parallelFilter = new TaskGraph("blur") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, redChannel, greenChannel, blueChannel, filter) //
                .task("red", ComputeKernels::channelConvolution, redChannel, redFilter, w, h, filter, FILTER_WIDTH) //
                .task("green", ComputeKernels::channelConvolution, greenChannel, greenFilter, w, h, filter, FILTER_WIDTH) //
                .task("blue", ComputeKernels::channelConvolution, blueChannel, blueFilter, w, h, filter, FILTER_WIDTH) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, redFilter, greenFilter, blueFilter);

        ImmutableTaskGraph immutableTaskGraph1 = parallelFilter.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph1);
        executor.withDevice(device).withDefaultScheduler().execute();

        // Sequential
        ComputeKernels.channelConvolution(redChannel, redFilterSeq, size, size, filter, FILTER_WIDTH);
        ComputeKernels.channelConvolution(greenChannel, greenFilterSeq, size, size, filter, FILTER_WIDTH);
        ComputeKernels.channelConvolution(blueChannel, blueFilterSeq, size, size, filter, FILTER_WIDTH);

        for (int i = 0; i < redFilter.getSize(); i++) {
            if (redFilter.get(i) != redFilterSeq.get(i)) {
                valid = false;
                break;
            }
            if (greenFilter.get(i) != greenFilterSeq.get(i)) {
                valid = false;
                break;
            }
            if (blueFilter.get(i) != blueFilterSeq.get(i)) {
                valid = false;
                break;
            }
        }

        return valid;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }
}
