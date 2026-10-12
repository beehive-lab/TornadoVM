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
@file:JvmName("TileVectorAdd")

package uk.ac.manchester.tornado.kotlin.examples.tile

import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.api.tile.TileContext
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.plus
import uk.ac.manchester.tornado.kotlin.api.runOnHost
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.tileGrid

/** Elements per tile block: a power of two and a compile-time constant, as CUDA Tile requires. */
const val VECTOR_TILE = 256

/**
 * Vector addition with CUDA Tile. The kernel describes one tile block; `a + b` is `tc.add(a, b)`.
 *
 * ```
 * tornado -m tornado.kotlin.examples/uk.ac.manchester.tornado.kotlin.examples.tile.TileVectorAdd
 * ```
 */
fun tileVectorAdd(tc: TileContext, a: TFloatArray, b: TFloatArray, c: TFloatArray, n: Int) = with(tc) {
    val av = partition(view(a, n), VECTOR_TILE)
    val bv = partition(view(b, n), VECTOR_TILE)
    val cv = partition(view(c, n), VECTOR_TILE)
    val block = bidX()
    cv.store(av.load(block) + bv.load(block), block)
}

fun main() {
    val n = 1 shl 20
    val a = TFloatArray(n) { it.toFloat() }
    val b = TFloatArray(n) { 2f * it }
    val c = TFloatArray(n)
    val tc = TileContext()

    // The worker grid of a tile task counts tile blocks, not threads.
    val deviceMs = runOnDevice(tileGrid("tile.add", n / VECTOR_TILE)) {
        taskGraph("tile") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
            task("add", ::tileVectorAdd, tc, a, b, c, n)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }
    }
    if (deviceMs == null) {
        tc.runOnHost(n / VECTOR_TILE) { tileVectorAdd(it, a, b, c, n) }
    }
    report("TileVectorAdd", deviceMs, (0 until n).all { c[it] == 3f * it })
}
