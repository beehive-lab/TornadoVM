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

import java.util.Random
import uk.ac.manchester.tornado.api.TaskGraph
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.TShortArray
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.toExecutionPlan

// The seven Kotlin benchmarks. Sizes, data and validation follow the Java benchmark drivers
// (uk.ac.manchester.tornado.benchmarks.<name>).

// ---------------------------------------------------------------- saxpy

fun saxpyBenchmark() = KotlinBenchmark("saxpy", 101, 16777216,
    { iterations, size ->
        lateinit var x: TFloatArray
        lateinit var y: TFloatArray
        KotlinSequentialDriver(iterations, { x = TFloatArray(size) { it.toFloat() }; y = TFloatArray(size) }, { saxpy(2f, x, y) })
    },
    { iterations, size -> SaxpyTornado(iterations, size) })

class SaxpyTornado(iterations: Int, private val size: Int) : KotlinTornadoDriver(iterations) {
    private val alpha = 2f
    private lateinit var x: TFloatArray
    private lateinit var y: TFloatArray

    override fun build(): TaskGraph {
        x = TFloatArray(size) { it.toFloat() }
        y = TFloatArray(size)
        return taskGraph("benchmark") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, x)
            task("saxpy", ::saxpy, alpha, x, y)
            transferToHost(DataTransferMode.EVERY_EXECUTION, y)
        }
    }

    override fun check(): Boolean {
        // y accumulates across executions: rebuild the expected value from a single run on fresh data.
        val xs = TFloatArray(size) { it.toFloat() }
        val expected = TFloatArray(size)
        saxpy(alpha, xs, expected)
        val actual = TFloatArray(size)
        taskGraph("check") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, xs)
            task("saxpy", ::saxpy, alpha, xs, actual)
            transferToHost(DataTransferMode.EVERY_EXECUTION, actual)
        }.toExecutionPlan().use { it.withDevice(device).execute() }
        return maxAbsDifference(expected, actual) < 0.01f
    }
}

// ---------------------------------------------------------------- sgemm

fun sgemmBenchmark() = KotlinBenchmark("sgemm", 20, 512,
    { iterations, size ->
        lateinit var a: TFloatArray
        lateinit var b: TFloatArray
        lateinit var c: TFloatArray
        KotlinSequentialDriver(iterations, {
            a = sgemmA(size); b = sgemmB(size); c = TFloatArray(size * size)
        }, { sgemm(size, size, size, a, b, c) })
    },
    { iterations, size -> SgemmTornado(iterations, size) })

private fun sgemmA(n: Int) = TFloatArray(n * n).also { a -> for (i in 0 until n) a[i * (n + 1)] = 1f }

private fun sgemmB(n: Int): TFloatArray {
    val random = Random()
    return TFloatArray(n * n) { random.nextFloat() }
}

class SgemmTornado(iterations: Int, private val n: Int) : KotlinTornadoDriver(iterations) {
    private lateinit var a: TFloatArray
    private lateinit var b: TFloatArray
    private lateinit var c: TFloatArray

    override fun build(): TaskGraph {
        a = sgemmA(n)
        b = sgemmB(n)
        c = TFloatArray(n * n)
        return taskGraph("benchmark") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
            task("sgemm", ::sgemm, n, n, n, a, b, c)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }
    }

    override fun check(): Boolean {
        val expected = TFloatArray(n * n)
        sgemm(n, n, n, a, b, expected)
        return maxAbsDifference(expected, c) < 0.01f
    }
}

// ---------------------------------------------------------------- blackscholes

fun blackscholesBenchmark() = KotlinBenchmark("blackscholes", 100, 8192,
    { iterations, size ->
        lateinit var rand: TFloatArray
        lateinit var put: TFloatArray
        lateinit var call: TFloatArray
        KotlinSequentialDriver(iterations, {
            rand = TFloatArray(size) { (it * 1.0f) / size }; put = TFloatArray(size); call = TFloatArray(size)
        }, { blackscholes(rand, put, call) })
    },
    { iterations, size -> BlackScholesTornado(iterations, size) })

class BlackScholesTornado(iterations: Int, private val size: Int) : KotlinTornadoDriver(iterations) {
    private lateinit var rand: TFloatArray
    private lateinit var put: TFloatArray
    private lateinit var call: TFloatArray

    override fun build(): TaskGraph {
        rand = TFloatArray(size) { (it * 1.0f) / size }
        put = TFloatArray(size)
        call = TFloatArray(size)
        return taskGraph("benchmark") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, rand)
            task("t0", ::blackscholes, rand, put, call)
            transferToHost(DataTransferMode.EVERY_EXECUTION, put, call)
        }
    }

    override fun check(): Boolean {
        val putSeq = TFloatArray(size)
        val callSeq = TFloatArray(size)
        blackscholes(rand, putSeq, callSeq)
        return maxAbsDifference(putSeq, put) < 0.01f && maxAbsDifference(callSeq, call) < 0.01f
    }
}

// ---------------------------------------------------------------- nbody

private const val DEL_T = 0.005f
private const val ESP_SQR = 500.0f

private fun nbodyPositions(numBodies: Int): TFloatArray {
    val random = Random(7)
    return TFloatArray(numBodies * 4) { random.nextFloat() }
}

fun nbodyBenchmark() = KotlinBenchmark("nbody", 51, 16384,
    { iterations, size ->
        lateinit var pos: TFloatArray
        lateinit var vel: TFloatArray
        KotlinSequentialDriver(iterations, { pos = nbodyPositions(size); vel = TFloatArray(size * 4) }, { nBody(size, pos, vel, DEL_T, ESP_SQR) })
    },
    { iterations, size -> NBodyTornado(iterations, size) })

