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
@file:JvmName("TaskGraphs")

package uk.ac.manchester.tornado.kotlin.api

import uk.ac.manchester.tornado.api.GridScheduler
import uk.ac.manchester.tornado.api.ImmutableTaskGraph
import uk.ac.manchester.tornado.api.TaskGraph
import uk.ac.manchester.tornado.api.TornadoExecutionPlan
import uk.ac.manchester.tornado.api.WorkerGrid
import uk.ac.manchester.tornado.api.WorkerGrid1D
import uk.ac.manchester.tornado.api.WorkerGrid2D
import uk.ac.manchester.tornado.api.WorkerGrid3D

/**
 * Builds a [TaskGraph]. The block runs with the graph as receiver, so every method of the Java API
 * (`transferToDevice`, `task`, `transferToHost`, ...) is available unqualified:
 *
 * ```
 * val graph = taskGraph("s0") {
 *     transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
 *     task("t0", ::vectorAdd, a, b, c)
 *     transferToHost(DataTransferMode.EVERY_EXECUTION, c)
 * }
 * ```
 *
 * Kernels must be passed as direct function references (`::vectorAdd`) or lambdas at the call site,
 * so that Kotlin converts them to the TornadoVM task interfaces without an intermediate wrapper.
 */
inline fun taskGraph(name: String, block: TaskGraph.() -> Unit): TaskGraph = TaskGraph(name).apply(block)

/** Creates an execution plan for the given immutable task-graphs. */
fun executionPlan(vararg graphs: ImmutableTaskGraph): TornadoExecutionPlan = TornadoExecutionPlan.of(*graphs)

/**
 * Creates an execution plan and configures it. The block runs with the plan as receiver; the plan
 * returned is the root plan, which shares its configuration with every `with...` step:
 *
 * ```
 * executionPlan(graph.snapshot()) {
 *     withDevice(0, 0)
 *     withGridScheduler(gridScheduler("s0.t0" to workerGrid(n, local = 256)))
 * }.use { it.execute() }
 * ```
 */
inline fun executionPlan(vararg graphs: ImmutableTaskGraph, configure: TornadoExecutionPlan.() -> Unit): TornadoExecutionPlan =
    TornadoExecutionPlan.of(*graphs).apply(configure)

/** Snapshots this task-graph and creates an execution plan for it. */
fun TaskGraph.toExecutionPlan(): TornadoExecutionPlan = TornadoExecutionPlan.of(snapshot())

/** Selects the device by backend (driver) index and device index, as listed by `tornado --devices`. */
fun TornadoExecutionPlan.withDevice(backendIndex: Int, deviceIndex: Int): TornadoExecutionPlan =
    withDevice(TornadoExecutionPlan.getDevice(backendIndex, deviceIndex))

/** Creates a [GridScheduler] from `"graph.task" to workerGrid` pairs. */
fun gridScheduler(vararg grids: Pair<String, WorkerGrid>): GridScheduler =
    GridScheduler().apply { grids.forEach { (task, grid) -> addWorkerGrid(task, grid) } }

/** A 1D worker grid of [x] threads, optionally with a local work-group size. */
fun workerGrid(x: Int, local: Int? = null): WorkerGrid1D =
    WorkerGrid1D(x).apply { if (local != null) setLocalWork(local.toLong(), 1, 1) }

/** A 2D worker grid of [x] by [y] threads, optionally with a local work-group size. */
fun workerGrid(x: Int, y: Int, localX: Int? = null, localY: Int? = null): WorkerGrid2D =
    WorkerGrid2D(x, y).apply {
        if (localX != null || localY != null) setLocalWork((localX ?: 1).toLong(), (localY ?: 1).toLong(), 1)
    }

/** A 3D worker grid of [x] by [y] by [z] threads, optionally with a local work-group size. */
fun workerGrid(x: Int, y: Int, z: Int, localX: Int? = null, localY: Int? = null, localZ: Int? = null): WorkerGrid3D =
    WorkerGrid3D(x, y, z).apply {
        if (localX != null || localY != null || localZ != null) {
            setLocalWork((localX ?: 1).toLong(), (localY ?: 1).toLong(), (localZ ?: 1).toLong())
        }
    }
