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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.profiler.ProfilerInterface;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;
import uk.ac.manchester.tornado.api.runtime.TaskContextInterface;

public interface TornadoTaskGraphInterface extends ProfilerInterface {

    SchedulableTask getTask(String taskNameID);

    TornadoDevice getDevice();

    void setDevice(TornadoDevice device);

    void updatePersistedObjectState();

    void setDevice(String taskName, TornadoDevice device);

    TornadoDevice getDeviceForTask(String id);

    void addInner(SchedulableTask task);

    boolean isLastDeviceListEmpty();

    void scheduleInner();

    void withBatch(String batchSize);

    void withMemoryLimit(String memoryLimit);

    void withoutMemoryLimit();

    void apply(Consumer<SchedulableTask> consumer);

    void mapAllToInner(TornadoDevice device);

    void dumpTimes();

    void dumpProfiles();

    void dumpEvents();

    void clearProfiles();

    void waitOn();

    void transferToDevice(int mode, Object... objects);

    void transferToHost(int mode, Object... objects);

    void consumeFromDevice(String uniqueTaskGraphName, Object... objects);

    void consumeFromDevice(Object... objects);

    void dump();

    void warmup(ExecutorFrame executionPackage);

    void freeDeviceMemory();

    void syncRuntimeTransferToHost(Object... objects);

    void syncRuntimeTransferToHost(Object objects, long offset, long partialCopySize);

    String getId();

    TaskContextInterface meta();

    TornadoTaskGraphInterface execute(ExecutorFrame executionPackage);

    void addTask(TaskPackage taskPackage);

    void addPrebuiltTask(TaskPackage taskPackage);

    String getTaskGraphName();

    void useDefaultThreadScheduler(boolean use);

    boolean isFinished();

    Set<Object> getArgumentsLookup();

    Collection<?> getOutputs();

    TornadoTaskGraphInterface createImmutableTaskGraph();

    void enableProfiler(ProfilerMode profilerMode);

    void disableProfiler();

    void withConcurrentDevices();

    void withoutConcurrentDevices();

    void withThreadInfo();

    void withoutThreadInfo();

    void withPrintKernel();

    void withoutPrintKernel();

    void withGridScheduler(GridScheduler gridScheduler);

    long getCurrentDeviceMemoryUsage();

    Map<String, List<Object>> getPersistedTaskToObjectsMap();

    void withCompilerFlags(TornadoVMBackendType backendType, String compilerFlags);

    void mapOnDeviceMemoryRegion(Object destArray, Object srcArray, long offset, TornadoTaskGraphInterface taskGraphSrc);

    void updateObjectAccess();

    void setLastExecutedTaskGraph(TornadoTaskGraphInterface lastExecutedTaskGraph);

    boolean isGridRegistered();
}
