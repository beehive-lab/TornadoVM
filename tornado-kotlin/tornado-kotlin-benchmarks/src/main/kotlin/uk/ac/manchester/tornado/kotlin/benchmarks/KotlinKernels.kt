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
@file:JvmName("KotlinKernels")

package uk.ac.manchester.tornado.kotlin.benchmarks

import uk.ac.manchester.tornado.api.math.TornadoMath
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.TShortArray
import uk.ac.manchester.tornado.kotlin.api.parallelFor

// Kotlin ports of the Java benchmark kernels (LinearAlgebraArrays, ComputeKernels). They follow the
// Java code statement by statement, with parallelFor in place of @Parallel loops, so that the
// Kotlin and Java versions can be compared directly.

fun saxpy(alpha: Float, x: TFloatArray, y: TFloatArray) {
    parallelFor(0, y.size) { i ->
        y[i] = y[i] + alpha * x[i]
    }
}

fun sgemm(m: Int, n: Int, k: Int, a: TFloatArray, b: TFloatArray, c: TFloatArray) {
    parallelFor(0, n) { i ->
        parallelFor(0, n) { j ->
            var sum = 0.0f
            for (kk in 0 until k) {
                sum += a[(i * n) + kk] * b[(kk * n) + j]
            }
            c[(i * n) + j] = sum
        }
    }
}

const val S_LOWER_LIMIT = 10.0f
const val S_UPPER_LIMIT = 100.0f
const val K_LOWER_LIMIT = 10.0f
const val K_UPPER_LIMIT = 100.0f
const val T_LOWER_LIMIT = 1.0f
const val T_UPPER_LIMIT = 10.0f
const val R_LOWER_LIMIT = 0.01f
const val R_UPPER_LIMIT = 0.05f
const val SIGMA_LOWER_LIMIT = 0.01f
const val SIGMA_UPPER_LIMIT = 0.10f

fun phi(x: Float): Float {
    val c1 = 0.319381530f
    val c2 = -0.356563782f
    val c3 = 1.781477937f
    val c4 = -1.821255978f
    val c5 = 1.330274429f

    val zero = 0.0f
    val one = 1.0f
    val two = 2.0f
    val temp4 = 0.2316419f

    val oneBySqrt2pi = 0.398942280f

    val absX = Math.abs(x)
    val t = one / (one + (temp4 * absX))

    val y = (one - (oneBySqrt2pi * TornadoMath.exp((-x * x) / two) * t * (c1 + (t * (c2 + (t * (c3 + (t * (c4 + (t * c5))))))))))

    return if (x < zero) (one - y) else y
}

fun blackscholes(randArray: TFloatArray, put: TFloatArray, call: TFloatArray) {
    parallelFor(0, call.size) { gid ->
        val two = 2.0f
        val inRand = randArray[gid]
        val s = (S_LOWER_LIMIT * inRand) + (S_UPPER_LIMIT * (1.0f - inRand))
        val k = (K_LOWER_LIMIT * inRand) + (K_UPPER_LIMIT * (1.0f - inRand))
        val t = (T_LOWER_LIMIT * inRand) + (T_UPPER_LIMIT * (1.0f - inRand))
        val r = (R_LOWER_LIMIT * inRand) + (R_UPPER_LIMIT * (1.0f - inRand))
        val sigmaVal = (SIGMA_LOWER_LIMIT * inRand) + (SIGMA_UPPER_LIMIT * (1.0f - inRand))

        val sigmaSqrtT = sigmaVal * TornadoMath.sqrt(t)

        val d1 = (TornadoMath.log(s / k) + ((r + ((sigmaVal * sigmaVal) / two)) * t)) / sigmaSqrtT
        val d2 = d1 - sigmaSqrtT

        val kexpMinusRT = k * TornadoMath.exp(-r * t)

        var phiD1 = phi(d1)
        var phiD2 = phi(d2)

        call[gid] = (s * phiD1) - (kexpMinusRT * phiD2)
        phiD1 = phi(-d1)
        phiD2 = phi(-d2)

        put[gid] = (kexpMinusRT * phiD2) - (s * phiD1)
    }
}

fun nBody(numBodies: Int, refPos: TFloatArray, refVel: TFloatArray, delT: Float, espSqr: Float) {
    parallelFor(0, numBodies) { i ->
        val body = 4 * i
        val acc = floatArrayOf(0.0f, 0.0f, 0.0f)
        for (j in 0 until numBodies) {
            val r = FloatArray(3)
            val index = 4 * j

            var distSqr = 0.0f
            for (k in 0 until 3) {
                r[k] = refPos[index + k] - refPos[body + k]
                distSqr += r[k] * r[k]
            }

            val invDist = 1.0f / TornadoMath.sqrt(distSqr + espSqr)

            val invDistCube = invDist * invDist * invDist
            val s = refPos[index + 3] * invDistCube

            for (k in 0 until 3) {
                acc[k] += s * r[k]
            }
        }
        for (k in 0 until 3) {
            refPos[body + k] = refPos[body + k] + refPos[body + k] * delT + 0.5f * acc[k] * delT * delT
            refVel[body + k] = refPos[body + k] + acc[k] * delT
        }
    }
}

fun mandelbrot(size: Int, output: TShortArray) {
    val iterations = 10000
    val space = 2.0f / size
    parallelFor(0, size) { i ->
        parallelFor(0, size) { j ->
            var zr = 0.0f
            var zi = 0.0f
            val cr = (1 * j * space - 1.5f)
            val ci = (1 * i * space - 1.0f)
            var zrN = 0f
            var ziN = 0f
            var y = 0
            var ii = 0
            while (ii < iterations) {
                if (ziN + zrN <= 4.0f) {
                    zi = 2.0f * zr * zi + ci
                    zr = 1 * zrN - ziN + cr
                    ziN = zi * zi
                    zrN = zr * zr
                    y++
                } else {
                    ii = iterations
                }
                ii++
            }
            val r = ((y * 255) / iterations).toShort()
            output[i * size + j] = r
        }
    }
}

fun computeDFT(inreal: TFloatArray, inimag: TFloatArray, outreal: TFloatArray, outimag: TFloatArray) {
    val n = inreal.size
    parallelFor(0, n) { k ->
        var sumReal = 0f
        var simImag = 0f
        for (t in 0 until n) {
            val angle = (2 * TornadoMath.floatPI() * t * k) / n
            sumReal += inreal[t] * TornadoMath.cos(angle) + inimag[t] * TornadoMath.sin(angle)
            simImag += -inreal[t] * TornadoMath.sin(angle) + inimag[t] * TornadoMath.cos(angle)
        }
        outreal[k] = sumReal
        outimag[k] = simImag
    }
}

fun monteCarlo(result: TFloatArray, size: Int) {
    val total = size
    val iter = 25000
    parallelFor(0, total) { idx ->
        var seed = idx.toLong()
        var sum = 0.0f
        for (j in 0 until iter) {
            seed = (seed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
            seed = (seed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
            val x = (seed and 0x0FFFFFFF) / 268435455f
            seed = (seed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
            seed = (seed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
            val y = (seed and 0x0FFFFFFF) / 268435455f
            val dist = TornadoMath.sqrt(x * x + y * y)
            if (dist <= 1.0f) {
                sum += 1.0f
            }
        }
        sum = sum * 4
        result[idx] = sum / iter
    }
}
