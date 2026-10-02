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
@file:JvmName("MatrixMultiplication")

package uk.ac.manchester.tornado.kotlin.examples

import uk.ac.manchester.tornado.api.KernelContext
import uk.ac.manchester.tornado.api.TaskGraph
import uk.ac.manchester.tornado.api.TornadoExecutionPlan
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.executionPlan
import uk.ac.manchester.tornado.kotlin.api.globalIdX
import uk.ac.manchester.tornado.kotlin.api.globalIdY
import uk.ac.manchester.tornado.kotlin.api.gridScheduler
import uk.ac.manchester.tornado.kotlin.api.parallelFor
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.workerGrid

/**
 * Matrix multiplication written twice: with nested parallel loops, and with a [KernelContext]
 * kernel and an explicit 2D grid. Both are compared with a sequential run.
 *
 * ```
 * tornado -m tornado.kotlin.examples/uk.ac.manchester.tornado.kotlin.examples.MatrixMultiplication [size]
 * ```
 */
fun matrixMultiplyLoops(a: TFloatArray, b: TFloatArray, c: TFloatArray, n: Int) {
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

fun matrixMultiplyKernelContext(context: KernelContext, a: TFloatArray, b: TFloatArray, c: TFloatArray, n: Int) {
    val row = context.globalIdY
    val col = context.globalIdX
    var sum = 0f
    for (k in 0 until n) {
        sum += a[row * n + k] * b[k * n + col]
    }
    c[row * n + col] = sum
}

private fun time(name: String, n: Int, plan: TornadoExecutionPlan) {
    plan.execute()
    val runs = 10
    val start = System.nanoTime()
    repeat(runs) { plan.execute() }
    val seconds = (System.nanoTime() - start) / 1e9 / runs
    println("%-16s %8.3f ms  %7.1f GFLOP/s".format(name, seconds * 1e3, 2.0 * n * n * n / seconds / 1e9))
}

fun main(args: Array<String>) {
    val n = args.firstOrNull()?.toInt() ?: 1024
    val a = TFloatArray(n * n) { (it % 13) / 13f }
    val b = TFloatArray(n * n) { (it % 7) / 7f }
    val cLoops = TFloatArray(n * n)
    val cContext = TFloatArray(n * n)
    val context = KernelContext()

    val loops: TaskGraph = taskGraph("loops") {
        transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
        task("mxm", ::matrixMultiplyLoops, a, b, cLoops, n)
        transferToHost(DataTransferMode.EVERY_EXECUTION, cLoops)
    }
    val kernelContext: TaskGraph = taskGraph("kc") {
        transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
        task("mxm", ::matrixMultiplyKernelContext, context, a, b, cContext, n)
        transferToHost(DataTransferMode.EVERY_EXECUTION, cContext)
    }

    executionPlan(loops.snapshot()).use { time("parallelFor", n, it) }
    executionPlan(kernelContext.snapshot()) {
        withGridScheduler(gridScheduler("kc.mxm" to workerGrid(n, n, localX = 16, localY = 16)))
    }.use { time("KernelContext", n, it) }

    val expected = TFloatArray(n * n)
    matrixMultiplyLoops(a, b, expected, n)
    val correct = (0 until n * n).all { Math.abs(expected[it] - cLoops[it]) < 0.1f && Math.abs(expected[it] - cContext[it]) < 0.1f }
    println("Results are ${if (correct) "correct" else "wrong"}")
}
