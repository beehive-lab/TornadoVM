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
@file:JvmName("TileSupport")

package uk.ac.manchester.tornado.kotlin.examples.tile

import uk.ac.manchester.tornado.api.GridScheduler
import uk.ac.manchester.tornado.api.TaskGraph
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider
import uk.ac.manchester.tornado.kotlin.api.toExecutionPlan

/** CUDA Tile kernels need the CUDA backend (plus CUDA Toolkit 13.3+, driver R580+, compute capability 8.0+). */
fun cudaTileBackend(): Boolean =
    TornadoRuntimeProvider.getTornadoRuntime().getBackendType(0) == TornadoVMBackendType.CUDA

/**
 * Builds the task-graph with [graph] and runs it on the device when the CUDA backend is available,
 * returning its median time in milliseconds. Otherwise returns null without building the graph
 * (adding a tile task already fails on other backends), and the caller runs the kernel on the JVM.
 */
inline fun runOnDevice(grid: GridScheduler, iterations: Int = 10, graph: () -> TaskGraph): Double? {
    if (!cudaTileBackend()) {
        return null
    }
    graph().toExecutionPlan().use { plan ->
        plan.withGridScheduler(grid)
        plan.execute()
        val times = List(iterations) {
            val start = System.nanoTime()
            plan.execute()
            (System.nanoTime() - start) / 1e6
        }
        return times.sorted()[iterations / 2]
    }
}

fun report(name: String, deviceMs: Double?, correct: Boolean) {
    val where = if (deviceMs != null) "CUDA Tile, %.3f ms".format(deviceMs) else "JVM fallback (CUDA Tile needs the CUDA backend)"
    println("$name [$where]: ${if (correct) "correct" else "WRONG"}")
}