class NBodyTornado(iterations: Int, private val numBodies: Int) : KotlinTornadoDriver(iterations) {
    private lateinit var pos: TFloatArray
    private lateinit var vel: TFloatArray

    override fun build(): TaskGraph {
        pos = nbodyPositions(numBodies)
        vel = TFloatArray(numBodies * 4)
        return taskGraph("benchmark") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, vel, pos)
            task("t0", ::nBody, numBodies, pos, vel, DEL_T, ESP_SQR)
        }
    }

    override fun check(): Boolean {
        // The kernel updates the positions in place: compare one step from the same initial state.
        val posDevice = nbodyPositions(numBodies)
        val velDevice = TFloatArray(numBodies * 4)
        val posHost = nbodyPositions(numBodies)
        val velHost = TFloatArray(numBodies * 4)
        taskGraph("check") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, velDevice, posDevice)
            task("t0", ::nBody, numBodies, posDevice, velDevice, DEL_T, ESP_SQR)
            transferToHost(DataTransferMode.EVERY_EXECUTION, posDevice, velDevice)
        }.toExecutionPlan().use { it.withDevice(device).execute() }
        nBody(numBodies, posHost, velHost, DEL_T, ESP_SQR)
        return maxAbsDifference(posHost, posDevice) < 0.1f && maxAbsDifference(velHost, velDevice) < 0.1f
    }
}

// ---------------------------------------------------------------- mandelbrot

fun mandelbrotBenchmark() = KotlinBenchmark("mandelbrot", 131, 512,
    { iterations, size ->
        lateinit var output: TShortArray
        KotlinSequentialDriver(iterations, { output = TShortArray(size * size) }, { mandelbrot(size, output) })
    },
    { iterations, size -> MandelbrotTornado(iterations, size) })

class MandelbrotTornado(iterations: Int, private val size: Int) : KotlinTornadoDriver(iterations) {
    private lateinit var output: TShortArray

    override fun build(): TaskGraph {
        output = TShortArray(size * size)
        return taskGraph("benchmark") {
            task("t0", ::mandelbrot, size, output)
            transferToHost(DataTransferMode.EVERY_EXECUTION, output)
        }
    }

    override fun check(): Boolean {
        val expected = TShortArray(size * size)
        mandelbrot(size, expected)
        // Pixels on the boundary of the set can escape one iteration earlier or later on the device, whose
        // floating-point arithmetic differs slightly from the JVM's (the Java benchmark's exact check fails
        // for the same reason). Accept up to 1% differing pixels.
        var differing = 0
        for (i in 0 until size * size) {
            if (expected[i] != output[i]) {
                differing++
            }
        }
        println("Mandelbrot pixels differing from the sequential run: $differing of ${size * size}")
        return differing <= (size * size) / 100
    }
}

// ---------------------------------------------------------------- dft

private fun dftInput(size: Int, imaginary: Boolean) = TFloatArray(size) { if (imaginary) 0f else 1f / (it + 2) }

fun dftBenchmark() = KotlinBenchmark("dft", 15, 8192,
    { iterations, size ->
        lateinit var inReal: TFloatArray
        lateinit var inImag: TFloatArray
        lateinit var outReal: TFloatArray
        lateinit var outImag: TFloatArray
        KotlinSequentialDriver(iterations, {
            inReal = dftInput(size, false); inImag = dftInput(size, true); outReal = TFloatArray(size); outImag = TFloatArray(size)
        }, { computeDFT(inReal, inImag, outReal, outImag) })
    },
    { iterations, size -> DFTTornado(iterations, size) })

class DFTTornado(iterations: Int, private val size: Int) : KotlinTornadoDriver(iterations) {
    private lateinit var inReal: TFloatArray
    private lateinit var inImag: TFloatArray
    private lateinit var outReal: TFloatArray
    private lateinit var outImag: TFloatArray

    override fun build(): TaskGraph {
        inReal = dftInput(size, false)
        inImag = dftInput(size, true)
        outReal = TFloatArray(size)
        outImag = TFloatArray(size)
        return taskGraph("benchmark") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, inReal, inImag)
            task("t0", ::computeDFT, inReal, inImag, outReal, outImag)
            transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag)
        }
    }

    override fun check(): Boolean {
        val expectedReal = TFloatArray(size)
        val expectedImag = TFloatArray(size)
        computeDFT(inReal, inImag, expectedReal, expectedImag)
        return maxAbsDifference(expectedReal, outReal) < 0.01f && maxAbsDifference(expectedImag, outImag) < 0.01f
    }
}

// ---------------------------------------------------------------- montecarlo

fun monteCarloBenchmark() = KotlinBenchmark("montecarlo", 41, 8192,
    { iterations, size ->
        lateinit var output: TFloatArray
        KotlinSequentialDriver(iterations, { output = TFloatArray(size) }, { monteCarlo(output, size) })
    },
    { iterations, size -> MonteCarloTornado(iterations, size) })

class MonteCarloTornado(iterations: Int, private val size: Int) : KotlinTornadoDriver(iterations) {
    private lateinit var output: TFloatArray

    override fun build(): TaskGraph {
        output = TFloatArray(size)
        return taskGraph("benchmark") {
            task("montecarlo", ::monteCarlo, output, size)
            transferToHost(DataTransferMode.EVERY_EXECUTION, output)
        }
    }

    override fun check(): Boolean {
        val expected = TFloatArray(size)
        monteCarlo(expected, size)
        return maxAbsDifference(expected, output) < 0.01f
    }
}
