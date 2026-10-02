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
package uk.ac.manchester.tornado.kotlin.unittests.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.ac.manchester.tornado.api.KernelContext
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.TIntArray
import uk.ac.manchester.tornado.kotlin.api.executionPlan
import uk.ac.manchester.tornado.kotlin.api.globalIdX
import uk.ac.manchester.tornado.kotlin.api.globalIdY
import uk.ac.manchester.tornado.kotlin.api.globalSizeX
import uk.ac.manchester.tornado.kotlin.api.gridScheduler
import uk.ac.manchester.tornado.kotlin.api.groupIdX
import uk.ac.manchester.tornado.kotlin.api.localIdX
import uk.ac.manchester.tornado.kotlin.api.localSizeX
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.tFloatArrayOf
import uk.ac.manchester.tornado.kotlin.api.toExecutionPlan
import uk.ac.manchester.tornado.kotlin.api.toFloatArray
import uk.ac.manchester.tornado.kotlin.api.toTornado
import uk.ac.manchester.tornado.kotlin.api.workerGrid
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase

// Kernels are top-level functions: they compile to static methods of TestKotlinApiKt, which is
// what TornadoVM requires of a task. Functions in a companion object would be instance methods.

private fun vectorAdd(context: KernelContext, a: TFloatArray, b: TFloatArray, c: TFloatArray) {
    val i = context.globalIdX
    c[i] = a[i] + b[i]
}

private fun matrixAdd(context: KernelContext, a: TFloatArray, b: TFloatArray, c: TFloatArray, width: Int) {
    val x = context.globalIdX
    val y = context.globalIdY
    c[y * width + x] = a[y * width + x] + b[y * width + x]
}

private fun globalSize(context: KernelContext, out: TIntArray) {
    out[context.globalIdX] = context.globalSizeX
}

private fun groupSum(context: KernelContext, input: TFloatArray, partial: TFloatArray) {
    val local = context.allocateFloatLocalArray(GROUP)
    val lid = context.localIdX
    local[lid] = input[context.globalIdX]
    var stride = context.localSizeX / 2
    while (stride > 0) {
        context.localBarrier()
        if (lid < stride) {
            local[lid] += local[lid + stride]
        }
        stride /= 2
    }
    if (lid == 0) {
        partial[context.groupIdX] = local[0]
    }
}

private const val GROUP = 128

/**
 * Tests for the Kotlin API entry points (task-graph and execution-plan builders, grid helpers,
 * KernelContext accessors and native-array helpers).
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.kotlin.unittests.api.TestKotlinApi
 * </code>
 */
class TestKotlinApi : TornadoTestBase() {

    @Test
    fun testVectorAddKernelContext() {
        val n = 1024
        val a = TFloatArray(n) { it.toFloat() }
        val b = TFloatArray(n) { 2f * it }
        val c = TFloatArray(n)
        val context = KernelContext()

        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
            task("t0", ::vectorAdd, context, a, b, c)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }

        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 64)))
        }.use { it.execute() }

        for (i in 0 until n) {
            assertEquals(3f * i, c[i], 0.0f)
        }
    }

    @Test
    fun testMatrixAdd2D() {
        val width = 64
        val height = 32
        val a = TFloatArray(width * height) { it.toFloat() }
        val b = TFloatArray(width * height) { 1f }
        val c = TFloatArray(width * height)
        val context = KernelContext()

        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
            task("t0", ::matrixAdd, context, a, b, c, width)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }

        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(width, height, localX = 16, localY = 16)))
        }.use { it.execute() }

        for (i in 0 until width * height) {
            assertEquals(i + 1f, c[i], 0.0f)
        }
    }

    @Test
    fun testGlobalSizeAccessor() {
        val n = 256
        val out = TIntArray(n)
        val context = KernelContext()

        val graph = taskGraph("s0") {
            task("t0", ::globalSize, context, out)
            transferToHost(DataTransferMode.EVERY_EXECUTION, out)
        }

        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 32)))
        }.use { it.execute() }

        for (i in 0 until n) {
            assertEquals(n, out[i])
        }
    }

    @Test
    fun testLocalMemoryGroupSum() {
        val groups = 16
        val input = TFloatArray(groups * GROUP) { (it % 7).toFloat() }
        val partial = TFloatArray(groups)
        val context = KernelContext()

        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
            task("t0", ::groupSum, context, input, partial)
            transferToHost(DataTransferMode.EVERY_EXECUTION, partial)
        }

        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(groups * GROUP, local = GROUP)))
        }.use { it.execute() }

        for (g in 0 until groups) {
            var expected = 0f
            for (i in g * GROUP until (g + 1) * GROUP) {
                expected += input[i]
            }
            assertEquals(expected, partial[g], 0.001f)
        }
    }

    @Test
    fun testLambdaKernel() {
        val n = 512
        val a = TFloatArray(n) { it.toFloat() }
        val c = TFloatArray(n)
        val context = KernelContext()

        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", { ctx: KernelContext, x: TFloatArray, y: TFloatArray ->
                val i = ctx.globalIdX
                y[i] = x[i] * 2f
            }, context, a, c)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }

        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 64)))
        }.use { it.execute() }

        for (i in 0 until n) {
            assertEquals(2f * i, c[i], 0.0f)
        }
    }

    @Test
    fun testToExecutionPlanRepeatedExecution() {
        val n = 256
        val a = TFloatArray(n) { 1f }
        val b = TFloatArray(n) { 1f }
        val c = TFloatArray(n)
        val context = KernelContext()

        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
            task("t0", ::vectorAdd, context, a, b, c)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }

        graph.toExecutionPlan().use { plan ->
            plan.withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 32)))
            for (run in 1..3) {
                a.init(run.toFloat())
                plan.execute()
                for (i in 0 until n) {
                    assertEquals(run + 1f, c[i], 0.0f)
                }
            }
        }
    }

    @Test
    fun testNativeArrayHelpers() {
        val values = floatArrayOf(1f, 2f, 3f, 4f)
        val tornado = values.toTornado()
        assertEquals(4, tornado.size)
        assertArrayEquals(values, tornado.toFloatArray(), 0.0f)

        val squares = TFloatArray(5) { (it * it).toFloat() }
        assertArrayEquals(floatArrayOf(0f, 1f, 4f, 9f, 16f), squares.toFloatArray(), 0.0f)

        val literal = tFloatArrayOf(5f, 6f)
        literal[1] = 7f
        assertEquals(7f, literal[1], 0.0f)
    }
}
