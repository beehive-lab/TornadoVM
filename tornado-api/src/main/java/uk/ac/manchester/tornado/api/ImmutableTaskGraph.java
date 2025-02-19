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

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;

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
 * @since 0.15
 */
public class ImmutableTaskGraph {

    private final TaskGraph taskGraph;

    ImmutableTaskGraph(TaskGraph taskGraph) {
        this.taskGraph = taskGraph;
    }

    void execute(ExecutorFrame executionPackage) {
        this.taskGraph.execute(executionPackage);
    }

    void warmup(ExecutorFrame executionPackage) {
        taskGraph.warmup(executionPackage);
    }

    void withDevice(TornadoDevice device) {
        taskGraph.withDevice(device);
    }

    void withDevice(String taskName, TornadoDevice device) {
        taskGraph.withDevice(taskName, device);
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

    TaskGraph getTaskGraph() {
        return taskGraph;
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

    long getTotalBytesCopyIn() {
        return taskGraph.getTotalBytesCopyIn();
    }

    long getTotalBytesCopyOut() {
        return taskGraph.getTotalBytesCopyOut();
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
        taskGraph.getDevice().clean();
    }

    void clearProfiles() {
        taskGraph.clearProfiles();
    }

    void withDefaultScheduler(boolean useDefaultScheduler) {
        taskGraph.useDefaultThreadScheduler(useDefaultScheduler);
    }

    void withBatch(String batchSize) {
        taskGraph.batch(batchSize);
    }

    void withMemoryLimit(String memoryLimit) {
        taskGraph.withMemoryLimit(memoryLimit);
    }

    void withoutMemoryLimit() {
        taskGraph.withoutMemoryLimit();
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

    void withConcurrentDevices() {
        taskGraph.withConcurrentDevices();
    }

    void withoutConcurrentDevices() {
        taskGraph.withoutConcurrentDevices();
    }

    void withThreadInfo() {
        taskGraph.withThreadInfo();
    }

    void withoutThreadInfo() {
        taskGraph.withoutThreadInfo();
    }

    void withPrintKernel() {
        taskGraph.withPrintKernel();
    }

    void withoutPrintKernel() {
        taskGraph.withoutPrintKernel();
    }

    void withCompilerFlags(TornadoVMBackendType backendType, String compilerFlags) {
        taskGraph.withCompilerFlags(backendType, compilerFlags);
    }

    void withGridScheduler(GridScheduler gridScheduler) {
        taskGraph.withGridScheduler(gridScheduler);
    }

    long getTotalBytesTransferred() {
        return taskGraph.getTotalBytesTransferred();
    }

    long getTotalDeviceMemoryUsage() {
        return taskGraph.getTotalDeviceMemoryUsage();
    }

    long getCurrentDeviceMemoryUsage() {
        return taskGraph.getCurrentDeviceMemoryUsage();
    }

    void mapOnDeviceMemoryRegion(Object destArray, Object srcArray, long offset, ImmutableTaskGraph taskGraphSrc) {
        taskGraph.mapOnDeviceMemoryRegion(destArray, srcArray, offset, taskGraphSrc.taskGraph.taskGraphImpl);
    }
}
