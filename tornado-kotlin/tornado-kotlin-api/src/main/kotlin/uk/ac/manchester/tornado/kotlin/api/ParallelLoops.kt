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
@file:JvmName("ParallelLoops")

package uk.ac.manchester.tornado.kotlin.api

/**
 * A loop whose iterations TornadoVM runs in parallel: the Kotlin equivalent of a Java loop with a
 * `@Parallel` loop variable.
 *
 * ```
 * fun vectorAdd(a: TFloatArray, b: TFloatArray, c: TFloatArray) {
 *     parallelFor(0, c.size) { i ->
 *         c[i] = a[i] + b[i]
 *     }
 * }
 * ```
 *
 * Nest two or three calls for 2D and 3D kernels, as with nested `@Parallel` loops in Java. As in
 * Java, [start] must be a constant for the loop to run in parallel. On the
 * host, or when TornadoVM runs the kernel sequentially, it is an ordinary loop from [start]
 * (inclusive) to [end] (exclusive).
 *
 * Kotlin does not keep `@Parallel` on loop variables, so `parallelFor` marks its index with
 * [parallelIndex] instead, which the TornadoVM compiler recognises.
 */
inline fun parallelFor(start: Int, end: Int, body: (Int) -> Unit) {
    var i = start
    while (i < end) {
        body(parallelIndex(i))
        i++
    }
}

/**
 * Marks [index] as the index of a parallel loop and returns it unchanged. Used by [parallelFor];
 * not meant to be called directly.
 */
fun parallelIndex(index: Int): Int = index
