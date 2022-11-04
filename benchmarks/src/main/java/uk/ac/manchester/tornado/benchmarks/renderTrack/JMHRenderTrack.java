/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.renderTrack;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageByte3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run in isolation?
 * </p>
 * <code>
 *    tornado -jar benchmarks/target/jmhbenchmarks.jar uk.ac.manchester.tornado.benchmarks.renderTrack.JMHRenderTrack
 * </code>
 */
public class JMHRenderTrack {
    @State(Scope.Thread)
    public static class BenchmarkSetup {

        private final int size = Integer.parseInt(System.getProperty("x", "8192"));
        private ImageFloat3 input;
        private ImageByte3 output;

        private TaskGraph taskGraph;

        @Setup(Level.Trial)
        public void doSetup() {
            output = new ImageByte3(size, size);
            input = new ImageFloat3(size, size);
            Random r = new Random();
            for (int i = 0; i < input.X(); i++) {
                for (int j = 0; j < input.Y(); j++) {
                    float value = (float) r.nextInt(10) * -1;
                    input.set(i, j, new Float3(i, j, value));
                }
            }
            taskGraph = new TaskGraph("s0")//
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                    .task("t0", ComputeKernels::renderTrack, output, input) //
                    .transferToHost(output);
            taskGraph.warmup();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 60, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    public void renderTrackJava(BenchmarkSetup state) {
        ComputeKernels.renderTrack(state.output, state.input);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 30, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    public void renderTrackTornado(BenchmarkSetup state, Blackhole blackhole) {
        TaskGraph taskGraph = state.taskGraph;
        taskGraph.execute();
        blackhole.consume(taskGraph);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder() //
                .include(JMHRenderTrack.class.getName() + ".*") //
                .mode(Mode.AverageTime) //
                .timeUnit(TimeUnit.NANOSECONDS) //
                .warmupTime(TimeValue.seconds(60)) //
                .warmupIterations(2) //
                .measurementTime(TimeValue.seconds(30)) //
                .measurementIterations(5) //
                .forks(1) //
                .build();
        new Runner(opt).run();
    }
}
