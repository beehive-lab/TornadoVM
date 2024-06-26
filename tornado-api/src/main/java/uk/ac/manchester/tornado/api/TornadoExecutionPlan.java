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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;

/**
 * Object to create and optimize an execution plan for running a set of
 * immutable tasks-graphs. An executor plan contains an executor object, which
 * in turn, contains a set of immutable task-graphs. All actions applied to the
 * execution plan affect to all the immutable graphs associated with it.
 *
 * @since TornadoVM-0.15
 */
public class TornadoExecutionPlan implements AutoCloseable {

    /**
     * Method to obtain the default device in TornadoVM. The default one corresponds
     * to the device assigned to the driver (backend) with index 0 and device 0.
     */
    public static TornadoDevice DEFAULT_DEVICE = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice();
    private final TornadoExecutor tornadoExecutor;

    private ProfilerMode profilerMode;
    private boolean disableProfiler;

    private static final AtomicLong globalExecutionPlanCounter = new AtomicLong(0);

    private final ExecutorFrame executionPackage;

    /**
     * Create an Execution Plan: Object to create and optimize an execution plan for
     * running a set of immutable tasks-graphs. An executor plan contains an
     * executor object, which in turn, contains a set of immutable task-graphs. All
     * actions applied to the execution plan affect to all the immutable graphs
     * associated with it.
     *
     * @param immutableTaskGraphs
     *     {@link ImmutableTaskGraph}
     */
    public TornadoExecutionPlan(ImmutableTaskGraph... immutableTaskGraphs) {
        this.tornadoExecutor = new TornadoExecutor(immutableTaskGraphs);
        long id = globalExecutionPlanCounter.incrementAndGet();
        executionPackage = new ExecutorFrame(id);
    }

    /**
     * Method to obtain a specific device using the driver index (backend index) and
     * device index.
     *
     * @param driverIndex
     *     Integer value that identifies the backend to be used.
     * @param deviceIndex
     *     Integer value that identifies the device within the backend to be
     *     used.
     * @return {@link TornadoDevice}
     *
     */
    public static TornadoDevice getDevice(int driverIndex, int deviceIndex) {
        return TornadoRuntimeProvider.getTornadoRuntime().getBackend(driverIndex).getDevice(deviceIndex);
    }

    public static int getTotalPlans() {
        return globalExecutionPlanCounter.intValue();
    }

    /**
     * Return a data structure that contains all drivers and devices that the TornadoVM Runtime can access.
     * 
     * @return {@link TornadoDeviceMap}
     */
    public static TornadoDeviceMap getTornadoDeviceMap() {
        return new TornadoDeviceMap();
    }

