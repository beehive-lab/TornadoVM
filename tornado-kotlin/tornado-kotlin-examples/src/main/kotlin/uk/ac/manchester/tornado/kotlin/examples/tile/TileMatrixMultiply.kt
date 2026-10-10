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
@file:JvmName("TileMatrixMultiply")

package uk.ac.manchester.tornado.kotlin.examples.tile

import java.util.Random
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.api.tile.DType
import uk.ac.manchester.tornado.api.tile.TileContext
import uk.ac.manchester.tornado.api.types.HalfFloat
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.runOnHost
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.tileGrid

/** Tile shape: part of the kernel's type, so a constant. */
const val MATMUL_TILE = 32

/**
 * Matrix multiplication with FP16 inputs and FP32 accumulation. No thread index, shared memory or
 * barrier: the tile compiler picks the tensor-core instruction and the layouts.
 *
 * ```
 * tornado -m tornado.kotlin.examples/uk.ac.manchester.tornado.kotlin.examples.tile.TileMatrixMultiply [n]
 * ```
 */
fun tileMatrixMultiply(tc: TileContext, a: HalfFloatArray, b: HalfFloatArray, c: TFloatArray, m: Int, n: Int, k: Int) = with(tc) {
    val av = partition(view(a, m, k), MATMUL_TILE, MATMUL_TILE)
    val bv = partition(view(b, k, n), MATMUL_TILE, MATMUL_TILE)
    val cv = partition(view(c, m, n), MATMUL_TILE, MATMUL_TILE)

    val rowBlock = bidX()
    val columnBlock = bidY()

    var acc = zeros(DType.F32, MATMUL_TILE, MATMUL_TILE)
    for (step in 0 until k / MATMUL_TILE) {
        acc = mma(av.load(rowBlock, step), bv.load(step, columnBlock), acc)
    }
    cv.store(acc, rowBlock, columnBlock)
}

fun main(args: Array<String>) {
    val n = args.firstOrNull()?.toInt() ?: 256
    require(n % MATMUL_TILE == 0) { "n must be a multiple of $MATMUL_TILE" }
    val random = Random(7)
    val a = HalfFloatArray(n * n).also { x -> for (i in 0 until n * n) x.set(i, HalfFloat(random.nextFloat() - 0.5f)) }
    val b = HalfFloatArray(n * n).also { x -> for (i in 0 until n * n) x.set(i, HalfFloat(random.nextFloat() - 0.5f)) }
    val c = TFloatArray(n * n)
    val tc = TileContext()

    val blocks = n / MATMUL_TILE
    val deviceMs = runOnDevice(tileGrid("tile.mxm", blocks, blocks)) {
        taskGraph("tile") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
            task("mxm", ::tileMatrixMultiply, tc, a, b, c, n, n, n)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }
    }
    if (deviceMs == null) {
        tc.runOnHost(blocks, blocks) { tileMatrixMultiply(it, a, b, c, n, n, n) }
    }

    var correct = true
    for (row in 0 until n) {
        for (column in 0 until n) {
            var sum = 0f
            for (inner in 0 until n) {
                sum += a.get(row * n + inner).getFloat32() * b.get(inner * n + column).getFloat32()
            }
            if (Math.abs(sum - c[row * n + column]) > 1e-2f * maxOf(1f, Math.abs(sum))) {
                correct = false
            }
        }
    }
    report("TileMatrixMultiply ${n}x$n", deviceMs, correct)
}
