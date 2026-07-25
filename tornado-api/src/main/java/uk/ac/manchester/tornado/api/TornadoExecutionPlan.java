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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.plan.types.OffConcurrentDevices;
import uk.ac.manchester.tornado.api.plan.types.OffMemoryLimit;
import uk.ac.manchester.tornado.api.plan.types.OffPrintKernel;
import uk.ac.manchester.tornado.api.plan.types.OffProfiler;
import uk.ac.manchester.tornado.api.plan.types.OffThreadInfo;
import uk.ac.manchester.tornado.api.plan.types.WithAllGraphs;
import uk.ac.manchester.tornado.api.plan.types.WithBatch;
import uk.ac.manchester.tornado.api.plan.types.WithCUDAGraph;
import uk.ac.manchester.tornado.api.plan.types.WithClearProfiles;
import uk.ac.manchester.tornado.api.plan.types.WithCompilerFlags;
import uk.ac.manchester.tornado.api.plan.types.WithConcurrentDevices;
import uk.ac.manchester.tornado.api.plan.types.WithDefaultScheduler;
import uk.ac.manchester.tornado.api.plan.types.WithDevice;
import uk.ac.manchester.tornado.api.plan.types.WithFreeDeviceMemory;
import uk.ac.manchester.tornado.api.plan.types.WithGraph;
import uk.ac.manchester.tornado.api.plan.types.WithGridScheduler;
import uk.ac.manchester.tornado.api.plan.types.WithIntraPlanConcurrency;
import uk.ac.manchester.tornado.api.plan.types.WithStagedTransfers;
import uk.ac.manchester.tornado.api.plan.types.WithMemoryLimit;
import uk.ac.manchester.tornado.api.plan.types.WithPreCompilation;
import uk.ac.manchester.tornado.api.plan.types.WithPrintKernel;
import uk.ac.manchester.tornado.api.plan.types.WithProfiler;
import uk.ac.manchester.tornado.api.plan.types.WithResetDevice;
import uk.ac.manchester.tornado.api.plan.types.WithThreadInfo;
import uk.ac.manchester.tornado.api.plan.types.WithWarmUpIterations;
import uk.ac.manchester.tornado.api.plan.types.WithWarmUpTime;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;

/**
 * Class to create and optimize execution plans for running a set of
 * immutable tasks-graphs on modern hardware. An executor plan contains an
 * executor object, which in turn, contains a set of immutable task-graphs.
 * All actions applied to the execution plan affect to all the immutable
 * graphs associated with it.
 *
 * @since v0.15
 */
public sealed class TornadoExecutionPlan implements AutoCloseable permits ExecutionPlanType {

    /**
     * Method to obtain the default device in TornadoVM. The default one corresponds
     * to the device assigned to the driver (backend) with index 0 and device 0.
     */
    public static TornadoDevice DEFAULT_DEVICE = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice();

    private static final AtomicLong globalExecutionPlanCounter = new AtomicLong(0);

    /**
     * The TornadoVM executor is a list of chain of actions to be performed.
     * Each action can enable/disable runtime features, influence the compiler,
     * influence the code optimization, adapt runtime parameters, etc.
     */
    protected TornadoExecutor tornadoExecutor;

    protected ExecutorFrame executionFrame;

    /**
     * Reference to the Root of the List.
     */
    protected TornadoExecutionPlan rootNode;

    /**
     * Reference to the next node in the list.
     */
    protected TornadoExecutionPlan childLink;

    /**
     * Reference to the previous node in the list.
     */
    protected TornadoExecutionPlan parentLink;

