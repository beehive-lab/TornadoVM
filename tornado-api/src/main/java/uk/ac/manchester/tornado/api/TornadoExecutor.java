/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executor Class to dispatch Tornado Task-Graphs. An executor plan
 * {@link TornadoExecutionPlan} contains an executor object, which in turn,
 * contains a set of immutable task-graphs. All actions applied to the
 * execution plan affect to all the immutable graphs associated with it.
 */
class TornadoExecutor {

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

    void warmup(ExecutorFrame executorFrame) {
        immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.warmup(executorFrame));
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
            throw new TornadoRuntimeException("TaskGraph index #" + immutableTaskGraphIndex + " does not exist in current executor");
        }
        return immutableTaskGraphList.get(immutableTaskGraphIndex).getDevice();
    }

    List<Object> getOutputs() {
        List<Object> outputs = new ArrayList<>();
        immutableTaskGraphList.forEach(immutableTaskGraph -> outputs.addAll(immutableTaskGraph.getOutputs()));
        return outputs;
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

    void withCompilerFlags(TornadoVMBackendType backendType, String compilerFlags) {
        immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.withCompilerFlags(backendType, compilerFlags));
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
