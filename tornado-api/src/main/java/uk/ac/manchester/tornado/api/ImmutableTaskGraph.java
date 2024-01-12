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

import java.util.Collection;
import java.util.Objects;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;

/**
 * A {@link TaskGraph} is encapsulated in this class and all actions over a task
 * graph are coded from this class. For instance, execution.
 *
 * <p>
 * This class does not allow a task-graph to mutate (e.g., add/remove tasks or
 * data from the graph itself). To mutate a graph, we need to create a new
 * {@link ImmutableTaskGraph} object from a mutated {@TaskGraph}. Note that,
 * developers can mutate task-graph objects (of type {@TaskGraph}) without
 * affecting the execution of graphs encapsulated in {@link ImmutableTaskGraph}.
 * </p>
 *
 * @since TornadoVM-0.15
 */
public class ImmutableTaskGraph {

    private final TaskGraph taskGraph;

    ImmutableTaskGraph(TaskGraph taskGraph) {
        this.taskGraph = taskGraph;
    }

    void execute() {
        this.taskGraph.execute();
    }

    void execute(GridScheduler gridScheduler) {
        taskGraph.execute(gridScheduler);
    }

    void executeWithDynamicReconfiguration(Policy policy, DRMode mode) {
        if (Objects.requireNonNull(mode) == DRMode.SERIAL) {
            taskGraph.executeWithProfilerSequential(policy);
        } else if (mode == DRMode.PARALLEL) {
            taskGraph.executeWithProfiler(policy);
        }
    }

    void warmup() {
        taskGraph.warmup();
    }

    void setDevice(TornadoDevice device) {
        taskGraph.setDevice(device);
    }

    void setDevice(String taskName, TornadoDevice device) {
        taskGraph.setDevice(taskName, device);
    }

    void freeDeviceMemory() {
        taskGraph.freeDeviceMemory();
    }

    void transferToHost(Object... objects) {
        taskGraph.syncRuntimeTransferToHost(objects);
    }

    void transferToHost(Object object, long offset, long partialCopySize) {
        taskGraph.syncRuntimeTransferToHost(object, offset, partialCopySize);
    }

    long getTotalTime() {
        return taskGraph.getTotalTime();
    }

    long getCompileTime() {
        return taskGraph.getCompileTime();
    }

    long getTornadoCompilerTime() {
        return taskGraph.getTornadoCompilerTime();
    }

    long getDriverInstallTime() {
        return taskGraph.getDriverInstallTime();
    }

    long getDataTransfersTime() {
        return taskGraph.getDataTransfersTime();
    }

    long getDeviceWriteTime() {
        return taskGraph.getWriteTime();
    }

    long getDeviceReadTime() {
        return taskGraph.getReadTime();
    }

    long getDataTransferDispatchTime() {
        return taskGraph.getDataTransferDispatchTime();
    }

    long getKernelDispatchTime() {
        return taskGraph.getKernelDispatchTime();
    }

    long getDeviceKernelTime() {
        return taskGraph.getDeviceKernelTime();
    }

    String getProfileLog() {
        return taskGraph.getProfileLog();
    }

    boolean isFinished() {
        return taskGraph.isFinished();
    }

    void dumpProfiles() {
        taskGraph.dumpProfiles();
    }

    void resetDevice() {
        taskGraph.getDevice().reset();
    }

    void clearProfiles() {
        taskGraph.clearProfiles();
    }

    void useDefaultScheduler(boolean useDefaultScheduler) {
        taskGraph.useDefaultThreadScheduler(useDefaultScheduler);
    }

    void withBatch(String batchSize) {
        taskGraph.batch(batchSize);
    }

    TornadoDevice getDevice() {
        return taskGraph.getDevice();
    }

    Collection<?> getOutputs() {
        return taskGraph.getOutputs();
    }

    void enableProfiler(ProfilerMode profilerMode) {
        taskGraph.enableProfiler(profilerMode);
    }

    void disableProfiler(ProfilerMode profilerMode) {
        taskGraph.disableProfiler(profilerMode);
    }

    public void withConcurrentDevices() {
        taskGraph.withConcurrentDevices();
    }

    public void withoutConcurrentDevices() {
        taskGraph.withoutConcurrentDevices();
    }
}
