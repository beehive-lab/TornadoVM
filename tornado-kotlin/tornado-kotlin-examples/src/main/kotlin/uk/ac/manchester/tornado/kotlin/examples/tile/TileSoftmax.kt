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
@file:JvmName("TileSoftmax")

package uk.ac.manchester.tornado.kotlin.examples.tile

import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.api.tile.TileContext
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.div
import uk.ac.manchester.tornado.kotlin.api.exp
import uk.ac.manchester.tornado.kotlin.api.max
import uk.ac.manchester.tornado.kotlin.api.minus
import uk.ac.manchester.tornado.kotlin.api.runOnHost
import uk.ac.manchester.tornado.kotlin.api.sum
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.tileGrid

/** Row width: a tile shape, so a constant; the number of rows stays a runtime value. */
const val SOFTMAX_WIDTH = 1024

/**
 * Row softmax, one row per tile block. `max` and `sum` keep the reduced axis, so their `[1, 1]`
 * results broadcast back across the row.
 *
 * ```
 * tornado -m tornado.kotlin.examples/uk.ac.manchester.tornado.kotlin.examples.tile.TileSoftmax [rows]
 * ```
 */
fun tileSoftmax(tc: TileContext, input: TFloatArray, output: TFloatArray, rows: Int) = with(tc) {
    val iv = partition(view(input, rows, SOFTMAX_WIDTH), 1, SOFTMAX_WIDTH)
    val ov = partition(view(output, rows, SOFTMAX_WIDTH), 1, SOFTMAX_WIDTH)

    val row = bidX()
    val values = iv.load(row, 0)
    val shifted = exp(values - values.max(1))
    ov.store(shifted / shifted.sum(1), row, 0)
}

fun main(args: Array<String>) {
    val rows = args.firstOrNull()?.toInt() ?: 256
    val input = TFloatArray(rows * SOFTMAX_WIDTH) { ((it * 37) % 101) / 25f }
    val output = TFloatArray(rows * SOFTMAX_WIDTH)
    val tc = TileContext()

    val deviceMs = runOnDevice(tileGrid("tile.softmax", rows)) {
        taskGraph("tile") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
            task("softmax", ::tileSoftmax, tc, input, output, rows)
            transferToHost(DataTransferMode.EVERY_EXECUTION, output)
        }
    }
    if (deviceMs == null) {
        tc.runOnHost(rows) { tileSoftmax(it, input, output, rows) }
    }

    var correct = true
    for (row in 0 until rows) {
        var max = Float.NEGATIVE_INFINITY
        for (i in 0 until SOFTMAX_WIDTH) max = maxOf(max, input[row * SOFTMAX_WIDTH + i])
        var total = 0.0
        for (i in 0 until SOFTMAX_WIDTH) total += Math.exp((input[row * SOFTMAX_WIDTH + i] - max).toDouble())
        for (i in 0 until SOFTMAX_WIDTH) {
            val expected = Math.exp((input[row * SOFTMAX_WIDTH + i] - max).toDouble()) / total
            if (Math.abs(expected - output[row * SOFTMAX_WIDTH + i]) > 1e-5) correct = false
        }
    }
    report("TileSoftmax ${rows}x$SOFTMAX_WIDTH", deviceMs, correct)
}
