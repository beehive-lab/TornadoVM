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
package uk.ac.manchester.tornado.benchmarks.addImage;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat4;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

/**
 * <p>
 * How to run in isolation?
 * </p>
 * <code>
 *    tornado -jar benchmarks/target/jmhbenchmarks.jar uk.ac.manchester.tornado.benchmarks.addImage.JMHAddImage
 * </code>
 */
public class JMHAddImage {

    @State(Scope.Thread)
    public static class BenchmarkSetup {

        int numElementsX = Integer.parseInt(System.getProperty("x", "2048"));
        int numElementsY = Integer.parseInt(System.getProperty("y", "2048"));
        TaskGraph taskGraph;

        ImageFloat4 a;
        ImageFloat4 b;
        ImageFloat4 c;

        @Setup(Level.Trial)
        public void doSetup() {
            a = new ImageFloat4(numElementsX, numElementsY);
            b = new ImageFloat4(numElementsX, numElementsY);
            c = new ImageFloat4(numElementsX, numElementsY);

            Random r = new Random();
            for (int j = 0; j < numElementsY; j++) {
                for (int i = 0; i < numElementsX; i++) {
                    float[] ra = new float[4];
                    IntStream.range(0, ra.length).forEach(x -> ra[x] = r.nextFloat());
                    float[] rb = new float[4];
                    IntStream.range(0, rb.length).forEach(x -> rb[x] = r.nextFloat());
                    a.set(i, j, new Float4(ra));
                    b.set(i, j, new Float4(rb));
                }
            }
            taskGraph = new TaskGraph("benchmark") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                    .task("addImage", GraphicsKernels::addImage, a, b, c) //
                    .transferToHost(c);
            taskGraph.warmup();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 60, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    public void addImageJava(BenchmarkSetup state) {
        GraphicsKernels.addImage(state.a, state.b, state.c);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 30, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    public void addImageTornado(BenchmarkSetup state, Blackhole blackhole) {
        TaskGraph taskGraph = state.taskGraph;
        taskGraph.execute();
        blackhole.consume(taskGraph);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder() //
                .include(JMHAddImage.class.getName() + ".*") //
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
