/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.runtime.autotune;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import uk.ac.manchester.tornado.api.AutoTuneConfig;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TornadoTaskGraphInterface;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.WorkerGrid3D;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.AutoTuneMetric;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.runtime.ExecutorFrame;
import uk.ac.manchester.tornado.runtime.autotune.spi.AutoTuneSearchStrategy;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;



/**
 * Tunes the local work-group size of the tasks in a task graph (design issue beehive-lab/TornadoVM#979).
 *
 * <p>
 * {@link #tune} keeps the tasks whose device has a registered {@link AutoTuneSearchStrategy}, runs the
 * graph only the first time so that every global work size is known, then benchmarks the candidate local sizes of each
 * task that is not in {@link AutoTuneCache}: warm-up runs, then measured runs of the whole graph, scored
 * by the median of the configured {@link AutoTuneMetric}. The lowest score wins, and the winners are
 * returned as a {@link GridScheduler}.
 * </p>
 *
 * <p>
 * If a task has a {@link WorkerGrid} in the frame's {@link GridScheduler}, the global size is read from
 * that grid and candidates are written into it; its original local size is one of the candidates and is
 * restored afterwards. Otherwise the task metadata is used. A failure while tuning one task is logged and
 * leaves that task unchanged.
 * </p>
 *
 * <p>
 * Called from {@code TornadoTaskGraph.checkAutoTuneOn}. An instance must not be shared between
 * concurrent {@link #tune} calls.
 * </p>
 * 
 * <p>
 * {@code tune} is called on every {@code execute()} of a plan with auto-tuning enabled. A task already in
 * {@link AutoTuneCache} is not benchmarked again: its cached local size is returned straight away, so only the
 * first execution of a graph pays for tuning. Tuning runs the graph many times, so kernels with side effects
 * (for example ones that update their input in place) see those extra runs.
 * </p>
 */
public final class AutoTuner {

    private final AutoTuneConfig config;
    private final TornadoLogger logger = new TornadoLogger(this.getClass());
    private GridScheduler userGridScheduler;
    /** Timing of a candidate that failed or was never measured. It is the largest {@code long}, so it loses to any measurement. */
    private static final long FAILED_CANDIDATE_TIMING = Long.MAX_VALUE;


    public AutoTuner(AutoTuneConfig config) {
        this.config = config;
    }

    /** An eligible task together with the strategy that handles its device. */
    private record EligibleTask(String taskId, SchedulableTask task, TornadoXPUDevice device, AutoTuneSearchStrategy strategy) {
    }

    /**
     * Tunes the eligible tasks of {@code taskGraph} and returns a {@link GridScheduler} with one
     * {@link WorkerGrid} per tuned task, holding the winning local work size. Tasks that are not eligible,
     * or whose tuning fails, are left out and keep their configuration.
     *
     * <p>
     * The graph is run with {@code taskGraph.execute(executorFrame)}, so the frame must not carry an
     * {@link AutoTuneConfig} (those runs would start tuning again), and its profiler must be enabled for
     * {@link AutoTuneMetric#KERNEL_TIME}. The config's time budget covers the whole call, counted from the
     * end of the probe run.
     * </p>
     *
     * @param taskGraph
     *     the task graph to tune.
     * @param taskIds
     *     the IDs of its tasks, in {@code "taskGraphName.taskName"} form, as built by
     *     {@code TornadoTaskGraph.checkAutoTuneOn} from the graph's task packages.
     * @param executorFrame
     *     the frame used for every execution. It supplies the user's {@link GridScheduler}, if any.
     * @return the tuned grids (empty if every eligible task failed), or {@code null} if no task is eligible.
     */
    public GridScheduler tune(TornadoTaskGraphInterface taskGraph, List<String> taskIds, ExecutorFrame executorFrame) {
        logger.debug("Auto-tuning %d candidate task(s)", taskIds.size());
        this.userGridScheduler = executorFrame.getGridScheduler();
        List<EligibleTask> eligibleTasks = filterEligibleTasks(taskIds, taskGraph);

        if (eligibleTasks.isEmpty()) {
            logger.warn("No eligible tasks for auto-tuning out of %d candidate(s)", taskIds.size());
            return null;
        }
        // Probe run: executes the graph once so each task's global work size is populated.It is skipped once
        // the sizes are known, so it happens at most once per task graph.
        if (! globalWorkSizesKnown(eligibleTasks)){
            taskGraph.execute(executorFrame);
        }
        GridScheduler effectiveGridScheduler = new GridScheduler();
        long timeBudgetMillis = config.getTimeBudgetMillis();
        long deadlineNanos = (timeBudgetMillis <= 0) ? Long.MAX_VALUE : System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeBudgetMillis);