    protected List<TornadoExecutionResult> planResults;

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
        tornadoExecutor = new TornadoExecutor(immutableTaskGraphs);
        final long id = globalExecutionPlanCounter.incrementAndGet();
        executionFrame = new ExecutorFrame(id);
        updateAccess(immutableTaskGraphs);
        rootNode = this;
        planResults = new ArrayList<>();
    }

    /**
     * If the {@code TornadoExecutionPlan} consists of multiple task-graphs, this function
     * updates the access type of the input and output data of each task-graph, as necessary.
     *
     * @param immutableTaskGraphs
     *     The list of the immutable task-graphs in the {@code TornadoExecutionPlan}
     */
    private void updateAccess(ImmutableTaskGraph... immutableTaskGraphs) {
        if (immutableTaskGraphs.length > 1) {
            for (ImmutableTaskGraph immutableTaskGraph : immutableTaskGraphs) {
                TaskGraph taskGraph = immutableTaskGraph.getTaskGraph();
                TornadoTaskGraphInterface taskGraphImpl = taskGraph.getTaskGraphImpl();
                taskGraphImpl.updateObjectAccess();
            }
        }
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

    /**
     * Method to return the total number of execution plans instantiated in a single JVM instance.
     *
     * @since 1.0.2
     * 
     * @return int
     */
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
        tornadoExecutor.execute(executionFrame);
        TornadoProfilerResult profilerResult = new TornadoProfilerResult(tornadoExecutor, this.getTraceExecutionPlan());
        TornadoExecutionResult executionResult = new TornadoExecutionResult(profilerResult);
        planResults.add(executionResult);
        tornadoExecutor.updateLastExecutedTaskGraph();
        return executionResult;
    }

    /**
     * Select a graph from the {@link TornadoExecutionPlan} to execute.
     * This method allows developers to select a specific graph from the
     * execution plan to launch. Developers can choose which graph from
     * the input list to use (passed in the constructor).
     *
     * 
     * @since 1.0.9
     * @param graphIndex
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withGraph(int graphIndex) {
        tornadoExecutor.selectGraph(graphIndex);
        if (executionFrame.getGridScheduler() != null) {
            tornadoExecutor.withGridScheduler(executionFrame.getGridScheduler());
        }
        return new WithGraph(this, graphIndex);
    }

    /**
     * Select all graphs from the {@link TornadoExecutionPlan}. This method
     * has an effect if the {@link #withGraph(int)} method was invoked.
     *
     * @since 1.0.9
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withAllGraphs() {
        tornadoExecutor.selectAll();
        return new WithAllGraphs(this);
    }

    /**
     * It invokes the JIT compiler for all immutable tasks-graphs associated to an
     * executor.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withPreCompilation() {
        tornadoExecutor.withPreCompilation(executionFrame);
        return new WithPreCompilation(this);
    }

    /**
     * It selects a specific device for all immutable tasks graphs associated to an
     * executor.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDevice(TornadoDevice device) {
        tornadoExecutor.setDevice(device);
        return new WithDevice(this, device);
    }

    /**
     * Print all operations enabled/disabled from the Execution Plan.
     * 
     * @since 1.0.8
     */
    public void printTraceExecutionPlan() {
        System.out.println(Objects.requireNonNullElse(childLink, this));
    }

    /**
     * Returns a string with all the operations enabled/disabled from the
     * Execution Plan.
     *
     * @since 1.0.8
     */
    public String getTraceExecutionPlan() {
        if (childLink != null) {
            return childLink.toString();
        }
        return toString();
    }

    @Override
    public String toString() {
        return "Root";
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
        return new WithDevice(this, device);
    }

    /**
     * It enables multiple tasks in a task graph to run concurrently on the same
     * or different devices. Note that the TornadoVM runtime does not check for
     * data dependencies across tasks when using this API call. Thus, it is
     * the responsibility of the programmer to provide tasks with no data dependencies
     * when invoking the method {@link TornadoExecutionPlan#withConcurrentDevices}.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withConcurrentDevices() {
        tornadoExecutor.withConcurrentDevices();
        return new WithConcurrentDevices(this);
    }

    /**
     * It disables multiple tasks in a task graph to run concurrently on the same
     * or different devices.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutConcurrentDevices() {
        tornadoExecutor.withoutConcurrentDevices();
        return new OffConcurrentDevices(this);
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
        return new WithFreeDeviceMemory(this);
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
        boolean isGridRegistered = tornadoExecutor.withGridScheduler(gridScheduler);
        if (!isGridRegistered) {
            // check for the whole set of task-graphs
            isGridRegistered = tornadoExecutor.checkAllTaskGraphsForGridScheduler();
            if (!isGridRegistered) {
                throw new TornadoRuntimeException("[ERROR] GridScheduler Name not registered in any task-graph");
            }
        }
        executionFrame.setGridScheduler(gridScheduler);
        return new WithGridScheduler(this, gridScheduler);
    }

    /**
     * Notify the TornadoVM runtime system to utilize the default thread scheduler.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withDefaultScheduler() {
        tornadoExecutor.withDefaultScheduler();
        return new WithDefaultScheduler(this);
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
        return new WithBatch(this, batchSize);
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
        executionFrame.setProfilerMode(profilerMode);
        return new WithProfiler(this, profilerMode);
    }

    /**
     * Disables the profiler if previous execution plan had the profiler enabled.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutProfiler() {
        executionFrame.setProfilerOff();
        return new OffProfiler(this);
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
        return new WithMemoryLimit(this, memoryLimit);
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
        return new OffMemoryLimit(this);
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
        return new WithResetDevice(this);
    }

    /**
     * Obtains the ID that was assigned to the execution plan.
     */
    public long getId() {
        return executionFrame.getExecutionPlanId();
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
        return new WithClearProfiles(this);
    }

    /**
     * Enable printing of the Thread-Block Deployment for the generated kernels.
     *
     * @since 1.0.2
     * 
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withThreadInfo() {
        tornadoExecutor.withThreadInfo();
        return new WithThreadInfo(this);
    }

    /**
     * Disable printing of the Thread-Block Deployment for the generated kernels.
     *
     * @since 1.0.2
     * 
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutThreadInfo() {
        tornadoExecutor.withoutThreadInfo();
        return new OffThreadInfo(this);
    }

    /**
     * Enable printing of the generated kernels for each task in a task-graph.
     *
     * @since 1.0.2
     * 
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withPrintKernel() {
        tornadoExecutor.withPrintKernel();
        return new WithPrintKernel(this);
    }

    /**
     * Disable printing of the generated kernels for each task in a task-graph.
     * 
     * @since 1.0.2
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutPrintKernel() {
        tornadoExecutor.withoutPrintKernel();
        return new OffPrintKernel(this);
    }

    /**
     * Set compiler flags for each backend.
     * 
     * @param backend
     *     {@link TornadoVMBackendType}
     * @param compilerFlags
     *     {@link String}
     * @since 1.0.7
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withCompilerFlags(TornadoVMBackendType backend, String compilerFlags) {
        tornadoExecutor.withCompilerFlags(backend, compilerFlags);
        return new WithCompilerFlags(this, compilerFlags);
    }

    /**
     * @since 1.0.4
     * 
     * @throws {@link
     *     TornadoExecutionPlanException}
     */
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

    public TornadoExecutionResult getPlanResult(int index) {
        if (index >= planResults.size()) {
            throw new TornadoRuntimeException("[ERROR] Execution result not found");
        }
        return planResults.get(index);
    }

    /**
     * This function maps the device memory region that corresponds to a TornadoVM object to another on-device memory region.
     * This call instructs the TornadoVM runtime to avoid transferring data between `device` -> `host` -> `device`. Instead,
     * it can update the corresponding device pointers.
     *
     * <p>
     * The semantics are as follows: there is the source object, and the destination object. This call maps the dest object
     * to the source object from a given offset. The source object is passed from the task-graph `fromGraphIndex`, and
     * the destination object is taken from the `toGraphIndex`. This method can be invoked in a multi-task-graph execution
     * plan. It will not work if there is only one task-graph in the execution plan.
     * </p>
     * 
     * @param destTornadoArray
     * @param srcTornadoArray
     * @param offset
     * @param fromGraphIndex
     * @param toGraphIndex
     *
     * @since v1.1.0
     */
    public void mapOnDeviceMemoryRegion(Object destTornadoArray, Object srcTornadoArray, long offset, int fromGraphIndex, int toGraphIndex) {
        tornadoExecutor.mapOnDeviceMemoryRegion(destTornadoArray, srcTornadoArray, offset, fromGraphIndex, toGraphIndex);
    }

    /**
     * This function allows developers to warm up the whole execution plan before running it. This covers
     * copy in and out data, compiling all tasks and executing all tasks once for the specified amount
     * of time.
     * 
     * @param milliseconds
     *     Amount of time to warm up the execution plan. This amount means that the execution plan will run,
     *     at least for the specified amount of time. if the tasks within the task-graphs
     *     takes longer to execute, in a second run, the code will not be dispatched.
     * @return {@link TornadoExecutionPlan}
     * 
     * @throws {@link
     *     InterruptedException}
     */
    public TornadoExecutionPlan withWarmUpTime(long milliseconds) throws InterruptedException {
        if (milliseconds < 0) {
            throw new TornadoRuntimeException("[ERROR] Warm-up time cannot be negative");
        }
        tornadoExecutor.withWarmUpTime(milliseconds, executionFrame);
        return new WithWarmUpTime(this, milliseconds);
    }

    /**
     * This function allows developers to warm up the whole execution plan before running it. This covers
     * copy in and out data, compiling all tasks and executing all tasks once for the specified amount
     * of time.
     *
     * @param iterations
     *     Number of iterations to run the whole execution plan as warm-up.
     * @return {@link TornadoExecutionPlan}
     *
     */
    public TornadoExecutionPlan withWarmUpIterations(int iterations) {
        if (iterations < 0) {
            throw new TornadoRuntimeException("[ERROR] Warm-up time cannot be negative");
        }
        tornadoExecutor.withWarmUpIterations(iterations, executionFrame);
        return new WithWarmUpIterations(this, iterations);
    }

    /**
     * Captures this execution plan into a CUDA graph on its first execution and replays the graph on
     * subsequent ones, so the per-operation launch and copy submissions are issued once instead of
     * every execution. Worth it for short plans that are re-executed many times with the same
     * buffers and grid, where host-side submission is a visible share of the time.
     * Realised on the PTX and CUDA backends; a no-op elsewhere.
     *
     * <p>Two consequences of capture are worth knowing: capture records a single stream, so a captured
     * plan runs single-stream even if {@link #withIntraPlanConcurrency()} was also requested; and
     * device-side per-operation timings are not collected for captured operations, so the profiler's
     * kernel and transfer timers under-report (host wall-clock metrics stay valid).
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withCUDAGraph() {
        //TODO: include a check to verify that the BACKEND is PTX
        tornadoExecutor.withCUDAGraph();
        return new WithCUDAGraph(this);
    }

    /**
     * Enables intra-plan concurrency for this execution plan: DAG-independent operations
     * (e.g. H2D copies, kernels, D2H copies) are routed to separate role streams so they
     * may overlap, with cross-stream ordering preserved via device events derived from the
     * bytecode dependency DAG.
     * Currently realised on the PTX and CUDA backends with CUDA streams.
     * A no-op for OPENCL, METAL and SPIRV backends.
     * Default is off (single stream).
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withIntraPlanConcurrency() {
        tornadoExecutor.withIntraPlanConcurrency();
        return new WithIntraPlanConcurrency(this);
    }

    /**
     * Enables intra-plan concurrency (see {@link #withIntraPlanConcurrency()}) and sizes the backend's
     * COMPUTE stream pool, which bounds how many DAG-independent kernels of this plan can be in flight
     * at once. Larger pools help plans with many small, non-saturating kernels; they do nothing for
     * kernels that already fill the device. Without this call the backend default is used
     * ({@code -Dtornado.cuda.compute.streams}, default 4).
     * Honoured by the CUDA backend; other backends ignore the pool size.
     *
     * @param computeStreams
     *     number of COMPUTE streams to use, at least 1
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withIntraPlanConcurrency(int computeStreams) {
        if (computeStreams < 1) {
            throw new TornadoRuntimeException("[ERROR] The number of compute streams must be at least 1: " + computeStreams);
        }
        tornadoExecutor.withIntraPlanConcurrency(computeStreams);
        return new WithIntraPlanConcurrency(this);
    }

    /**
     * Disables intra-plan concurrency for this execution plan (single-stream execution).
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutIntraPlanConcurrency() {
        tornadoExecutor.withoutIntraPlanConcurrency();
        return this;
    }

    /**
     * Enables staged host-to-device transfers for this execution plan: large one-shot uploads
     * (e.g. {@code FIRST_EXECUTION} model weights) are chunked and copied through a ring of
     * pinned host staging buffers, so filling chunk {@code i+1} overlaps the DMA of chunk
     * {@code i}. Buffers served by the ring also skip the whole-segment host pin, whose
     * synchronous page-in dominates the cost of large (often mmap'd) sources.
     * Only transfers that are non-batched, read-only and at least
     * {@code -Dtornado.staged.min.size} bytes (default 16MB) are staged; every other buffer keeps
     * the default direct path, including host pinning.
     * Currently realised on the PTX and CUDA backends.
     * A no-op for OPENCL, METAL and SPIRV backends.
     * Default is off, and can also be enabled process-wide with
     * {@code -Dtornado.staged.transfers=true}.
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withStagedTransfers() {
        tornadoExecutor.withStagedTransfers();
        return new WithStagedTransfers(this);
    }

    /**
     * Enables staged host-to-device transfers (see {@link #withStagedTransfers()}) with explicit
     * tuning, instead of the {@code -Dtornado.staged.*} defaults. The three knobs trade staging
     * overhead against overlap: transfers below {@code minTransferSize} keep the direct path,
     * {@code chunkSize} is the granularity at which copies overlap host-side filling, and
     * {@code ringDepth} is how many pinned slots rotate (so how many chunks may be in flight).
     * Pinned host memory of {@code chunkSize * ringDepth} bytes is reserved on first use; changing
     * either value rebuilds the ring.
     * Honoured by the CUDA backend; other backends ignore the tuning values.
     *
     * @param minTransferSize
     *     smallest transfer, in bytes, that is routed through the staging ring
     * @param chunkSize
     *     size, in bytes, of each staged chunk
     * @param ringDepth
     *     number of pinned staging slots, at least 2
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withStagedTransfers(long minTransferSize, long chunkSize, int ringDepth) {
        if (minTransferSize < 1 || chunkSize < 1) {
            throw new TornadoRuntimeException("[ERROR] Staged transfer sizes must be positive: minTransferSize=" + minTransferSize + ", chunkSize=" + chunkSize);
        }
        if (ringDepth < 2) {
            throw new TornadoRuntimeException("[ERROR] The staging ring needs at least 2 slots to overlap filling with DMA: " + ringDepth);
        }
        tornadoExecutor.withStagedTransfers(minTransferSize, chunkSize, ringDepth);
        return new WithStagedTransfers(this);
    }

    /**
     * Disables staged host-to-device transfers for this execution plan (direct transfers).
     *
     * @return {@link TornadoExecutionPlan}
     */
    public TornadoExecutionPlan withoutStagedTransfers() {
        tornadoExecutor.withoutStagedTransfers();
        return this;
    }
}
