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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;

/**
 * Executor Class to dispatch Tornado Task-Graphs. An executor plan
 * {@link TornadoExecutionPlan} contains an executor object, which in turn,
 * contains a set of immutable task-graphs. All actions applied to the
 * execution plan affect to all the immutable graphs associated with it.
 */
class TornadoExecutor {

    private final List<ImmutableTaskGraph> immutableTaskGraphList;
    private List<ImmutableTaskGraph> subgraphList;

    TornadoExecutor(ImmutableTaskGraph... immutableTaskGraphs) {
        immutableTaskGraphList = new ArrayList<>();
        Collections.addAll(immutableTaskGraphList, immutableTaskGraphs);
    }

    void execute(ExecutorFrame executionPackage) {
        immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.execute(executionPackage));
    }

    boolean withGridScheduler(GridScheduler gridScheduler) {
        boolean checkGridRegistered = false;
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            immutableTaskGraph.withGridScheduler(gridScheduler);
            checkGridRegistered |= immutableTaskGraph.isGridRegistered();
        }
        return checkGridRegistered;
    }

    void updateLastExecutedTaskGraph() {
        ImmutableTaskGraph last = immutableTaskGraphList.getLast();
        immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.setLastExecutedTaskGraph(immutableTaskGraphList.getLast()));

        if (subgraphList != null) {
            for (ImmutableTaskGraph immutableTaskGraph : subgraphList) {
                immutableTaskGraph.setLastExecutedTaskGraph(last);
            }
        }
    }

    void withPreCompilation(ExecutorFrame executorFrame) {
        immutableTaskGraphList.forEach(immutableTaskGraph -> immutableTaskGraph.withPreCompilation(executorFrame));
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

    void withThreadInfo() {
        immutableTaskGraphList.forEach(ImmutableTaskGraph::withThreadInfo);
    }

    List<Object> getOutputs() {
        List<Object> outputs = new ArrayList<>();
        immutableTaskGraphList.forEach(immutableTaskGraph -> outputs.addAll(immutableTaskGraph.getOutputs()));
        return outputs;
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

    void selectGraph(int graphIndex) {
        if (subgraphList == null) {
            subgraphList = new ArrayList<>();
            immutableTaskGraphList.forEach(g -> Collections.addAll(subgraphList, g));
        }
        processPersistentStates(graphIndex);
        immutableTaskGraphList.clear();
        Collections.addAll(immutableTaskGraphList, subgraphList.get(graphIndex));
    }

    /**
     * Processes the persistent states of a specified subgraph.
     *
     * @param graphIndex
     *     The index of the subgraph to process.
     */
    private void processPersistentStates(int graphIndex) {
        // Validate that the graphIndex is within bounds of subgraphList
        if (graphIndex < 0 || graphIndex >= subgraphList.size()) {
            throw new TornadoRuntimeException("Error: graphIndex out of bounds: " + graphIndex);
        }

        // Store the selected graph before clearing the list
        ImmutableTaskGraph selectedGraph = subgraphList.get(graphIndex);

        // Clear and update the immutableTaskGraphList
        immutableTaskGraphList.clear();
        Collections.addAll(immutableTaskGraphList, selectedGraph);

    }

    private ImmutableTaskGraph getGraph(int graphIndex) {
        if (graphIndex < immutableTaskGraphList.size()) {
            return immutableTaskGraphList.get(graphIndex);
        } else {
            throw new TornadoRuntimeException("TaskGraph index #" + graphIndex + " does not exist in current executor");
        }
    }

    private ImmutableTaskGraph getGraphByName(String uniqueName) {
        for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphList) {
            if (immutableTaskGraph.getTaskGraph().getTaskGraphName().equals(uniqueName)) {
                return immutableTaskGraph;
            }
        }
        throw new TornadoRuntimeException("TaskGraph with name " + uniqueName + " does not exist in current executor");
    }

    void selectAll() {
        if (subgraphList == null) {
            return;
        }
        immutableTaskGraphList.clear();
        subgraphList.forEach(g -> Collections.addAll(immutableTaskGraphList, g));
        subgraphList = null;
    }

    void mapOnDeviceMemoryRegion(Object destArray, Object srcArray, long offset, int fromGraphIndex, int toGraphIndex) {
        // Be sure to update the whole list of graphs
        selectAll();

        // Guard checks
        if (immutableTaskGraphList.size() < 2) {
            throw new TornadoRuntimeException("MapOnDeviceMemoryRegion needs at least two task graphs");
        } else if (immutableTaskGraphList.size() < fromGraphIndex) {
            throw new TornadoRuntimeException("TaskGraph index #" + fromGraphIndex + " does not exist in current executor");
        } else if (immutableTaskGraphList.size() < toGraphIndex) {
            throw new TornadoRuntimeException("TaskGraph index #" + toGraphIndex + " does not exist in current executor");
        }
        // Identify the task-graphs to take for the update operation
        ImmutableTaskGraph taskGraphSrc = getGraph(fromGraphIndex);
        ImmutableTaskGraph taskGraphDest = getGraph(toGraphIndex);
        taskGraphDest.mapOnDeviceMemoryRegion(destArray, srcArray, offset, taskGraphSrc);
    }

    boolean checkAllTaskGraphsForGridScheduler() {
        if (subgraphList == null) {
            return false;
        }
        for (ImmutableTaskGraph immutableTaskGraph : subgraphList) {
            if (immutableTaskGraph.isGridRegistered()) {
                return true;
            }
        }
        return false;
    }

    public void withWarmUpTime(long milliseconds, ExecutorFrame executorFrame) throws InterruptedException {
        AtomicBoolean run = new AtomicBoolean(true);

        // If iterations takes more than the specified amount of milliseconds,
        // the next run is stopped. This means that the amount if milliseconds
        // specified is "at least" the time that the warm-up will take.
        Thread warmUpThread = new Thread(() -> {
            while (run.get()) {
                runForWarmUp(executorFrame);
            }
        });

        Thread controllerThread = new Thread(() -> {
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException e) {
                throw new TornadoRuntimeException(e);
            }
            run.set(false);
        });
        warmUpThread.start();
        controllerThread.start();

        warmUpThread.join();
        controllerThread.join();
    }

    private void runForWarmUp(ExecutorFrame executorFrame) {
        immutableTaskGraphList.forEach(immutableTaskGraph -> {
            immutableTaskGraph.execute(executorFrame);
            // Update state for all task-graphs within the execution plan
            ImmutableTaskGraph last = immutableTaskGraphList.getLast();
            immutableTaskGraphList.forEach(itg -> itg.setLastExecutedTaskGraph(last));
        });
    }

    public void withWarmUpIterations(int iterations, ExecutorFrame executorFrame) {
        IntStream.range(0, iterations).forEach(iteration -> runForWarmUp(executorFrame));
    }
}
