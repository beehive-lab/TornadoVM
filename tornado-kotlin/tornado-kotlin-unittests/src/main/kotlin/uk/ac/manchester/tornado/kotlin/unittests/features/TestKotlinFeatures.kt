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
package uk.ac.manchester.tornado.kotlin.unittests.features

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.ac.manchester.tornado.api.KernelContext
import uk.ac.manchester.tornado.api.TaskGraph
import uk.ac.manchester.tornado.api.TornadoExecutionPlan
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.api.enums.ProfilerMode
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType
import uk.ac.manchester.tornado.api.types.HalfFloat
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat
import uk.ac.manchester.tornado.api.types.vectors.Float4
import uk.ac.manchester.tornado.kotlin.api.TDoubleArray
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.TIntArray
import uk.ac.manchester.tornado.kotlin.api.TLongArray
import uk.ac.manchester.tornado.kotlin.api.executionPlan
import uk.ac.manchester.tornado.kotlin.api.globalIdX
import uk.ac.manchester.tornado.kotlin.api.gridScheduler
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.workerGrid
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase

private const val N = 1024
private const val LOCAL = 64

fun float4Add(context: KernelContext, a: VectorFloat4, b: VectorFloat4, c: VectorFloat4, n: Int) {
    val i = context.globalIdX
    if (i < n) c.set(i, Float4.add(a.get(i), b.get(i)))
}

fun halfAdd(context: KernelContext, a: HalfFloatArray, b: HalfFloatArray, c: HalfFloatArray, n: Int) {
    val i = context.globalIdX
    if (i < n) c.set(i, HalfFloat.add(a.get(i), b.get(i)))
}

fun atomicCount(context: KernelContext, counter: TIntArray, n: Int) {
    val i = context.globalIdX
    if (i < n) context.atomicAdd(counter, 0, 1)
}

fun square(x: Float): Float = x * x

fun Float.cube(): Float = this * this * this

fun helperCalls(context: KernelContext, a: TFloatArray, c: TFloatArray, n: Int) {
    val i = context.globalIdX
    if (i < n) c[i] = square(a[i]) + a[i].cube()
}

fun kotlinMath(context: KernelContext, a: TFloatArray, c: TFloatArray, n: Int) {
    val i = context.globalIdX
    if (i < n) c[i] = kotlin.math.sqrt(a[i]) + kotlin.math.abs(-a[i]) + kotlin.math.max(a[i], 1f)
}

fun unsignedShift(context: KernelContext, a: TIntArray, c: TIntArray, n: Int) {
    val i = context.globalIdX
    if (i < n) c[i] = (a[i].toUInt() shr 1).toInt()
}

fun unsignedDivide(context: KernelContext, a: TIntArray, c: TIntArray, n: Int) {
    val i = context.globalIdX
    if (i < n) c[i] = (a[i].toUInt() / 3u).toInt()
}

fun whenExpression(context: KernelContext, c: TIntArray, n: Int) {
    val i = context.globalIdX
    if (i < n) {
        c[i] = when (i % 3) {
            0 -> 10
            1 -> 20
            else -> 30
        }
    }
}

fun longAndDouble(context: KernelContext, l: TLongArray, d: TDoubleArray, n: Int) {
    val i = context.globalIdX
    if (i < n) {
        l[i] = i.toLong() * 3_000_000_000L
        d[i] = i.toDouble() * 0.5
    }
}

fun matrixRead(context: KernelContext, m: Matrix2DFloat, c: TFloatArray, rows: Int, cols: Int) {
    val i = context.globalIdX
    if (i < rows * cols) c[i] = m.get(i / cols, i % cols) * 2f
}

fun innerLoops(context: KernelContext, c: TFloatArray, n: Int) {
    val i = context.globalIdX
    if (i < n) {
        var sum = 0f
        for (j in 0 until 8) sum += j
        repeat(4) { sum += 1f }
        c[i] = sum
    }
}

fun downToLoop(context: KernelContext, c: TFloatArray, n: Int) {
    val i = context.globalIdX
    if (i < n) {
        var sum = 0f
        for (j in 7 downTo 0) sum += j
        c[i] = sum
    }
}