    /**
     * Execute an execution plan. It returns a {@link TornadoExecutionPlan} for
     * further build different optimization after the execution as well as obtain
     * the profiler results.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionResult execute() {
        checkProfilerEnabled();
        tornadoExecutor.execute(executionPackage);
        return new TornadoExecutionResult(new TornadoProfilerResult(tornadoExecutor));
    }

    private void checkProfilerEnabled() {
        if (this.profilerMode != null && !this.disableProfiler) {
            tornadoExecutor.enableProfiler(profilerMode);
        } else if (this.profilerMode != null) {
            tornadoExecutor.disableProfiler(profilerMode);
        }
    }

    /**
     * It invokes the JIT compiler for all immutable tasks-graphs associated to an
     * executor.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withWarmUp() {
        checkProfilerEnabled();
        tornadoExecutor.warmup();
        return this;
    }

    /**
     * It selects a specific device for all immutable tasks graphs associated to an
     * executor.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDevice(TornadoDevice device) {
        tornadoExecutor.setDevice(device);
        return this;
    }

    /**
     * It selects a specific device for one particular task of the task-graph.
     *
     * @param taskName
     *     The task-name is identified by the task-graph name followed by a dot (".") and
     *     the task name. For example: "graph.task1".
     * @param device
     *     The device is an instance of a {@link TornadoDevice}
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDevice(String taskName, TornadoDevice device) {
        tornadoExecutor.setDevice(taskName, device);
        return this;
    }

    /**
     * It enables multiple tasks in a task graph to run concurrently on the same
     * or different devices. Note that the TornadoVM runtime does not check for
     * data dependencies across tasks when using this API call. Thus, it is
     * the responsibility of the programmer to provide tasks with no data dependencies
     * when invoking the method {@link TornadoExecutionPlan#withConcurrentDevices()}.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withConcurrentDevices() {
        tornadoExecutor.withConcurrentDevices();
        return this;
    }

    /**
     * It disables multiple tasks in a task graph to run concurrently on the same
     * or different devices.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutConcurrentDevices() {
        tornadoExecutor.withoutConcurrentDevices();
        return this;
    }

    /**
     * It obtains the device for a specific immutable task-graph. Note that,
     * ideally, different task immutable task-graph could be executed on different
     * devices.
     *
     * @param immutableTaskGraphIndex
     *     Index of a specific immutable task-graph
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoDevice getDevice(int immutableTaskGraphIndex) {
        return tornadoExecutor.getDevice(immutableTaskGraphIndex);
    }

    /**
     * Mark all device buffers that correspond to the current execution plan as free
     * in order for the TornadoVM runtime system to reuse those buffers and avoid
     * continuous device memory deallocation and allocation.
     *
     * <p>
     * Note that, in this context, "free device memory" means the TornadoVM runtime
     * system marks device buffers to be reusable, thus, for the runtime system,
     * device buffers are no longer linked to the current execution plan.
     * </p>
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan freeDeviceMemory() {
        tornadoExecutor.freeDeviceMemory();
        return this;
    }

    /**
     * Use a {@link GridScheduler} for thread dispatch. The same GridScheduler will
     * be applied to all tasks within the executor. Note that the grid-scheduler API
     * can specify all workers for each task-graph.
     *
     * @param gridScheduler
     *     {@link GridScheduler}
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withGridScheduler(GridScheduler gridScheduler) {
        tornadoExecutor.withGridScheduler(gridScheduler);
        return this;
    }

    /**
     * Notify the TornadoVM runtime system to utilize the default thread scheduler.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDefaultScheduler() {
        tornadoExecutor.withDefaultScheduler();
        return this;
    }

    /**
     * Use the TornadoVM dynamic reconfiguration (akka live task migration) across
     * visible devices.
     *
     * @param policy
     *     {@link Policy}
     * @param mode
     *     {@link DRMode}
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDynamicReconfiguration(Policy policy, DRMode mode) {
        executionPackage.withPolicy(policy).withMode(mode);
        return this;
    }

    /**
     * Enable batch processing. TornadoVM will split the iteration space in smaller
     * batches (with batch size specified by the user). This is used mainly when
     * users want to execute big data applications that do not fit on the device's
     * global memory.
     *
     * @param batchSize
     *     String in the format a number + "MB" Example "512MB".
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withBatch(String batchSize) {
        tornadoExecutor.withBatch(batchSize);
        return this;
    }

    /**
     * Enables the profiler. The profiler includes options to query device kernel
     * time, data transfers and compilation at different stages (JIT, driver
     * compilation, Graal, etc.).
     *
     * @param profilerMode
     *     {@link ProfilerMode}
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withProfiler(ProfilerMode profilerMode) {
        this.profilerMode = profilerMode;
        disableProfiler = false;
        return this;
    }

    /**
     * Disables the profiler if previous execution plan had the profiler enabled.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutProfiler() {
        this.disableProfiler = true;
        return this;
    }

    /**
     * This method sets a limit to the amount of memory used on the target
     * hardware accelerator. The TornadoVM runtime will check that the
     * current instance of the {@link TornadoExecutionPlan} does not exceed
     * the limit that was specified.
     *
     * @param memoryLimit
     *     Specify the limit in a string format. E.g., "1GB", "512MB".
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withMemoryLimit(String memoryLimit) {
        tornadoExecutor.withMemoryLimit(memoryLimit);
        return this;
    }

    /**
     * It disables the memory limit for the current instance of an
     * {@link TornadoExecutionPlan}. This is the default action.
     * If the memory limit is not set, then the maximum memory to use
     * is set to the maximum buffer allocation (e.g., 1/4 of the total
     * capacity using the OpenCL backend), or the maximum memory available
     * on the target device.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutMemoryLimit() {
        tornadoExecutor.withoutMemoryLimit();
        return this;
    }

    /**
     * Reset the execution context for the current execution plan. The TornadoVM
     * runtime system will clean the code cache and all events associated with the
     * current execution. It resets the internal GPU/FPGA/CPU execution context to
     * its default values.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan resetDevice() {
        tornadoExecutor.resetDevice();
        return this;
    }

    /**
     * Obtains the ID that was assigned to the execution plan.
     */
    public long getId() {
        return executionPackage.getExecutionPlanId();
    }

    /**
     * Obtains the total number of execution plans instantiated in a TornadoVM application.
     */
    public long getGlobalExecutionPlansCounter() {
        return globalExecutionPlanCounter.get();
    }

    /**
     * Clean all events associated with previous executions.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan clearProfiles() {
        tornadoExecutor.clearProfiles();
        return this;
    }

    public TornadoExecutionPlan withThreadInfo() {
        tornadoExecutor.withThreadInfo();
        return this;
    }

    public TornadoExecutionPlan withoutThreadInfo() {
        tornadoExecutor.withoutThreadInfo();
        return this;
    }

    public TornadoExecutionPlan withPrintKernel() {
        tornadoExecutor.withPrintKernel();
        return this;
    }

    public TornadoExecutionPlan withoutPrintKernel() {
        tornadoExecutor.withoutPrintKernel();
        return this;
    }

    @Override
    public void close() throws TornadoExecutionPlanException {
        tornadoExecutor.freeDeviceMemory();
    }

    /**
     * It returns the current memory usage on the device in bytes.
     * 
     * @return long
     *     Number of bytes used.
     */
    public long getCurrentDeviceMemoryUsage() {
        return tornadoExecutor.getCurrentDeviceMemoryUsage();
    }

    static class TornadoExecutor {

        private final List<ImmutableTaskGraph> immutableTaskGraphList;

