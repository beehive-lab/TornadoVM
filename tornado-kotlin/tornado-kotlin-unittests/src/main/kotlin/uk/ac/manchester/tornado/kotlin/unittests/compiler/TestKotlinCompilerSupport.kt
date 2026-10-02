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
package uk.ac.manchester.tornado.kotlin.unittests.compiler

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.ac.manchester.tornado.api.KernelContext
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.TIntArray
import uk.ac.manchester.tornado.kotlin.api.executionPlan
import uk.ac.manchester.tornado.kotlin.api.gridScheduler
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.workerGrid
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase

// These kernels are written the way plain Kotlin code is, and compiled with kotlinc's default
// flags. They are public, so kotlinc inserts Intrinsics.checkNotNullParameter calls, and they read
// the boxed KernelContext fields directly, so the index stays boxed and is unboxed at every use.
// Both only compile because of the runtime's Kotlin support (tornado.kotlin.support).

fun boxedIndexVectorAdd(context: KernelContext, a: TFloatArray, b: TFloatArray, c: TFloatArray) {
    val i = context.globalIdx
    c[i] = a[i] + b[i]
}

fun boxedIndexInBranch(context: KernelContext, a: TFloatArray, c: TFloatArray, limit: Int) {
    val i = context.globalIdx
    if (i < limit) {
        c[i] = a[i] * 2f
    } else {
        c[i] = -1f
    }
}

fun boxedIndex2D(context: KernelContext, out: TIntArray, width: Int) {
    val x = context.globalIdx
    val y = context.globalIdy
    out[y * width + x] = y * 1000 + x
}

fun boxedLocalIndices(context: KernelContext, out: TIntArray) {
    val g = context.globalIdx
    val l = context.localIdx
    val group = context.groupIdx
    val size = context.localGroupSizeX
    out[g] = group * size + l - g
}

fun nullChecksOnly(context: KernelContext, a: TFloatArray, c: TFloatArray) {
    val i: Int = context.globalIdx
    c[i] = a[i] + 1f
}

/**
 * Tests for the compiler support of kernels written in Kotlin: removal of kotlinc's null-check
 * intrinsics and lowering of boxed KernelContext indices.
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.kotlin.unittests.compiler.TestKotlinCompilerSupport
 * </code>
 */
class TestKotlinCompilerSupport : TornadoTestBase() {

    @Test
    fun testNullCheckIntrinsicsRemoved() {
        val n = 1024
        val a = TFloatArray(n) { it.toFloat() }
        val c = TFloatArray(n)
        val context = KernelContext()
        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", ::nullChecksOnly, context, a, c)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }
        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 64)))
        }.use { it.execute() }
        for (i in 0 until n) {
            assertEquals(i + 1f, c[i], 0.0f)
        }
    }

    @Test
    fun testBoxedGlobalIndex() {
        val n = 2048
        val a = TFloatArray(n) { it.toFloat() }
        val b = TFloatArray(n) { 3f }
        val c = TFloatArray(n)
        val context = KernelContext()
        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
            task("t0", ::boxedIndexVectorAdd, context, a, b, c)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }
        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 128)))
        }.use { it.execute() }
        for (i in 0 until n) {
            assertEquals(i + 3f, c[i], 0.0f)
        }
    }

    @Test
    fun testBoxedGlobalIndexInBranch() {
        val n = 512
        val limit = 300
        val a = TFloatArray(n) { it.toFloat() }
        val c = TFloatArray(n)
        val context = KernelContext()
        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", ::boxedIndexInBranch, context, a, c, limit)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }
        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 64)))
        }.use { it.execute() }
        for (i in 0 until n) {
            assertEquals(if (i < limit) 2f * i else -1f, c[i], 0.0f)
        }
    }

    @Test
    fun testBoxedGlobalIndex2D() {
        val width = 64
        val height = 16
        val out = TIntArray(width * height)
        val context = KernelContext()
        val graph = taskGraph("s0") {
            task("t0", ::boxedIndex2D, context, out, width)
            transferToHost(DataTransferMode.EVERY_EXECUTION, out)
        }
        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(width, height, localX = 16, localY = 4)))
        }.use { it.execute() }
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(y * 1000 + x, out[y * width + x])
            }
        }
    }

    @Test
    fun testBoxedLocalAndGroupIndices() {
        val n = 1024
        val out = TIntArray(n)
        out.init(-1)
        val context = KernelContext()
        val graph = taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, out)
            task("t0", ::boxedLocalIndices, context, out)
            transferToHost(DataTransferMode.EVERY_EXECUTION, out)
        }
        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 32)))
        }.use { it.execute() }
        for (i in 0 until n) {
            assertEquals(0, out[i])
        }
    }
}
