/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.profiler.ProfilerInterface;

/**
 * Object that stores all information related to profiling an executor. To be
 * able to return all timers, developers must enable the profiler from the
 * command line using the option:
 *
 * <p>
 * <code>
 * --enableProfiler <console|silent>
 * </code>
 * </p>
 *
 * <p>
 * Alternatively, the profiler can be enabled using the
 * {@link TornadoExecutionPlan#withProfiler(ProfilerMode)}.
 * </p>
 *
 * @since 0.15
 */
public class TornadoProfilerResult implements ProfilerInterface {

    private final TornadoExecutor executor;
    private String traceExecutionPlan;

    TornadoProfilerResult(TornadoExecutor executor, String traceExecutionPlan) {
        this.executor = executor;
        this.traceExecutionPlan = traceExecutionPlan;
    }

    /**
     * Returns the end-to-end time for all immutable task-graph to execute.
     *
     * @return long
     */
    @Override
    public long getTotalTime() {
        return executor.getTotalTime();
    }

    /**
     * Returns the JIT Compilation time for all immutable task-graphs associated to
     * the executor.
     *
     * @return long
     */
    @Override
    public long getCompileTime() {
        return executor.getCompileTime();
    }

    /**
     * Returns Tornado JIT Compilation time, in ns, (from Java bc to final step in
     * Graal LIR) for all immutable task-graphs associated to the executor.
     *
     * @return long
     */
    @Override
    public long getTornadoCompilerTime() {
        return executor.getTornadoCompilerTime();
    }

    /**
     * Returns the compilation time (in ns)that took the device driver (e.g., OpenCL
     * driver) to create the device binary (e.g., from OpenCL C to binary, or from
     * SPIR-V to binary).
     *
     * @return long
     */
    @Override
    public long getDriverInstallTime() {
        return executor.getDriverInstallTime();
    }

    /**
     * Returns the total data transfer time (in ns) for all immutable task-graphs to
     * perform copies from host to device and device to host.
     *
     * @return long
     */
    @Override
    public long getDataTransfersTime() {
        return executor.getDataTransfersTime();
    }

    /**
     * Return the total time for all immutable task-graphs that took to send data to
     * the device (host -> device).
     *
     * @return long
     */
    @Override
    public long getDeviceWriteTime() {
        return executor.getDeviceWriteTime();
    }

    /**
     * Return the total time (in ns) for all immutable task-graphs that took to
     * receive data to the host (device -> host).
     *
     * @return long
     */
    @Override
    public long getDeviceReadTime() {
        return executor.getDeviceReadTime();
    }

    /**
     * Returns the total time (in ns) that took for all immutable task-graphs to
     * dispatch the command to send and receive data. This depends on the driver
     * implementation.
     *
     * @return long
     */
    @Override
    public long getDataTransferDispatchTime() {
        return executor.getDataTransferDispatchTime();
    }

    /**
     * Returns the total time (in ns) that took all kernels to be dispatched by the
     * driver. This is mainly used for debugging purposes.
     *
     * @return long
     */
    @Override
    public long getKernelDispatchTime() {
        return executor.getKernelDispatchTime();
    }

    /**
     * Returns the total time (in ns) for all immutable task-graphs to run the
     * kernel. This metric is from the driver.
     *
     * @return long
     */
    @Override
    public long getDeviceKernelTime() {
        return executor.getDeviceKernelTime();
    }

    /**
     * Returns the profiler log in a JSON format for all the tasks within the
     * executor.
     *
     * @return String
     */
    @Override
    public String getProfileLog() {
        return executor.getProfileLog();
    }

    /**
     * Returns the total number of bytes that were transferred to the hardware
     * accelerator (host to device) for the current execution of the execution plan.
     * 
     * @return long
     *     Number of bytes
     */
    @Override
    public long getTotalBytesCopyIn() {
        return executor.getTotalBytesCopyIn();
    }

    /**
     * Returns the total number of bytes that were transferred to the host
     * (device to host) for the current execution of the execution plan.
     *
     * @return long
     *     Number of bytes
     */
    @Override
    public long getTotalBytesCopyOut() {
        return executor.getTotalBytesCopyOut();
    }

    TornadoExecutor getExecutor() {
        return executor;
    }

    /**
     * Dump in STDOUT all metrics associated to an execution. This is for debugging
     * purposes.
     */
    public void dumpProfiles() {
        getExecutor().dumpProfiles();
    }

    /**
     * Return the total number of bytes transferred to/from the target accelerators.
     * 
     * @return long
     *     Number of bytes
     */
    @Override
    public long getTotalBytesTransferred() {
        return executor.getTotalBytesTransferred();
    }

    /**
     * Return the total number of bytes allocated on the target device.
     * 
     * @return long
     *     Number of bytes.
     */
    @Override
    public long getTotalDeviceMemoryUsage() {
        return executor.getTotalDeviceMemoryUsage();
    }

    public String getTraceExecutionPlan() {
        return traceExecutionPlan;
    }

}