        TornadoExecutor(ImmutableTaskGraph... immutableTaskGraphs) {
            immutableTaskGraphList = new ArrayList<>();
            Collections.addAll(immutableTaskGraphList, immutableTaskGraphs);
        }

        void execute(ExecutorFrame executionPackage) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.execute(executionPackage));
        }

        void withGridScheduler(GridScheduler gridScheduler) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.withGridScheduler(gridScheduler));
        }

        void warmup() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::warmup);
        }

        void withBatch(String batchSize) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.withBatch(batchSize));
        }

        void withMemoryLimit(String memoryLimit) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.withMemoryLimit(memoryLimit));
        }

        public void withoutMemoryLimit() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::withoutMemoryLimit);
        }

        /**
         * For all task-graphs contained in an Executor, update the device.
         *
         * @param device
         *     {@link TornadoDevice} object
         */
        void setDevice(TornadoDevice device) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.withDevice(device));
        }

        void setDevice(String taskName, TornadoDevice device) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.withDevice(taskName, device));
        }

        void withConcurrentDevices() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::withConcurrentDevices);
        }

        void withoutConcurrentDevices() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::withoutConcurrentDevices);
        }

        void freeDeviceMemory() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::freeDeviceMemory);
        }

        void transferToHost(Object... objects) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.transferToHost(objects));
        }

        void partialTransferToHost(DataRange dataRange) {
            // At this point we compute the offsets and the total size in bytes.
            dataRange.materialize();
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.transferToHost(dataRange.getArray(), dataRange.getOffset(), dataRange.getPartialSize()));
        }

        boolean isFinished() {
            boolean result = true;
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
                result &= immutableTaskGraph.isFinished();
            }
            return result;
        }

        void resetDevice() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::resetDevice);
        }

        long getTotalTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getTotalTime).mapToLong(Long::longValue).sum();
        }

        long getCompileTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getCompileTime).mapToLong(Long::longValue).sum();
        }

        long getTornadoCompilerTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getTornadoCompilerTime).mapToLong(Long::longValue).sum();
        }

        long getDriverInstallTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDriverInstallTime).mapToLong(Long::longValue).sum();
        }

        long getDataTransfersTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDataTransfersTime).mapToLong(Long::longValue).sum();
        }

        long getDeviceWriteTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDeviceWriteTime).mapToLong(Long::longValue).sum();
        }

        long getDeviceReadTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDeviceReadTime).mapToLong(Long::longValue).sum();
        }

        long getDataTransferDispatchTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDataTransferDispatchTime).mapToLong(Long::longValue).sum();
        }

        long getKernelDispatchTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getKernelDispatchTime).mapToLong(Long::longValue).sum();
        }

        long getDeviceKernelTime() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getDeviceKernelTime).mapToLong(Long::longValue).sum();
        }

        long getTotalBytesCopyIn() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getTotalBytesCopyIn).mapToLong(Long::longValue).sum();
        }

        long getTotalBytesCopyOut() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getTotalBytesCopyOut).mapToLong(Long::longValue).sum();
        }

        String getProfileLog() {
            return immutableTaskGraphList.stream().map(ImmutableTaskGraph::getProfileLog).collect(Collectors.joining());
        }

        void dumpProfiles() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::dumpProfiles);
        }

        void clearProfiles() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::clearProfiles);
        }

        void withDefaultScheduler() {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.withDefaultScheduler(true));
        }

        TornadoDevice getDevice(int immutableTaskGraphIndex) {
            if (immutableTaskGraphList.size() < immutableTaskGraphIndex) {
                throw new TornadoRuntimeException(STR."TaskGraph index #\{immutableTaskGraphIndex} does not exist in current executor");
            }
            return immutableTaskGraphList.get(immutableTaskGraphIndex).getDevice();
        }

        List<Object> getOutputs() {
            List<Object> outputs = new ArrayList<>();
            immutableTaskGraphList.forEach(immutableTaskGraph -> outputs.addAll(immutableTaskGraph.getOutputs()));
            return outputs;
        }

        void enableProfiler(ProfilerMode profilerMode) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.enableProfiler(profilerMode));
        }

        void disableProfiler(ProfilerMode profilerMode) {
            immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.disableProfiler(profilerMode));
        }

        void withThreadInfo() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::withThreadInfo);
        }

        void withoutThreadInfo() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::withoutThreadInfo);
        }

        void withPrintKernel() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::withPrintKernel);
        }

        void withoutPrintKernel() {
            immutableTaskGraphList.forEach(ImmutableTaskGraph::withoutPrintKernel);
        }

        long getTotalBytesTransferred() {
            return immutableTaskGraphList.stream().mapToLong(ImmutableTaskGraph::getTotalBytesTransferred).sum();
        }

        long getTotalDeviceMemoryUsage() {
            return immutableTaskGraphList.stream().mapToLong(ImmutableTaskGraph::getTotalDeviceMemoryUsage).sum();
        }

        long getCurrentDeviceMemoryUsage() {
            return immutableTaskGraphList.stream().mapToLong(ImmutableTaskGraph::getCurrentDeviceMemoryUsage).sum();
        }
    }
}
