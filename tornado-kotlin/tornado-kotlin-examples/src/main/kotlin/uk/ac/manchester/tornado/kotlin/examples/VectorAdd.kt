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
@file:JvmName("VectorAdd")

package uk.ac.manchester.tornado.kotlin.examples

import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.parallelFor
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.toExecutionPlan

/**
 * Vector addition with a parallel loop.
 *
 * ```
 * tornado -m tornado.kotlin.examples/uk.ac.manchester.tornado.kotlin.examples.VectorAdd
 * ```
 */
fun vectorAdd(a: TFloatArray, b: TFloatArray, c: TFloatArray) {
    parallelFor(0, c.size) { i ->
        c[i] = a[i] + b[i]
    }
}

fun main(args: Array<String>) {
    val n = args.firstOrNull()?.toInt() ?: (1 shl 24)
    val a = TFloatArray(n) { it.toFloat() }
    val b = TFloatArray(n) { 2f * it }
    val c = TFloatArray(n)

    val graph = taskGraph("s0") {
        transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
        task("t0", ::vectorAdd, a, b, c)
        transferToHost(DataTransferMode.EVERY_EXECUTION, c)
    }

    graph.toExecutionPlan().use { plan ->
        repeat(5) {
            val start = System.nanoTime()
            plan.execute()
            println("Iteration $it: ${(System.nanoTime() - start) / 1_000_000.0} ms")
        }
    }

    val correct = (0 until n).all { c[it] == 3f * it }
    println("Result is ${if (correct) "correct" else "wrong"}")
}
