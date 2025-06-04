/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.stencil;

import static uk.ac.manchester.tornado.benchmarks.stencil.Stencil.copy;
import static uk.ac.manchester.tornado.benchmarks.stencil.Stencil.stencil3d;

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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * <p>
 * How to run in isolation?
 * </p>
 * <code>
 * tornado -jar tornado-benchmarks/target/jmhbenchmarks.jar uk.ac.manchester.tornado.benchmarks.stencil.JMHStencil
 * </code>
 */
public class JMHStencil {
    @State(Scope.Thread)
    public static class BenchmarkSetup {

        private final int size = Integer.parseInt(System.getProperty("x", "1048576"));
        int sz;
        int n;
        private final float FAC = 1 / 26;
        private FloatArray a0;
        private FloatArray a1;
        private FloatArray ainit;

        private TornadoExecutionPlan executor;

        @Setup(Level.Trial)
        public void doSetup() {
            sz = (int) Math.cbrt(size / 8) / 2;
            n = sz - 2;
            a0 = new FloatArray(sz * sz * sz);
            a1 = new FloatArray(sz * sz * sz);
            ainit = new FloatArray(sz * sz * sz);

            a1.init(0);

            final Random rand = new Random(7);
            for (int i = 1; i < n + 1; i++) {
                for (int j = 1; j < n + 1; j++) {
                    for (int k = 1; k < n + 1; k++) {
                        ainit.set((i * sz * sz) + (j * sz) + k, rand.nextFloat());
                    }
                }
            }
            copy(sz, ainit, a0);
            TaskGraph taskGraph = new TaskGraph("benchmark") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a0, a1) //
                    .task("stencil", Stencil::stencil3d, n, sz, a0, a1, FAC) //
                    .task("copy", Stencil::copy, sz, a1, a0) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, a0);

            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            executor = new TornadoExecutionPlan(immutableTaskGraph);
            executor.withPreCompilation();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 60, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    public void stencilJava(BenchmarkSetup state) {
        stencil3d(state.n, state.sz, state.a0, state.a1, state.FAC);
        copy(state.sz, state.a0, state.a1);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 30, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    public void stencilTornado(BenchmarkSetup state, Blackhole blackhole) {
        TornadoExecutionPlan executor = state.executor;
        executor.execute();
        blackhole.consume(executor);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder() //
                .include(JMHStencil.class.getName() + ".*") //
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
