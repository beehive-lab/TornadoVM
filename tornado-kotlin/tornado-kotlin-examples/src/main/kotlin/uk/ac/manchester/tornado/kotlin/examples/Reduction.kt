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
@file:JvmName("Reduction")

package uk.ac.manchester.tornado.kotlin.examples

import uk.ac.manchester.tornado.api.annotations.Reduce
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.parallelFor
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.toExecutionPlan

/**
 * Parallel reduction: `@Reduce` marks the output, as in Java.
 *
 * ```
 * tornado -m tornado.kotlin.examples/uk.ac.manchester.tornado.kotlin.examples.Reduction
 * ```
 */
fun sum(input: TFloatArray, @Reduce result: TFloatArray) {
    result[0] = 0f
    parallelFor(0, input.size) { i ->
        result[0] += input[i]
    }
}

fun main(args: Array<String>) {
    val n = args.firstOrNull()?.toInt() ?: (1 shl 22)
    val input = TFloatArray(n) { 1f }
    val result = TFloatArray(1)

    taskGraph("s0") {
        transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
        task("t0", ::sum, input, result)
        transferToHost(DataTransferMode.EVERY_EXECUTION, result)
    }.toExecutionPlan().use { it.execute() }

    println("Sum of $n ones: ${result[0]} (${if (result[0] == n.toFloat()) "correct" else "wrong"})")
}