        for (EligibleTask eligibleTask : eligibleTasks) {
            try{
                long[] globalWorkSize = resolveGlobalWorkSize(eligibleTask.task()).clone();
                logger.debug("Autotuning task %s with global work size %s", eligibleTask.taskId(), Arrays.toString(globalWorkSize));
                Optional<AutoTuneResult> cached = checkCache(eligibleTask.taskId(), eligibleTask.device(), kernelSignatureHash(eligibleTask.task()), globalWorkSize);
                AutoTuneResult result;
                if (cached.isPresent()) {
                    result = cached.get();
                    logger.debug("Using cached auto-tune result for %s: local work = %s (%d ns)", eligibleTask.taskId(), arrayToString(result.getWinningLocalWork()), result.getMedianTimeNanos());
                } else {
                    result=taskTuner(eligibleTask, globalWorkSize, taskGraph, executorFrame, deadlineNanos);
                }

                applyResult(effectiveGridScheduler, eligibleTask.taskId(), globalWorkSize, result);
            }
            catch(RuntimeException e){
                logger.warn("Auto-tuning failed for %s, keeping existing configuration: %s", eligibleTask.taskId(), e.getMessage());
            }
        }

        return effectiveGridScheduler;
    }


    /**
     * Benchmarks the candidates for one task, caches and returns the best result, and restores the task's
     * original local work size. No new candidate starts once {@code deadlineNanos} has passed.
     *
     * @throws TornadoRuntimeException
     *     if no candidate produced a valid timing.
     */
    private AutoTuneResult taskTuner(EligibleTask eligibleTask, long[] globalWorkSize, TornadoTaskGraphInterface taskGraph, ExecutorFrame executorFrame, long deadlineNanos){
        AutoTuneResult result;
        int[] originalLocalWorkInt = narrowToInt(currentLocalWork(eligibleTask.task()));
        List<int[]> candidates = generateCandidates(eligibleTask.strategy(), globalWorkSize, eligibleTask.device(), originalLocalWorkInt);
        logger.debug("Generated %d candidate(s) for %s", candidates.size(), eligibleTask.taskId());
        List<Long> timings = new ArrayList<>(Collections.nCopies(candidates.size(), FAILED_CANDIDATE_TIMING));
        try{
            for (int i = 0; i < candidates.size(); i++) {
                if (System.nanoTime() >= deadlineNanos) {
                    logger.warn("Time budget reached for %s after %d/%d candidate(s); selecting best so far", eligibleTask.taskId(), i, candidates.size());
                    break;
                }
                int[] candidate = candidates.get(i);
                long timing = benchmarkCandidate(eligibleTask.task(), taskGraph, executorFrame, candidate, eligibleTask.taskId());
                logger.debug("\tCandidate %s for %s = %d ns", arrayToString(candidate), eligibleTask.taskId(), timing);
                timings.set(i,timing);
            }
        }
        finally{
            applyLocalWork(eligibleTask.task(), originalLocalWorkInt);
        }
        result = selectBest(eligibleTask.taskId(), eligibleTask.device(),kernelSignatureHash(eligibleTask.task()), globalWorkSize, candidates, timings);
        if (result.getMedianTimeNanos()==FAILED_CANDIDATE_TIMING){
         throw new TornadoRuntimeException("[ERROR] No valid auto-tune candidate for " + eligibleTask.taskId());   
        }
        storeInCache(result);
        
        logger.info("Selected local work %s for %s (%d ns)", arrayToString(result.getWinningLocalWork()), eligibleTask.taskId(), result.getMedianTimeNanos());
        return result;
    }

    /** Reads the task's current local size from where the runtime reads it (the grid if present, otherwise the metadata). */
    private long[] currentLocalWork(SchedulableTask task) {
        WorkerGrid grid = userGridOf(task);
        return (grid != null) ? grid.getLocalWork() : task.meta().getLocalWork();
    }

    /** Writes a local size where the runtime reads it; null means the backend default. */
    private void applyLocalWork(SchedulableTask task, int[] localWork) {
        WorkerGrid grid = userGridOf(task);
        if (grid != null) {
            if (localWork == null) {
                grid.setLocalWorkToNull();
            } else {
                grid.setLocalWork(localWork[0], localWork.length > 1 ? localWork[1] : 1, localWork.length > 2 ? localWork[2] : 1);
            }
        } else if (localWork == null) {
            ((TaskDataContext) task.meta()).setLocalWorkToNull();
        } else {
            long[] asLong = new long[localWork.length];
            for (int i = 0; i < localWork.length; i++) {
                asLong[i] = localWork[i];
            }
            task.meta().setLocalWork(asLong);
        }
    }

     /**
     * Keeps the tasks that exist in the graph, do not take a {@link KernelContext}, run on a
     * {@link TornadoXPUDevice} and have a registered {@link AutoTuneSearchStrategy}, which is resolved
     * here once. All other tasks are skipped and keep their configuration.
     */
    private List<EligibleTask> filterEligibleTasks(List<String> taskIds, TornadoTaskGraphInterface taskGraph) {
        List<EligibleTask> eligible = new ArrayList<>();
        for (String taskId : taskIds) {
            SchedulableTask task = taskGraph.getTask(taskId);
            if (task == null) {
                logger.warn("\tSkipping %s: task not found in graph", taskId);
                continue;
            }
            if (usesKernelContext(task)) {
            logger.info("\tSkipping %s: KernelContext tasks are not auto-tuned", taskId);
            continue;
            }
            TornadoDevice device = task.getDevice();
            if (!(device instanceof TornadoXPUDevice xpuDevice)) {
                logger.warn("\tSkipping %s: device is not a TornadoXPUDevice", taskId);
                continue;
            }
            Optional<AutoTuneSearchStrategy> strategy = AutoTuneStrategyRegistry.findStrategy(xpuDevice);
            if (strategy.isPresent()) {
                eligible.add(new EligibleTask(taskId, task, xpuDevice, strategy.get()));
            } else {
                logger.warn("\tSkipping %s: no AutoTuneSearchStrategy registered for its device", taskId);
            }
        }
        return eligible;
    }


