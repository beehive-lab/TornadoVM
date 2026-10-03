/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.nccl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * Executes the plans of the ranks of an NCCL communicator together. A collective completes only
 * once every rank has enqueued it, so the plans cannot run one after the other on one thread: the
 * first would wait for the others forever. Each plan is given a thread of its own and always runs
 * on it, which also keeps it on the same TornadoVM command queue (queues are per thread) from one
 * execution to the next.
 *
 * <pre>{@code
 * try (NcclPlanGroup ranks = new NcclPlanGroup(plan0, plan1)) {
 *     for (int step = 0; step < steps; step++) {
 *         ranks.execute();
 *     }
 * }
 * }</pre>
 *
 * <p>
 * Closing the group stops its threads; it does not close the plans.
 * </p>
 */
public final class NcclPlanGroup implements AutoCloseable {

    private final TornadoExecutionPlan[] plans;
    private final ExecutorService[] threads;

    public NcclPlanGroup(TornadoExecutionPlan... plans) {
        if (plans == null || plans.length == 0) {
            throw new TornadoRuntimeException("[ERROR] An NCCL plan group needs at least one plan");
        }
        this.plans = plans.clone();
        this.threads = new ExecutorService[plans.length];
        for (int rank = 0; rank < plans.length; rank++) {
            final String name = "tornado-nccl-rank-" + rank;
            threads[rank] = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    /**
     * Executes every plan once, concurrently, and returns when all of them have finished, with the
     * result of each plan in rank order. If any plan fails, the first failure is rethrown after the
     * others have finished.
     */
    public TornadoExecutionResult[] execute() {
        List<Future<TornadoExecutionResult>> running = new ArrayList<>(plans.length);
        for (int rank = 0; rank < plans.length; rank++) {
            final Callable<TornadoExecutionResult> execution = plans[rank]::execute;
            running.add(threads[rank].submit(execution));
        }
        TornadoExecutionResult[] results = new TornadoExecutionResult[plans.length];
        RuntimeException failure = null;
        for (int rank = 0; rank < running.size(); rank++) {
            try {
                results[rank] = running.get(rank).get();
            } catch (ExecutionException e) {
                if (failure == null) {
                    failure = new TornadoRuntimeException("[ERROR] Plan of NCCL rank " + rank + " failed: " + e.getCause().getMessage());
                    failure.initCause(e.getCause());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TornadoRuntimeException(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return results;
    }

    @Override
    public void close() {
        for (ExecutorService thread : threads) {
            thread.shutdown();
        }
    }
}
