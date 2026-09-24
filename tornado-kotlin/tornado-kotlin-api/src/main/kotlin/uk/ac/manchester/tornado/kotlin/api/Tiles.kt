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
@file:JvmName("Tiles")

package uk.ac.manchester.tornado.kotlin.api

import uk.ac.manchester.tornado.api.GridScheduler
import uk.ac.manchester.tornado.api.WorkerGrid
import uk.ac.manchester.tornado.api.WorkerGrid1D
import uk.ac.manchester.tornado.api.WorkerGrid2D
import uk.ac.manchester.tornado.api.WorkerGrid3D
import uk.ac.manchester.tornado.api.tile.Tile
import uk.ac.manchester.tornado.api.tile.TileContext

// Kotlin layer over the CUDA Tile API (TileContext). Every tile operation is a method of
// TileContext; these helpers let a kernel write them as operators and functions on Tile:
//
//     fun vectorAdd(tc: TileContext, a: TFloatArray, b: TFloatArray, c: TFloatArray, n: Int) = with(tc) {
//         val block = bidX()
//         partition(view(c, n), TILE).store(partition(view(a, n), TILE).load(block) + partition(view(b, n), TILE).load(block), block)
//     }
//
// They take the TileContext as a context parameter and are inline, so `a + b` inside
// `with(tc) { ... }` compiles to exactly `tc.add(a, b)` on the kernel's own TileContext: the
// bytecode the CUDA Tile compiler sees is the same as for the Java API. As in Java, tile shapes
// must be compile-time constants: use literals or `const val`.

// ------------------------------------------------------------------ arithmetic

/** Elementwise sum, `tc.add(a, b)`. */
context(tc: TileContext)
inline operator fun Tile.plus(other: Tile): Tile = tc.add(this, other)

/** Elementwise difference, `tc.sub(a, b)`. */
context(tc: TileContext)
inline operator fun Tile.minus(other: Tile): Tile = tc.sub(this, other)

/** Elementwise product, `tc.mul(a, b)`. */
context(tc: TileContext)
inline operator fun Tile.times(other: Tile): Tile = tc.mul(this, other)

/** Elementwise quotient, `tc.div(a, b)`. */
context(tc: TileContext)
inline operator fun Tile.div(other: Tile): Tile = tc.div(this, other)

/** Elementwise remainder, `tc.remainder(a, b)`. */
context(tc: TileContext)
inline operator fun Tile.rem(other: Tile): Tile = tc.remainder(this, other)

/** Multiplies every element by [scalar], `tc.scale(a, scalar)`. */
context(tc: TileContext)
inline operator fun Tile.times(scalar: Double): Tile = tc.scale(this, scalar)

/** Multiplies every element by [scalar], `tc.scale(a, scalar)`. */
context(tc: TileContext)
inline operator fun Tile.times(scalar: Float): Tile = tc.scale(this, scalar.toDouble())

/** Negation, `tc.scale(a, -1.0)`. */
context(tc: TileContext)
inline operator fun Tile.unaryMinus(): Tile = tc.scale(this, -1.0)

/** Matrix product of two tiles, `tc.matmul(a, b)`. */
context(tc: TileContext)
inline infix fun Tile.matmul(other: Tile): Tile = tc.matmul(this, other)

// ------------------------------------------------------------------ comparisons (predicate tiles)

context(tc: TileContext)
inline infix fun Tile.lt(other: Tile): Tile = tc.lessThan(this, other)

context(tc: TileContext)
inline infix fun Tile.lt(scalar: Double): Tile = tc.lessThan(this, scalar)

context(tc: TileContext)
inline infix fun Tile.le(other: Tile): Tile = tc.lessOrEqual(this, other)

context(tc: TileContext)
inline infix fun Tile.le(scalar: Double): Tile = tc.lessOrEqual(this, scalar)

context(tc: TileContext)
inline infix fun Tile.gt(other: Tile): Tile = tc.greaterThan(this, other)

context(tc: TileContext)
inline infix fun Tile.gt(scalar: Double): Tile = tc.greaterThan(this, scalar)

context(tc: TileContext)
inline infix fun Tile.ge(other: Tile): Tile = tc.greaterOrEqual(this, other)

context(tc: TileContext)
inline infix fun Tile.ge(scalar: Double): Tile = tc.greaterOrEqual(this, scalar)

/** Elementwise choice between [whenTrue] and [whenFalse] by this predicate tile, `tc.select(...)`. */
context(tc: TileContext)
inline fun Tile.select(whenTrue: Tile, whenFalse: Tile): Tile = tc.select(this, whenTrue, whenFalse)

// ------------------------------------------------------------------ math

context(tc: TileContext)
inline fun exp(a: Tile): Tile = tc.exp(a)

context(tc: TileContext)
inline fun log(a: Tile): Tile = tc.log(a)

context(tc: TileContext)
inline fun sqrt(a: Tile): Tile = tc.sqrt(a)

context(tc: TileContext)
inline fun rsqrt(a: Tile): Tile = tc.rsqrt(a)

context(tc: TileContext)
inline fun tanh(a: Tile): Tile = tc.tanh(a)

context(tc: TileContext)
inline fun sin(a: Tile): Tile = tc.sin(a)

context(tc: TileContext)
inline fun cos(a: Tile): Tile = tc.cos(a)

context(tc: TileContext)
inline fun abs(a: Tile): Tile = tc.abs(a)

context(tc: TileContext)
inline fun maximum(a: Tile, b: Tile): Tile = tc.maximum(a, b)

context(tc: TileContext)
inline fun minimum(a: Tile, b: Tile): Tile = tc.minimum(a, b)

/** Fused multiply-add `a * b + c`, `tc.fma(a, b, c)`. */
context(tc: TileContext)
inline fun fma(a: Tile, b: Tile, c: Tile): Tile = tc.fma(a, b, c)

// ------------------------------------------------------------------ reductions (keep the reduced axis)

context(tc: TileContext)
inline fun Tile.sum(axis: Int): Tile = tc.sum(this, axis)

context(tc: TileContext)
inline fun Tile.max(axis: Int): Tile = tc.max(this, axis)

context(tc: TileContext)
inline fun Tile.min(axis: Int): Tile = tc.min(this, axis)

// ------------------------------------------------------------------ launching

/**
 * Grid scheduler for a tile task. The worker grid of a tile task counts tile blocks, not threads,
 * and must not set a local work size (CUDA Tile pins the block to 1x1x1).
 */
fun tileGrid(task: String, blocksX: Int, blocksY: Int = 1, blocksZ: Int = 1): GridScheduler {
    val worker: WorkerGrid = when {
        blocksZ > 1 -> WorkerGrid3D(blocksX, blocksY, blocksZ)
        blocksY > 1 -> WorkerGrid2D(blocksX, blocksY)
        else -> WorkerGrid1D(blocksX)
    }
    return GridScheduler(task, worker)
}

/**
 * Runs a tile kernel on the JVM, over [blocksX] x [blocksY] x [blocksZ] tile blocks, using the
 * JVM implementation every tile operation has. Useful to check a kernel on a host without CUDA
 * Tile. Host side only.
 */
inline fun TileContext.runOnHost(blocksX: Int, blocksY: Int = 1, blocksZ: Int = 1, kernel: (TileContext) -> Unit) {
    setBlockCount(blocksX, blocksY, blocksZ)
    for (z in 0 until blocksZ) {
        for (y in 0 until blocksY) {
            for (x in 0 until blocksX) {
                setBlockIndex(x, y, z)
                kernel(this)
            }
        }
    }
}
