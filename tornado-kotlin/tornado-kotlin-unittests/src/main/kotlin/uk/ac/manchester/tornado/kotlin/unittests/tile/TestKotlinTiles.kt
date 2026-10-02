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
package uk.ac.manchester.tornado.kotlin.unittests.tile

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.ac.manchester.tornado.api.GridScheduler
import uk.ac.manchester.tornado.api.TaskGraph
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceTileNotSupported
import uk.ac.manchester.tornado.api.tile.TileContext
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.div
import uk.ac.manchester.tornado.kotlin.api.exp
import uk.ac.manchester.tornado.kotlin.api.fma
import uk.ac.manchester.tornado.kotlin.api.gt
import uk.ac.manchester.tornado.kotlin.api.max
import uk.ac.manchester.tornado.kotlin.api.maximum
import uk.ac.manchester.tornado.kotlin.api.minus
import uk.ac.manchester.tornado.kotlin.api.plus
import uk.ac.manchester.tornado.kotlin.api.runOnHost
import uk.ac.manchester.tornado.kotlin.api.select
import uk.ac.manchester.tornado.kotlin.api.sqrt
import uk.ac.manchester.tornado.kotlin.api.sum
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.tileGrid
import uk.ac.manchester.tornado.kotlin.api.times
import uk.ac.manchester.tornado.kotlin.api.toExecutionPlan
import uk.ac.manchester.tornado.kotlin.api.unaryMinus
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported

// Tile shapes are compile-time constants.
const val TILE = 256
const val WIDTH = 256

/** c = a + b */
fun tileAdd(tc: TileContext, a: TFloatArray, b: TFloatArray, c: TFloatArray, n: Int) = with(tc) {
    val block = bidX()
    partition(view(c, n), TILE).store(partition(view(a, n), TILE).load(block) + partition(view(b, n), TILE).load(block), block)
}

/** c = 2a*b - a */
fun tileArithmetic(tc: TileContext, a: TFloatArray, b: TFloatArray, c: TFloatArray, n: Int) = with(tc) {
    val block = bidX()
    val x = partition(view(a, n), TILE).load(block)
    val y = partition(view(b, n), TILE).load(block)
    partition(view(c, n), TILE).store(x * 2.0 * y - x, block)
}

/** c = max(a, b) where a > 0, else -sqrt(b) + fma(a, a, b) */
fun tileMathAndSelect(tc: TileContext, a: TFloatArray, b: TFloatArray, c: TFloatArray, n: Int) = with(tc) {
    val block = bidX()
    val x = partition(view(a, n), TILE).load(block)
    val y = partition(view(b, n), TILE).load(block)
    val result = (x gt 0.0).select(maximum(x, y), -sqrt(y) + fma(x, x, y))
    partition(view(c, n), TILE).store(result, block)
}

/** Row softmax, one row per tile block. */
fun tileSoftmax(tc: TileContext, input: TFloatArray, output: TFloatArray, rows: Int) = with(tc) {
    val row = bidX()
    val values = partition(view(input, rows, WIDTH), 1, WIDTH).load(row, 0)
    val shifted = exp(values - values.max(1))
    partition(view(output, rows, WIDTH), 1, WIDTH).store(shifted / shifted.sum(1), row, 0)
}

/**
 * Tile kernels written with the Kotlin tile API (operators and functions on Tile inside
 * `with(tc)`).
 *
 * The `*OnHost` tests run the kernels through the JVM implementation of the tile operations, on
 * any backend, and check that the Kotlin operators map to the right TileContext operations. The
 * `*OnDevice` tests compile them with CUDA Tile; they need the CUDA backend and are reported as
 * [UNSUPPORTED] elsewhere, like the Java tile tests.
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.kotlin.unittests.tile.TestKotlinTiles
 * </code>
 */
class TestKotlinTiles : TornadoTestBase() {

    private val n = 4 * TILE
    private val a = TFloatArray(n) { (it % 17) - 8f }
    private val b = TFloatArray(n) { (it % 5) + 1f }

    private fun expectedAdd(i: Int) = a[i] + b[i]
    private fun expectedArithmetic(i: Int) = 2f * a[i] * b[i] - a[i]
    private fun expectedMathAndSelect(i: Int) = if (a[i] > 0f) maxOf(a[i], b[i]) else -Math.sqrt(b[i].toDouble()).toFloat() + (a[i] * a[i] + b[i])

    private fun softmaxInput(rows: Int) = TFloatArray(rows * WIDTH) { ((it * 37) % 101) / 25f }