fun notNullAssertion(context: KernelContext, a: TFloatArray?, c: TFloatArray, n: Int) {
    val i = context.globalIdX
    if (i < n) c[i] = a!![i] + 1f
}

class Scale(val factor: Float)

fun propertyRead(context: KernelContext, s: Scale, a: TFloatArray, c: TFloatArray, n: Int) {
    val i = context.globalIdX
    if (i < n) c[i] = a[i] * s.factor
}

object Kernels {
    @JvmStatic
    fun addOne(context: KernelContext, a: TFloatArray, c: TFloatArray, n: Int) {
        val i = context.globalIdX
        if (i < n) c[i] = a[i] + 1f
    }
}

/**
 * TornadoVM features used from Kotlin kernels and execution plans: vector and half-float types,
 * matrices, atomics, Kotlin language constructs inside kernels, and execution-plan options.
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.kotlin.unittests.features.TestKotlinFeatures
 * </code>
 */
class TestKotlinFeatures : TornadoTestBase() {

    private fun run(graph: TaskGraph, configure: TornadoExecutionPlan.() -> Unit = {}) {
        executionPlan(graph.snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(N, local = LOCAL)))
            configure()
        }.use { it.execute() }
    }

    private fun floats(init: (Int) -> Float) = TFloatArray(N, init)

    @Test
    fun testVectorFloat4() {
        val a = VectorFloat4(N)
        val b = VectorFloat4(N)
        val c = VectorFloat4(N)
        for (i in 0 until N) {
            a.set(i, Float4(i.toFloat(), 1f, 2f, 3f))
            b.set(i, Float4(1f, 1f, 1f, 1f))
        }
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
            task("t0", ::float4Add, context, a, b, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) {
            assertEquals(i + 1f, c.get(i).x, 0.0001f)
            assertEquals(4f, c.get(i).w, 0.0001f)
        }
    }

    @Test
    fun testHalfFloat() {
        val a = HalfFloatArray(N)
        val b = HalfFloatArray(N)
        val c = HalfFloatArray(N)
        for (i in 0 until N) {
            a.set(i, HalfFloat((i % 16).toFloat()))
            b.set(i, HalfFloat(0.5f))
        }
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
            task("t0", ::halfAdd, context, a, b, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) assertEquals((i % 16) + 0.5f, c.get(i).getFloat32(), 0.01f)
    }

    @Test
    fun testAtomicAdd() {
        val counter = TIntArray(1)
        counter.init(0)
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, counter)
            task("t0", ::atomicCount, context, counter, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, counter)
        })
        assertEquals(N, counter[0])
    }

    @Test
    fun testHelperAndExtensionFunctions() {
        val a = floats { (it % 10).toFloat() }
        val c = TFloatArray(N)
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", ::helperCalls, context, a, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) {
            val x = (i % 10).toFloat()
            assertEquals(x * x + x * x * x, c[i], 0.001f)
        }
    }

    @Test
    fun testKotlinMath() {
        val a = floats { (it % 10).toFloat() }
        val c = TFloatArray(N)
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", ::kotlinMath, context, a, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) {
            val x = (i % 10).toFloat()
            assertEquals(kotlin.math.sqrt(x) + x + kotlin.math.max(x, 1f), c[i], 0.001f)
        }
    }

    @Test
    fun testUnsignedShift() {
        val a = TIntArray(N) { -it }
        val c = TIntArray(N)
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", ::unsignedShift, context, a, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) assertEquals(((-i).toUInt() shr 1).toInt(), c[i])
    }

    @Test
    fun testUnsignedDivide() {
        val a = TIntArray(N) { -it }
        val c = TIntArray(N)
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", ::unsignedDivide, context, a, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) assertEquals(((-i).toUInt() / 3u).toInt(), c[i])
    }

    @Test
    fun testWhenExpression() {
        val c = TIntArray(N)
        val context = KernelContext()
        run(taskGraph("s0") {
            task("t0", ::whenExpression, context, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) assertEquals(10 * (i % 3 + 1), c[i])
    }

    @Test
    fun testLongAndDouble() {
        val l = TLongArray(N) { 0L }
        val d = TDoubleArray(N) { 0.0 }
        val context = KernelContext()
        run(taskGraph("s0") {
            task("t0", ::longAndDouble, context, l, d, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, l, d)
        })
        for (i in 0 until N) {
            assertEquals(i * 3_000_000_000L, l[i])
            assertEquals(i * 0.5, d[i], 1e-9)
        }
    }

    @Test
    fun testMatrix2DFloat() {
        val rows = 32
        val cols = 32
        val m = Matrix2DFloat(rows, cols)
        for (r in 0 until rows) for (k in 0 until cols) m.set(r, k, (r * cols + k).toFloat())
        val c = TFloatArray(rows * cols)
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, m)
            task("t0", ::matrixRead, context, m, c, rows, cols)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until rows * cols) assertEquals(2f * i, c[i], 0.001f)
    }

    @Test
    fun testUntilAndRepeatLoops() {
        val c = TFloatArray(N)
        val context = KernelContext()
        run(taskGraph("s0") {
            task("t0", ::innerLoops, context, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) assertEquals(32f, c[i], 0.001f)
    }

    @Test
    fun testDownToLoop() {
        val c = TFloatArray(N)
        val context = KernelContext()
        run(taskGraph("s0") {
            task("t0", ::downToLoop, context, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) assertEquals(28f, c[i], 0.001f)
    }

    @Test
    fun testNotNullAssertion() {
        val a = floats { it.toFloat() }
        val c = TFloatArray(N)
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", ::notNullAssertion, context, a, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) assertEquals(i + 1f, c[i], 0.001f)
    }

    @Test
    fun testObjectPropertyParameter() {
        val a = floats { it.toFloat() }
        val c = TFloatArray(N)
        val scale = Scale(3f)
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a, scale)
            task("t0", ::propertyRead, context, scale, a, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) assertEquals(3f * i, c[i], 0.001f)
    }

    @Test
    fun testJvmStaticObjectKernel() {
        val a = floats { it.toFloat() }
        val c = TFloatArray(N)
        val context = KernelContext()
        run(taskGraph("s0") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
            task("t0", Kernels::addOne, context, a, c, N)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        })
        for (i in 0 until N) assertEquals(i + 1f, c[i], 0.001f)
    }

    private fun addOneGraph(a: TFloatArray, c: TFloatArray, context: KernelContext) = taskGraph("s0") {
        transferToDevice(DataTransferMode.FIRST_EXECUTION, a)
        task("t0", Kernels::addOne, context, a, c, N)
        transferToHost(DataTransferMode.EVERY_EXECUTION, c)
    }

    @Test
    fun testProfiler() {
        val a = floats { it.toFloat() }
        val c = TFloatArray(N)
        val context = KernelContext()
        executionPlan(addOneGraph(a, c, context).snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(N, local = LOCAL)))
            withProfiler(ProfilerMode.SILENT)
        }.use { plan ->
            val result = plan.execute()
            assert(result.profilerResult.deviceKernelTime > 0)
        }
        for (i in 0 until N) assertEquals(i + 1f, c[i], 0.001f)
    }

    @Test
    fun testCudaGraph() {
        val backendType = getTornadoRuntime().defaultDevice.tornadoVMBackend
        if (backendType != TornadoVMBackendType.CUDA) {
            assertNotBackend(backendType, "CUDA graphs require the CUDA backend (default device is $backendType)")
        }
        val a = floats { it.toFloat() }
        val c = TFloatArray(N)
        val context = KernelContext()
        executionPlan(addOneGraph(a, c, context).snapshot()) {
            withGridScheduler(gridScheduler("s0.t0" to workerGrid(N, local = LOCAL)))
            withCUDAGraph()
        }.use { plan -> repeat(3) { plan.execute() } }
        for (i in 0 until N) assertEquals(i + 1f, c[i], 0.001f)
    }
}