/**
 * Tasks that take a {@link KernelContext} define their own thread indexing, local memory and barriers,
 * so their work-group size is part of the kernel's meaning and cannot be changed freely.
 */
private static boolean usesKernelContext(SchedulableTask task) {
    Object[] arguments = task.getArguments();
    if (arguments == null) {
        return false;
    }
    for (Object argument : arguments) {
        if (argument instanceof KernelContext) {
            return true;
        }
    }
    return false;
}
    

    /**
     * Returns the global work size from the task's {@link WorkerGrid} if the user attached one, otherwise
     * from the task metadata, which the probe run populates.
     *
     * @throws TornadoRuntimeException
     *     if it is unavailable or has a dimension below 1.
     */
    private long[] resolveGlobalWorkSize(SchedulableTask task) {
        WorkerGrid grid = userGridOf(task);
        long[] globalWork = (grid != null && grid.getGlobalWork() != null) ? trimTrailingOnes(grid.getGlobalWork()) : task.meta().getGlobalWork();
        if (globalWork == null || globalWork.length == 0) {
            throw new TornadoRuntimeException("[ERROR] Global work size is not available for " + task.getId());
        }
        for (long dimension : globalWork) {
            if (dimension <= 0) {
                throw new TornadoRuntimeException("[ERROR] Invalid global work size " + Arrays.toString(globalWork) + " for " + task.getId());
            }
        }
        return globalWork;
    }

    /** Whether every eligible task already has a valid global work size, i.e. the graph has run before. */
    private boolean globalWorkSizesKnown(List<EligibleTask> eligibleTasks) {
        for (EligibleTask eligibleTask : eligibleTasks) {
            try {
                resolveGlobalWorkSize(eligibleTask.task());
            } catch (RuntimeException e) {
                return false;
            }
        }
        return true;
    }

    private static long[] trimTrailingOnes(long[] size) {
    int length = size.length;
    while (length > 1 && size[length - 1] == 1) {
        length--;
    }
    return Arrays.copyOf(size, length);
    }
    
    /** Narrows a local work size to the {@code int} form used by {@link AutoTuneSearchStrategy}; {@code null} stays {@code null}. */
    private int[] narrowToInt(long[] localWork) {
        if (localWork == null) {
            return null;
        }
        int[] narrowed = new int[localWork.length];
        for (int i = 0; i < localWork.length; i++) {
            narrowed[i] = (int) localWork[i];
        }
        return narrowed;
    }

    /**
     * Returns the candidates for a task. For a 1D task with custom candidates in the config, the list is
     * {@code null} (backend default), the original local size if there is one, then the custom candidates.
     * Otherwise it comes from the {@link AutoTuneSearchStrategy}.
     */
    private List<int[]> generateCandidates(AutoTuneSearchStrategy strategy, long[] globalWorkSize, TornadoXPUDevice device, int[] originalLocalWorkInt) {
        if (globalWorkSize.length == 1 && this.config.getCustomCandidates1D() != null) {
            logger.debug("\tUsing user-supplied custom candidate list");
            List<int[]> customCandidates = new ArrayList<>();
            customCandidates.add(null);
            if (originalLocalWorkInt != null) {
                customCandidates.add(originalLocalWorkInt);
            }
            for (long candidate : config.getCustomCandidates1D()) {
                customCandidates.add(new int[] { (int) candidate });
            }
            return customCandidates;
        }
 
        return switch (globalWorkSize.length) {
            case 1 -> strategy.generateCandidates1D(globalWorkSize[0], device, originalLocalWorkInt, this.config.getStrategy());
            case 2 -> strategy.generateCandidates2D(globalWorkSize, device, originalLocalWorkInt, this.config.getStrategy());
            case 3 -> strategy.generateCandidates3D(globalWorkSize, device, originalLocalWorkInt, this.config.getStrategy());
            default -> throw new IllegalStateException("[ERROR] Unsupported global work dimensionality: " + globalWorkSize.length);
        };
    }


        /**
         * Applies one candidate local size ({@code null} selects the backend default), runs the configured
         * warm-up executions of the graph, then the measured ones, and returns their median in nanoseconds.
         * A measurement is the task's kernel time for {@link AutoTuneMetric#KERNEL_TIME}, or the wall-clock
         * time of one graph execution for {@link AutoTuneMetric#END_TO_END_TIME}. A measurement that is not
         * positive counts as failed.
         *
         * @return the median, or {@link #FAILED_CANDIDATE_TIMING} if the candidate throws or the median falls
         *     on a failed measurement.
         */
        private long benchmarkCandidate(SchedulableTask task, TornadoTaskGraphInterface taskGraph, ExecutorFrame executorFrame, int[] candidateLocalWork,String taskId) {
        try{
            applyLocalWork(task, candidateLocalWork);
    
            for (int i = 0; i < config.getWarmupIterations(); i++) {
                taskGraph.execute(executorFrame);
            }
    
            List<Long> measurements = new ArrayList<>(config.getMeasuredIterations());
            for (int i = 0; i < config.getMeasuredIterations(); i++) {
                long measuredTime;
                if (config.getMetric() == AutoTuneMetric.KERNEL_TIME) {
                    taskGraph.execute(executorFrame);
                    measuredTime = task.getProfiler().getTaskTimer(ProfilerType.TASK_KERNEL_TIME, task.getId());
                } else {
                    long start = System.nanoTime();
                    taskGraph.execute(executorFrame);
                    long end = System.nanoTime();
                    measuredTime =end-start;
                }
                if (measuredTime <= 0){
                    measuredTime=FAILED_CANDIDATE_TIMING;
                }
                measurements.add(measuredTime);
            }
    
            return medianBenchmarks(measurements);
        }
        catch(RuntimeException e){
            logger.debug("\tCandidate %s for %s failed: %s", arrayToString(candidateLocalWork), taskId, e.getMessage());
            return FAILED_CANDIDATE_TIMING;
        }
    }

    private long medianBenchmarks(List<Long> values) {
        if (values.isEmpty()){
            return FAILED_CANDIDATE_TIMING;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            if(sorted.get(middle)==FAILED_CANDIDATE_TIMING){
                    return FAILED_CANDIDATE_TIMING;
            }
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2;
        }
        return sorted.get(middle);
    }

    /**
     * Returns the candidate with the lowest timing as an {@link AutoTuneResult}. Ties go to the earlier
     * candidate, so the backend default ({@code null}, always first) wins ties.
     */
    private AutoTuneResult selectBest(String taskId, TornadoDevice device, long kernelSignatureHash, long[] globalWorkSize, List<int[]> candidates, List<Long> timings){
        int bestIndex = 0;
        for (int i = 1; i < timings.size(); i++) {
            if (timings.get(i) < timings.get(bestIndex)) {
                bestIndex = i;
            }
        }
        return new AutoTuneResult(taskId, device, kernelSignatureHash, globalWorkSize, candidates.get(bestIndex), timings.get(bestIndex));
    }

    /** Adds a {@link WorkerGrid} with the global size and the winning local size to {@code effectiveGridScheduler}. */
    private void applyResult(GridScheduler effectiveGridScheduler, String taskId, long[] globalWorkSize, AutoTuneResult result) {
        WorkerGrid workerGrid = buildWorkerGrid(globalWorkSize);

        int[] winningLocalWork = result.getWinningLocalWork();
        if (winningLocalWork == null) {
            workerGrid.setLocalWorkToNull();
        } else {
            long x = winningLocalWork.length > 0 ? winningLocalWork[0] : 1;
            long y = winningLocalWork.length > 1 ? winningLocalWork[1] : 1;
            long z = winningLocalWork.length > 2 ? winningLocalWork[2] : 1;
            workerGrid.setLocalWork(x, y, z);
        }

        effectiveGridScheduler.addWorkerGrid(taskId, workerGrid);
    }

    private WorkerGrid buildWorkerGrid(long[] globalWorkSize) {
        return switch (globalWorkSize.length) {
            case 1 -> new WorkerGrid1D((int) globalWorkSize[0]);
            case 2 -> new WorkerGrid2D((int) globalWorkSize[0], (int) globalWorkSize[1]);
            case 3 -> new WorkerGrid3D((int) globalWorkSize[0], (int) globalWorkSize[1], (int) globalWorkSize[2]);
            default -> throw new IllegalStateException("[ERROR] Unsupported global work dimensionality: " + globalWorkSize.length);
        };
    }

    /** The WorkerGrid the user attached to this task, or null. The runtime launches from it when present. */
    private WorkerGrid userGridOf(SchedulableTask task) {
        if (userGridScheduler == null || !userGridScheduler.keySet().contains(task.getId())) {
            return null;
        }
        return userGridScheduler.get(task.getId());
    }
    
    private Optional<AutoTuneResult> checkCache(String taskId, TornadoDevice device, long kernelSignatureHash, long[] globalWorkSize) {
    return AutoTuneCache.get(device, taskId, kernelSignatureHash, globalWorkSize);
}

private void storeInCache(AutoTuneResult result) {
    AutoTuneCache.put(result);
}
    
    // private Optional<AutoTuneResult> checkCache(String taskId, TornadoDevice device, long kernelSignatureHash, long[] globalWorkSize) {
    //     return AutoTuneCache.get(device, taskId, kernelSignatureHash, globalWorkSize);
    // }

    /** The hash of the method's string form for a task built from a Java method, {@code 0} for any other task. It identifies the kernel by its method, so it does not notice a changed body of the same method within one JVM. */
    private static long kernelSignatureHash(SchedulableTask task) {
        if (task instanceof CompilableTask compilableTask) {
            return compilableTask.getMethod().toString().hashCode();
        }
        return 0L;
    }

    // private void storeInCache(AutoTuneResult result) {
    //     AutoTuneCache.put(result);
    // }

    /**
     * Formats an {@code int[]} candidate (or {@code null}, meaning
     * "backend default") for log output.
     */
    private static String arrayToString(int[] values) {
        if (values == null) {
            return "<backend default>";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values[i]);
        }
        return builder.append("]").toString();
    }
}