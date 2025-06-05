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
package uk.ac.manchester.tornado.benchmarks.spmv;

import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.spmv;
import static uk.ac.manchester.tornado.benchmarks.spmv.Benchmark.initData;

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
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;
import uk.ac.manchester.tornado.matrix.SparseMatrixUtils;

/**
 * <p>
 * How to run in isolation?
 * </p>
 * <code>
 * tornado -jar tornado-benchmarks/target/jmhbenchmarks.jar uk.ac.manchester.tornado.benchmarks.spmv.JMHSpmv
 * </code>
 */
public class JMHSpmv {

    @State(Scope.Thread)
    public static class BenchmarkSetup {
        private SparseMatrixUtils.CSRMatrix<FloatArray> matrix;
        private FloatArray v;
        private FloatArray y;
        private TornadoExecutionPlan executor;

        @Setup(Level.Trial)
        public void doSetup() {
            String path = System.getProperty("spmv.matrix", "/bcsstk32.mtx");
            matrix = SparseMatrixUtils.loadMatrixF(uk.ac.manchester.tornado.benchmarks.spmv.Benchmark.class.getResourceAsStream(path));
            v = new FloatArray(matrix.size);
            y = new FloatArray(matrix.size);
            initData(v);
            TaskGraph taskGraph = new TaskGraph("benchmark") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix.vals, matrix.cols, matrix.rows, v, y) //
                    .task("spmv", LinearAlgebraArrays::spmv, matrix.vals, matrix.cols, matrix.rows, v, matrix.size, y) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
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
    public void spmvJava(BenchmarkSetup state) {
        SparseMatrixUtils.CSRMatrix<FloatArray> matrix = state.matrix;
        spmv(matrix.vals, matrix.cols, matrix.rows, state.v, matrix.size, state.y);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 30, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Fork(1)
    public void spmvTornado(BenchmarkSetup state, Blackhole blackhole) {
        TornadoExecutionPlan executor = state.executor;
        executor.execute();
        blackhole.consume(executor);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder() //
                .include(JMHSpmv.class.getName() + ".*") //
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
