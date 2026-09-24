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
package uk.ac.manchester.tornado.kotlin.unittests.loops

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.ac.manchester.tornado.api.annotations.Reduce
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.api.math.TornadoMath
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.TIntArray
import uk.ac.manchester.tornado.kotlin.api.parallelFor
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.toExecutionPlan
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase

fun vectorAdd(a: TFloatArray, b: TFloatArray, c: TFloatArray) {
    parallelFor(0, c.size) { i ->
        c[i] = a[i] + b[i]
    }
}

// As with @Parallel loops in Java, the start of a parallel loop must be a constant.
const val FROM = 100

fun offsetLoop(a: TIntArray) {
    parallelFor(FROM, a.size) { i ->
        a[i] = i * 2
    }
}

fun matrixMultiply(a: TFloatArray, b: TFloatArray, c: TFloatArray, n: Int) {
    parallelFor(0, n) { i ->
        parallelFor(0, n) { j ->
            var sum = 0f
            for (k in 0 until n) {
                sum += a[i * n + k] * b[k * n + j]
            }
            c[i * n + j] = sum
        }
    }
}

fun index3D(out: TIntArray, nx: Int, ny: Int, nz: Int) {
    parallelFor(0, nz) { z ->
        parallelFor(0, ny) { y ->
            parallelFor(0, nx) { x ->
                out[(z * ny + y) * nx + x] = z * 10000 + y * 100 + x
            }
        }
    }
}

fun saxpyWithMath(alpha: Float, x: TFloatArray, y: TFloatArray) {
    parallelFor(0, y.size) { i ->
        y[i] = alpha * x[i] + TornadoMath.sqrt(y[i])
    }
}

fun reduceSum(input: TFloatArray, @Reduce result: TFloatArray) {
    result[0] = 0f
    parallelFor(0, input.size) { i ->
        result[0] += input[i]
    }
}

/**
 * Tests for loop-parallel kernels written with the Kotlin API's `parallelFor`.
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.kotlin.unittests.loops.TestParallelFor
 * </code>
 */
class TestParallelFor : TornadoTestBase() {

    @Test
    fun testVectorAdd() {
        val n = 4096
        val a = TFloatArray(n) { it.toFloat() }
        val b = TFloatArray(n) { 0.5f }
        val c = TFloatArray(n)
        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
            task("t0", ::vectorAdd, a, b, c)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }
        graph.toExecutionPlan().use { it.execute() }
        for (i in 0 until n) {
            assertEquals(i + 0.5f, c[i], 0.0f)
        }
    }

    @Test
    fun testNonZeroStart() {
        val n = 1000
        val a = TIntArray(n)
        a.init(-1)
        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", ::offsetLoop, a)
            transferToHost(DataTransferMode.EVERY_EXECUTION, a)
        }
        graph.toExecutionPlan().use { it.execute() }
        for (i in 0 until n) {
            assertEquals(if (i < FROM) -1 else i * 2, a[i])
        }
    }

    @Test
    fun testMatrixMultiply2D() {
        val n = 64
        val a = TFloatArray(n * n) { (it % 7).toFloat() }
        val b = TFloatArray(n * n) { (it % 5).toFloat() }
        val c = TFloatArray(n * n)
        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
            task("t0", ::matrixMultiply, a, b, c, n)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }
        graph.toExecutionPlan().use { it.execute() }
        for (i in 0 until n) {
            for (j in 0 until n) {
                var expected = 0f
                for (k in 0 until n) {
                    expected += a[i * n + k] * b[k * n + j]
                }
                assertEquals(expected, c[i * n + j], 0.01f)
            }
        }
    }

    @Test
    fun testIndex3D() {
        val nx = 16
        val ny = 8
        val nz = 4
        val out = TIntArray(nx * ny * nz)
        val graph = taskGraph("s0") {
            task("t0", ::index3D, out, nx, ny, nz)
            transferToHost(DataTransferMode.EVERY_EXECUTION, out)
        }
        graph.toExecutionPlan().use { it.execute() }
        for (z in 0 until nz) {
            for (y in 0 until ny) {
                for (x in 0 until nx) {
                    assertEquals(z * 10000 + y * 100 + x, out[(z * ny + y) * nx + x])
                }
            }
        }
    }

    @Test
    fun testTornadoMathInParallelFor() {
        val n = 1024
        val x = TFloatArray(n) { it.toFloat() }
        val y = TFloatArray(n) { (it * it).toFloat() }
        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, x, y)
            task("t0", ::saxpyWithMath, 2f, x, y)
            transferToHost(DataTransferMode.EVERY_EXECUTION, y)
        }
        graph.toExecutionPlan().use { it.execute() }
        for (i in 0 until n) {
            assertEquals(3f * i, y[i], 0.01f)
        }
    }

    @Test
    fun testReduction() {
        val n = 8192
        val input = TFloatArray(n) { (it % 10).toFloat() }
        val result = TFloatArray(1)
        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
            task("t0", ::reduceSum, input, result)
            transferToHost(DataTransferMode.EVERY_EXECUTION, result)
        }
        graph.toExecutionPlan().use { it.execute() }
        var expected = 0f
        for (i in 0 until n) {
            expected += input[i]
        }
        assertEquals(expected, result[0], 0.1f)
    }
}