    private fun checkSoftmax(input: TFloatArray, output: TFloatArray, rows: Int) {
        for (row in 0 until rows) {
            var max = Float.NEGATIVE_INFINITY
            for (i in 0 until WIDTH) max = maxOf(max, input[row * WIDTH + i])
            var total = 0.0
            for (i in 0 until WIDTH) total += Math.exp((input[row * WIDTH + i] - max).toDouble())
            for (i in 0 until WIDTH) {
                assertEquals(Math.exp((input[row * WIDTH + i] - max).toDouble()) / total, output[row * WIDTH + i].toDouble(), 1e-5)
            }
        }
    }

    // ------------------------------------------------------------------ JVM path, any backend

    @Test
    fun testAddOnHost() {
        val c = TFloatArray(n)
        TileContext().runOnHost(n / TILE) { tileAdd(it, a, b, c, n) }
        for (i in 0 until n) assertEquals(expectedAdd(i), c[i], 1e-5f)
    }

    @Test
    fun testArithmeticOnHost() {
        val c = TFloatArray(n)
        TileContext().runOnHost(n / TILE) { tileArithmetic(it, a, b, c, n) }
        for (i in 0 until n) assertEquals(expectedArithmetic(i), c[i], 1e-4f)
    }

    @Test
    fun testMathAndSelectOnHost() {
        val c = TFloatArray(n)
        TileContext().runOnHost(n / TILE) { tileMathAndSelect(it, a, b, c, n) }
        for (i in 0 until n) assertEquals(expectedMathAndSelect(i), c[i], 1e-4f)
    }

    @Test
    fun testSoftmaxOnHost() {
        val rows = 8
        val input = softmaxInput(rows)
        val output = TFloatArray(rows * WIDTH)
        TileContext().runOnHost(rows) { tileSoftmax(it, input, output, rows) }
        checkSoftmax(input, output, rows)
    }

    // ------------------------------------------------------------------ CUDA Tile

    private fun requireCudaTile() {
        val backend = getTornadoRuntime().defaultDevice.tornadoVMBackend
        if (backend != TornadoVMBackendType.CUDA) {
            throw TornadoVMCUDANotSupported("CUDA Tile kernels require the CUDA backend (default device is $backend)")
        }
    }

    /** As the Java tile tests: a host that cannot run CUDA Tile reports UNSUPPORTED, not a failure. */
    private fun execute(graph: TaskGraph, grid: GridScheduler) {
        try {
            graph.toExecutionPlan().use { it.withGridScheduler(grid).execute() }
        } catch (e: TornadoDeviceTileNotSupported) {
            throw TornadoVMCUDANotSupported(e.message)
        } catch (e: TornadoBailoutRuntimeException) {
            if (e.message.toString().contains("device kernel image is invalid")) {
                throw TornadoVMCUDANotSupported("Loading a CUDA Tile cubin requires driver R580 or newer: ${e.message}")
            }
            throw e
        }
    }

    @Test
    fun testAddOnDevice() {
        requireCudaTile()
        val c = TFloatArray(n)
        val tc = TileContext()
        execute(taskGraph("s0") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
            task("t0", ::tileAdd, tc, a, b, c, n)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }, tileGrid("s0.t0", n / TILE))
        for (i in 0 until n) assertEquals(expectedAdd(i), c[i], 1e-5f)
    }

    @Test
    fun testArithmeticOnDevice() {
        requireCudaTile()
        val c = TFloatArray(n)
        val tc = TileContext()
        execute(taskGraph("s0") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
            task("t0", ::tileArithmetic, tc, a, b, c, n)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }, tileGrid("s0.t0", n / TILE))
        for (i in 0 until n) assertEquals(expectedArithmetic(i), c[i], 1e-4f)
    }

    @Test
    fun testMathAndSelectOnDevice() {
        requireCudaTile()
        val c = TFloatArray(n)
        val tc = TileContext()
        execute(taskGraph("s0") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
            task("t0", ::tileMathAndSelect, tc, a, b, c, n)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }, tileGrid("s0.t0", n / TILE))
        for (i in 0 until n) assertEquals(expectedMathAndSelect(i), c[i], 1e-3f)
    }

    @Test
    fun testSoftmaxOnDevice() {
        requireCudaTile()
        val rows = 64
        val input = softmaxInput(rows)
        val output = TFloatArray(rows * WIDTH)
        val tc = TileContext()
        execute(taskGraph("s0") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
            task("t0", ::tileSoftmax, tc, input, output, rows)
            transferToHost(DataTransferMode.EVERY_EXECUTION, output)
        }, tileGrid("s0.t0", rows))
        checkSoftmax(input, output, rows)
    }
}
