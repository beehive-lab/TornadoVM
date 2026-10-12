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
@file:JvmName("NativeArrays")

package uk.ac.manchester.tornado.kotlin.api

import uk.ac.manchester.tornado.api.types.arrays.ByteArray as TornadoByteArray
import uk.ac.manchester.tornado.api.types.arrays.CharArray as TornadoCharArray
import uk.ac.manchester.tornado.api.types.arrays.ShortArray as TornadoShortArray
import uk.ac.manchester.tornado.api.types.arrays.IntArray as TornadoIntArray
import uk.ac.manchester.tornado.api.types.arrays.LongArray as TornadoLongArray
import uk.ac.manchester.tornado.api.types.arrays.FloatArray as TornadoFloatArray
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray as TornadoDoubleArray

// TornadoVM's off-heap arrays share their simple names with Kotlin's built-in arrays
// (kotlin.FloatArray vs uk.ac.manchester.tornado.api.types.arrays.FloatArray). The T-prefixed
// aliases let both be used in one file without import aliases.
//
// Indexing needs no extra code: Kotlin maps the Java get(int)/set(int, value) methods to the
// indexing operators, so `a[i]` and `a[i] = v` compile to the same calls as in Java, and
// getSize() is available as the `size` property.

/** TornadoVM off-heap array of Byte values. */
typealias TByteArray = TornadoByteArray

/** TornadoVM off-heap array of Char values. */
typealias TCharArray = TornadoCharArray

/** TornadoVM off-heap array of Short values. */
typealias TShortArray = TornadoShortArray

/** TornadoVM off-heap array of Int values. */
typealias TIntArray = TornadoIntArray

/** TornadoVM off-heap array of Long values. */
typealias TLongArray = TornadoLongArray

/** TornadoVM off-heap array of Float values. */
typealias TFloatArray = TornadoFloatArray

/** TornadoVM off-heap array of Double values. */
typealias TDoubleArray = TornadoDoubleArray

/** Creates a [TByteArray] of [size] elements, each set to [init] applied to its index. Host-side only. */
inline fun TByteArray(size: Int, init: (Int) -> Byte): TByteArray {
    val array = TByteArray(size)
    for (i in 0 until size) {
        array[i] = init(i)
    }
    return array
}

/** Creates a [TByteArray] holding the given values. */
fun tByteArrayOf(vararg values: Byte): TByteArray = TByteArray.fromArray(values)

/** Copies this Kotlin array into a new [TByteArray]. */
fun ByteArray.toTornado(): TByteArray = TByteArray.fromArray(this)

/** Copies this [TByteArray] into a new Kotlin [ByteArray]. */
fun TByteArray.toByteArray(): ByteArray = toHeapArray()

/** Creates a [TCharArray] of [size] elements, each set to [init] applied to its index. Host-side only. */
inline fun TCharArray(size: Int, init: (Int) -> Char): TCharArray {
    val array = TCharArray(size)
    for (i in 0 until size) {
        array[i] = init(i)
    }
    return array
}

/** Creates a [TCharArray] holding the given values. */
fun tCharArrayOf(vararg values: Char): TCharArray = TCharArray.fromArray(values)

/** Copies this Kotlin array into a new [TCharArray]. */
fun CharArray.toTornado(): TCharArray = TCharArray.fromArray(this)

/** Copies this [TCharArray] into a new Kotlin [CharArray]. */
fun TCharArray.toCharArray(): CharArray = toHeapArray()

/** Creates a [TShortArray] of [size] elements, each set to [init] applied to its index. Host-side only. */
inline fun TShortArray(size: Int, init: (Int) -> Short): TShortArray {
    val array = TShortArray(size)
    for (i in 0 until size) {
        array[i] = init(i)
    }
    return array
}

/** Creates a [TShortArray] holding the given values. */
fun tShortArrayOf(vararg values: Short): TShortArray = TShortArray.fromArray(values)

/** Copies this Kotlin array into a new [TShortArray]. */
fun ShortArray.toTornado(): TShortArray = TShortArray.fromArray(this)

/** Copies this [TShortArray] into a new Kotlin [ShortArray]. */
fun TShortArray.toShortArray(): ShortArray = toHeapArray()

/** Creates a [TIntArray] of [size] elements, each set to [init] applied to its index. Host-side only. */
inline fun TIntArray(size: Int, init: (Int) -> Int): TIntArray {
    val array = TIntArray(size)
    for (i in 0 until size) {
        array[i] = init(i)
    }
    return array
}

/** Creates a [TIntArray] holding the given values. */
fun tIntArrayOf(vararg values: Int): TIntArray = TIntArray.fromArray(values)

/** Copies this Kotlin array into a new [TIntArray]. */
fun IntArray.toTornado(): TIntArray = TIntArray.fromArray(this)

/** Copies this [TIntArray] into a new Kotlin [IntArray]. */
fun TIntArray.toIntArray(): IntArray = toHeapArray()

/** Creates a [TLongArray] of [size] elements, each set to [init] applied to its index. Host-side only. */
inline fun TLongArray(size: Int, init: (Int) -> Long): TLongArray {
    val array = TLongArray(size)
    for (i in 0 until size) {
        array[i] = init(i)
    }
    return array
}

/** Creates a [TLongArray] holding the given values. */
fun tLongArrayOf(vararg values: Long): TLongArray = TLongArray.fromArray(values)

/** Copies this Kotlin array into a new [TLongArray]. */
fun LongArray.toTornado(): TLongArray = TLongArray.fromArray(this)

/** Copies this [TLongArray] into a new Kotlin [LongArray]. */
fun TLongArray.toLongArray(): LongArray = toHeapArray()

/** Creates a [TFloatArray] of [size] elements, each set to [init] applied to its index. Host-side only. */
inline fun TFloatArray(size: Int, init: (Int) -> Float): TFloatArray {
    val array = TFloatArray(size)
    for (i in 0 until size) {
        array[i] = init(i)
    }
    return array
}

/** Creates a [TFloatArray] holding the given values. */
fun tFloatArrayOf(vararg values: Float): TFloatArray = TFloatArray.fromArray(values)

/** Copies this Kotlin array into a new [TFloatArray]. */
fun FloatArray.toTornado(): TFloatArray = TFloatArray.fromArray(this)

/** Copies this [TFloatArray] into a new Kotlin [FloatArray]. */
fun TFloatArray.toFloatArray(): FloatArray = toHeapArray()

/** Creates a [TDoubleArray] of [size] elements, each set to [init] applied to its index. Host-side only. */
inline fun TDoubleArray(size: Int, init: (Int) -> Double): TDoubleArray {
    val array = TDoubleArray(size)
    for (i in 0 until size) {
        array[i] = init(i)
    }
    return array
}

/** Creates a [TDoubleArray] holding the given values. */
fun tDoubleArrayOf(vararg values: Double): TDoubleArray = TDoubleArray.fromArray(values)

/** Copies this Kotlin array into a new [TDoubleArray]. */
fun DoubleArray.toTornado(): TDoubleArray = TDoubleArray.fromArray(this)

/** Copies this [TDoubleArray] into a new Kotlin [DoubleArray]. */
fun TDoubleArray.toDoubleArray(): DoubleArray = toHeapArray()
