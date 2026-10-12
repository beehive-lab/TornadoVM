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
package uk.ac.manchester.tornado.kotlin.unittests.hybrid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import uk.ac.manchester.tornado.api.common.Access
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor
import uk.ac.manchester.tornado.api.enums.DataTransferMode
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType
import uk.ac.manchester.tornado.cublas.CuBlas
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation
import uk.ac.manchester.tornado.cublas.provider.CuBlasLibraryProvider
import uk.ac.manchester.tornado.kotlin.api.TFloatArray
import uk.ac.manchester.tornado.kotlin.api.parallelFor
import uk.ac.manchester.tornado.kotlin.api.taskGraph
import uk.ac.manchester.tornado.kotlin.api.toExecutionPlan
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported

private const val SIZE = 64

// A library binding written in Kotlin: the factory a provider exposes, like CuBlas.cublasSgemm.
fun kotlinScaleBinding(output: TFloatArray, input: TFloatArray, alpha: Float): LibraryTaskDescriptor =
    LibraryTaskDescriptor()
        .withLibrary("kotlin-test")
        .withFunction("scale")
        .withParameters(arrayOf(output, input, alpha))
        .withAccess(arrayOf(Access.WRITE_ONLY, Access.READ_ONLY, Access.READ_ONLY))

// JIT kernels around the library call.
fun addOne(a: TFloatArray) {
    parallelFor(0, a.size) { i ->
        a[i] = a[i] + 1f
    }
}

fun scaleByTwo(input: TFloatArray, output: TFloatArray) {
    parallelFor(0, input.size) { i ->
        output[i] = input[i] * 2f
    }
}

private fun matrixMultiply(a: TFloatArray, b: TFloatArray, n: Int): TFloatArray {
    val c = TFloatArray(n * n)
    for (i in 0 until n) {
        for (j in 0 until n) {
            var sum = 0f
            for (k in 0 until n) {
                sum += a[i * n + k] * b[k * n + j]
            }
            c[i * n + j] = sum
        }
    }
    return c
}

/**
 * The hybrid API (library tasks, e.g. cuBLAS) used from Kotlin. Building graphs works on every
 * backend; executing native library tasks needs the CUDA backend and is reported as
 * [UNSUPPORTED] elsewhere, as for the Java cuBLAS tests.
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.kotlin.unittests.hybrid.TestKotlinHybridApi
 * </code>
 */
class TestKotlinHybridApi : TornadoTestBase() {

    private fun cuBlasMustBeAvailable() {
        val backendType = getTornadoRuntime().defaultDevice.tornadoVMBackend
        if (backendType != TornadoVMBackendType.CUDA) {
            val message = "cuBLAS library tasks require the CUDA backend (default device is $backendType)"
            when (backendType) {
                TornadoVMBackendType.OPENCL, TornadoVMBackendType.METAL -> assertNotBackend(backendType, message)
                else -> throw TornadoVMCUDANotSupported(message)
            }
        }
        if (!CuBlasLibraryProvider.isAvailable()) {
            throw TornadoVMCUDANotSupported("cuBLAS is not available on this host")
        }
    }

    @Test
    fun testKotlinLibraryBinding() {
        val input = TFloatArray(SIZE) { it.toFloat() }
        val output = TFloatArray(SIZE)

        val graph = taskGraph("hybrid") {
            transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
            libraryTask("scale", ::kotlinScaleBinding, output, input, 3f)
            transferToHost(DataTransferMode.EVERY_EXECUTION, output)
        }
        assertNotNull(graph.snapshot())

        val descriptor = kotlinScaleBinding(output, input, 3f)
        assertEquals("kotlin-test", descriptor.libraryName)
        assertEquals("scale", descriptor.functionName)
        assertArrayEquals(arrayOf(output, input, 3f), descriptor.parameters)
    }

    @Test
    fun testCuBlasGraphFromKotlin() {
        val a = TFloatArray(SIZE * SIZE) { 1f }
        val b = TFloatArray(SIZE * SIZE) { 1f }
        val c = TFloatArray(SIZE * SIZE)
        val op = CuBlasOperation.CUBLAS_OP_N.operation()

        val graph = taskGraph("hybrid") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
            libraryTask("sgemm", CuBlas::cublasSgemm, op, op, SIZE, SIZE, SIZE, 1f, b, SIZE, a, SIZE, 0f, c, SIZE)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }
        assertNotNull(graph.snapshot())
    }

    @Test
    fun testCuBlasSgemm() {
        cuBlasMustBeAvailable()
        val a = TFloatArray(SIZE * SIZE) { (it % 7) / 7f }
        val b = TFloatArray(SIZE * SIZE) { (it % 5) / 5f }
        val c = TFloatArray(SIZE * SIZE)
        val op = CuBlasOperation.CUBLAS_OP_N.operation()

        taskGraph("hybrid") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
            // cuBLAS is column-major: C^T = B^T A^T gives the row-major C = A B.
            libraryTask("sgemm", CuBlas::cublasSgemm, op, op, SIZE, SIZE, SIZE, 1f, b, SIZE, a, SIZE, 0f, c, SIZE)
            transferToHost(DataTransferMode.EVERY_EXECUTION, c)
        }.toExecutionPlan().use { it.execute() }

        val expected = matrixMultiply(a, b, SIZE)
        for (i in 0 until SIZE * SIZE) {
            assertEquals(expected[i], c[i], 0.01f * maxOf(1f, Math.abs(expected[i])))
        }
    }

    @Test
    fun testKotlinKernelsAroundCuBlas() {
        cuBlasMustBeAvailable()
        val a = TFloatArray(SIZE * SIZE) { (it % 3).toFloat() }
        val b = TFloatArray(SIZE * SIZE) { (it % 4) / 4f }
        val c = TFloatArray(SIZE * SIZE)
        val d = TFloatArray(SIZE * SIZE)
        val op = CuBlasOperation.CUBLAS_OP_N.operation()

        // Kotlin JIT kernel -> cuBLAS -> Kotlin JIT kernel, sharing device buffers in one graph.
        taskGraph("hybrid") {
            transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
            task("addOne", ::addOne, a)
            libraryTask("sgemm", CuBlas::cublasSgemm, op, op, SIZE, SIZE, SIZE, 1f, b, SIZE, a, SIZE, 0f, c, SIZE)
            task("scale", ::scaleByTwo, c, d)
            transferToHost(DataTransferMode.EVERY_EXECUTION, d)
        }.toExecutionPlan().use { it.execute() }

        val aPlusOne = TFloatArray(SIZE * SIZE) { (it % 3) + 1f }
        val expected = matrixMultiply(aPlusOne, b, SIZE)
        for (i in 0 until SIZE * SIZE) {
            assertEquals(2f * expected[i], d[i], 0.02f * maxOf(1f, Math.abs(expected[i])))
        }
    }
}
