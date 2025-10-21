/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api;

/**
 * Object created when the {@link TornadoExecutionPlan#execute()} is finished.
 * This objects stores the results of the execution. Additionally, if the
 * execution plan enabled the profiler information, this object also stores all
 * profiler information (e.g., read/write time, kernel time, etc.) through the
 * {@link TornadoProfilerResult} object.
 *
 * @since 0.15.0
 */
public class TornadoExecutionResult {

    private final TornadoProfilerResult tornadoProfilerResult;

    TornadoExecutionResult(TornadoProfilerResult profilerResult) {
        this.tornadoProfilerResult = profilerResult;
    }

    /**
     * Method to obtain the profiler information associated to the latest execution
     * plan. Note that, all timers associated to the profiler are enabled only if
     * the execution plan enables the profiler.
     *
     * @return {@link TornadoProfilerResult}
     *
     * @since 0.15.0
     */
    public TornadoProfilerResult getProfilerResult() {
        return tornadoProfilerResult;
    }

    /**
     * Transfer data from device to host. This is applied for all immutable
     * task-graphs within an executor. This method is used when a task-graph defines
     * transferToHost using the
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#UNDER_DEMAND}.
     * This indicates the runtime to not to copy-out the data en every iteration and
     * transfer the data under demand.
     *
     * @param objects
     *     Host objects to transfer the data to.
     *
     * @return {@link TornadoExecutionResult}
     *
     * @since 0.15.0
     */
    public TornadoExecutionResult transferToHost(Object... objects) {
        tornadoProfilerResult.getExecutor().transferToHost(objects);
        return this;
    }

    /**
     * Partial data transfer from the device to the host. This is applied for all immutable
     * task-graphs within an executor. This indicates the runtime to not to copy-out the data
     * en every iteration and transfer the data under demand. The sub-region to be copied is
     * specified in the data range.
     *
     * @param dataRange
     *     Range of type: {@link DataRange}
     * @return {@link TornadoExecutionResult}
     *
     * @since v1.0.1
     */
    public TornadoExecutionResult transferToHost(DataRange dataRange) {
        tornadoProfilerResult.getExecutor().partialTransferToHost(dataRange);
        return this;
    }

    /**
     * Transfer data from host to device. This is applied for all immutable
     * task-graphs within an executor. This method is used when a task-graph defines
     * transferToDevice using the
     * {@link uk.ac.manchester.tornado.api.enums.DataTransferMode#UNDER_DEMAND}.
     * This indicates the runtime to not to copy-in the data on every iteration and
     * transfer the data under demand.
     *
     * @param objects
     *     Host objects to transfer the data from.
     *
     * @return {@link TornadoExecutionResult}
     *
     * @since v1.0.2
     */
    public TornadoExecutionResult transferToDevice(Object... objects) {
        tornadoProfilerResult.getExecutor().transferToDevice(objects);
        return this;
    }

    /**
     * Partial data transfer from the host to the device. This is applied for all immutable
     * task-graphs within an executor. This indicates the runtime to not to copy-in the data
     * on every iteration and transfer the data under demand. The sub-region to be copied is
     * specified in the data range.
     *
     * @param dataRange
     *     Range of type: {@link DataRange}
     * @return {@link TornadoExecutionResult}
     *
     * @since v1.0.2
     */
    public TornadoExecutionResult transferToDevice(DataRange dataRange) {
        tornadoProfilerResult.getExecutor().partialTransferToDevice(dataRange);
        return this;
    }

    /**
     * It returns true if all task-graphs associated to the executor finished
     * execution.
     *
     * @return boolean
     *
     * @since 0.15.0
     */
    public boolean isReady() {
        return tornadoProfilerResult.getExecutor().isFinished();
    }

}
