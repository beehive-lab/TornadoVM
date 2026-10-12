/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.kotlin.benchmarks

import uk.ac.manchester.tornado.api.GridScheduler
import uk.ac.manchester.tornado.api.TaskGraph
import uk.ac.manchester.tornado.api.TornadoExecutionPlan
import uk.ac.manchester.tornado.api.common.TornadoDevice
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner

/**
 * Reference driver: runs a Kotlin kernel sequentially on the host (a `parallelFor` loop outside
 * TornadoVM is an ordinary loop). It is reported as `java-reference` by the harness.
 */
class KotlinSequentialDriver(iterations: Int, private val init: () -> Unit, private val kernel: () -> Unit) : BenchmarkDriver(iterations.toLong()) {
    override fun setUp() = init()

    override fun runBenchmark(device: TornadoDevice?) = kernel()

    override fun validate(device: TornadoDevice?) = true
}

/**
 * Driver that runs a Kotlin kernel with TornadoVM. Subclasses create their data and task-graph in
 * [build], and compare the device results with a sequential run in [check].
 */
abstract class KotlinTornadoDriver(iterations: Int) : BenchmarkDriver(iterations.toLong()) {

    /** Allocates the data and returns the task-graph to benchmark. */
    abstract fun build(): TaskGraph

    /** Compares the results of the last device execution with the kernel run sequentially. */
    abstract fun check(): Boolean

    /** The device of the current benchmark run, for checks that execute their own task-graph. */
    protected var device: TornadoDevice? = null

    /** Optional grid for kernels that need one. */
    open fun grid(): GridScheduler? = null

    override fun setUp() {
        taskGraph = build()
        immutableTaskGraph = taskGraph.snapshot()
        executionPlan = TornadoExecutionPlan.of(immutableTaskGraph)
        grid()?.let { executionPlan.withGridScheduler(it) }
        executionPlan.withPreCompilation()
    }

    override fun runBenchmark(device: TornadoDevice?) {
        this.device = device
        executionResult = executionPlan.withDevice(device).execute()
    }

    override fun validate(device: TornadoDevice?): Boolean {
        runBenchmark(device)
        executionPlan.clearProfiles()
        val valid = check()
        println("Number validation: $valid")
        return valid
    }

    override fun tearDown() {
        executionPlan.resetDevice()
        super.tearDown()
    }
}

/**
 * A Kotlin benchmark on the tornado-benchmarks harness. It takes the same arguments as the Java
 * benchmark of the same name (`iterations size`), and reports itself as `kotlin-<name>`.
 */
class KotlinBenchmark(
    private val benchmarkName: String,
    private val defaultIterations: Int,
    private val defaultSize: Int,
    private val sequentialDriver: (iterations: Int, size: Int) -> BenchmarkDriver,
    private val tornadoDriver: (iterations: Int, size: Int) -> BenchmarkDriver,
) : BenchmarkRunner() {

    private var size = 0

    override fun parseArgs(args: Array<String>) {
        if (args.size >= 2) {
            iterations = args[0].toInt()
            size = args[1].toInt()
        } else {
            iterations = defaultIterations
            size = defaultSize
        }
    }

    override fun getName() = "kotlin-$benchmarkName"

    override fun getIdString() = String.format("%s-%d-%d", name, iterations, size)

    override fun getConfigString() = "size=$size"

    override fun getJavaDriver() = sequentialDriver(iterations, size)

    override fun getTornadoDriver() = tornadoDriver(iterations, size)
}

/** Largest absolute difference between two arrays, for validation. */
fun maxAbsDifference(a: uk.ac.manchester.tornado.kotlin.api.TFloatArray, b: uk.ac.manchester.tornado.kotlin.api.TFloatArray): Float {
    var max = 0f
    for (i in 0 until a.size) {
        max = maxOf(max, Math.abs(a[i] - b[i]))
    }
    return max
}
