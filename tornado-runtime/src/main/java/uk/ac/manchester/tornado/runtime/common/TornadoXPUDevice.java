/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package uk.ac.manchester.tornado.runtime.common;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.memory.XPUBuffer;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;

/**
 * A Tornado accelerator device extending the {@link TornadoDevice} interface.
 */
public interface TornadoXPUDevice extends TornadoDevice {

    /**
     * It returns the preferred scheduling strategy for the Tornado accelerator device.
     *
     * @return The preferred scheduling strategy.
     */
    TornadoSchedulingStrategy getPreferredSchedule();

    /**
     * It creates a call wrapper for the kernel with the specified number of
     * arguments.
     *
     * @param numArgs
     *     The number of arguments for the kernel call wrapper.
     * @return The created {@link KernelStackFrame} object.
     */
    KernelStackFrame createKernelStackFrame(long executionId, int numArgs, Access access);

    /**
     * It creates or reuses an atomic buffer for the specified integer array.
     *
     * @param arr
     *     The integer array for which to create or reuse an atomic buffer.
     * @return The created or reused {@link XPUBuffer}.
     */
    XPUBuffer createOrReuseAtomicsBuffer(int[] arr, Access access);

    /**
     * It installs the Tornado code for the specified schedulable task.
     *
     * @param executionPlanId
     *     ID number for the execution plan that the task belongs to.
     * @param task
     *     The {@link SchedulableTask} to install the code for.
     * @return The {@link TornadoInstalledCode} indicating the installation status.
     */
    TornadoInstalledCode installCode(long executionPlanId, SchedulableTask task);

    /**
     * It checks if the specified schedulable task is in full Just-In-Time (JIT)
     * mode.
     *
     * @param executionPlanId
     *     ID for the execution plan to be compiled.
     * @param task
     *     The {@link SchedulableTask} to check for full JIT mode.
     * @return True if the task is in full JIT mode, false otherwise.
     */
    boolean isFullJITMode(long executionPlanId, SchedulableTask task);

    /**
     * It retrieves the Tornado installed code from the cache for the specified
     * schedulable task.
     *
     * @param executionPlanId
     *     ID for the execution plan to be obtained from the code cache
     * @param task
     *     The {@link SchedulableTask} to get the installed code from the
     *     cache.
     * @return The {@link TornadoInstalledCode} from the cache.
     */
    TornadoInstalledCode getCodeFromCache(long executionPlanId, SchedulableTask task);

    /**
     * It checks for atomic operations in the specified schedulable task and returns
     * an integer array representing them.
     *
     * @param task
     *     The {@link SchedulableTask} to check for atomic operations.
     * @return The integer array representing the atomic operations.
     */
    int[] checkAtomicsForTask(SchedulableTask task);

    /**
     * It checks for atomic operations in the specified schedulable task, array,
     * parameter index, and value.
     *
     * @param task
     *     The {@link SchedulableTask} to check for atomic operations.
     * @param array
     *     The array to check for atomic operations.
     * @param paramIndex
     *     The parameter index to check for atomic operations.
     * @param value
     *     The value to check for atomic operations.
     * @return The integer array representing the atomic operations.
     */
    int[] checkAtomicsForTask(SchedulableTask task, int[] array, int paramIndex, Object value);

    /**
     * It updates the atomic region and object state for the specified schedulable
     * task, array, parameter index, value, and device object state.
     *
     * @param task
     *     The {@link SchedulableTask} to update the atomic region and object
     *     state.
     * @param array
     *     The array to update the atomic region and object state.
     * @param paramIndex
     *     The parameter index to update the atomic region and object state.
     * @param value
     *     The value to update the atomic region and object state.
     * @param objectState
     *     The {@link XPUDeviceBufferState} to update the atomic region and
     *     object state.
     * @return The integer array representing the updated atomic region and object
     *     state.
     */
    int[] updateAtomicRegionAndObjectState(SchedulableTask task, int[] array, int paramIndex, Object value, XPUDeviceBufferState objectState);

    /**
     * It gets the global index of atomic operations for the specified schedulable
     * task and parameter index.
     *
     * @param task
     *     The schedulable task to get the global index of atomic operations.
     * @param paramIndex
     *     The parameter index to get the global index of atomic operations.
     * @return The global index of atomic operations.
     */
    int getAtomicsGlobalIndexForTask(SchedulableTask task, int paramIndex);

    /**
     * It checks if there are atomic parameters in the specified schedulable task.
     *
     * @param task
     *     The {@link SchedulableTask} to check for atomic parameters.
     * @return True if there are atomic parameters, false otherwise.
     */
    boolean checkAtomicsParametersForTask(SchedulableTask task);

    /**
     * In CUDA, the context is not attached to the whole process, but to individual
     * threads. Therefore, in the case of new threads executing a task schedule, we
     * must make sure that the context is set for that thread.
     */
    void enableThreadSharing();

    /**
     * It sets the atomic region for the Tornado accelerator device using the
     * specified {@link XPUBuffer}.
     *
     * @param bufferAtomics
     *     The {@link XPUBuffer} representing the atomic region.
     */
    void setAtomicRegion(XPUBuffer bufferAtomics);

    /**
     * It returns from the sketch of a task whether the loop index is written in the output buffer.
     * 
     * @param task
     *     {@link SchedulableTask}
     * @return
     */
    default boolean loopIndexInWrite(SchedulableTask task) {
        if (task instanceof CompilableTask executable) {
            final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getTornadoRuntime().resolveMethod(executable.getMethod());
            final Sketch sketch = TornadoSketcher.lookup(resolvedMethod, task.meta().getBackendIndex(), task.meta().getDeviceIndex());
            return sketch.getBatchWriteThreadIndex();
        } else {
            return false;
        }
    }

}
