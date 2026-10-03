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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

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
 * Handing a step to the rank threads and waiting for them uses a short spin before parking. With
 * thread pools and futures, every step paid two thread wake-ups in a row (rank threads waking for
 * the step, then the caller waking for the result) and the ranks started the collective at
 * different times; on a two-socket host that doubled the time of a 4 MiB exchange. The spin is
 * bounded by {@code -Dtornado.nccl.spinWaitMicros} (default 1000; 0 parks straight away), so a
 * thread that has nothing to do parks after that long instead of holding a core.
 * </p>
 *
 * <p>
 * Closing the group stops its threads; it does not close the plans.
 * </p>
 */
public final class NcclPlanGroup implements AutoCloseable {

    private static final long SPIN_WAIT_NANOS = Long.getLong("tornado.nccl.spinWaitMicros", 1000L) * 1000L;

    private final TornadoExecutionPlan[] plans;
    private final Thread[] threads;
    private final TornadoExecutionResult[] results;
    private final Throwable[] failures;

    /** Incremented for every step; a rank thread runs its plan once per new value. */
    private final AtomicLong step = new AtomicLong();
    /** Rank threads still running the current step. */
    private final AtomicInteger running = new AtomicInteger();
    private volatile Thread caller;
    private volatile boolean closed;

    public NcclPlanGroup(TornadoExecutionPlan... plans) {
        if (plans == null || plans.length == 0) {
            throw new TornadoRuntimeException("[ERROR] An NCCL plan group needs at least one plan");
        }
        this.plans = plans.clone();
        this.results = new TornadoExecutionResult[plans.length];
        this.failures = new Throwable[plans.length];
        this.threads = new Thread[plans.length];
        for (int rank = 0; rank < plans.length; rank++) {
            final int r = rank;
            threads[rank] = new Thread(() -> runRank(r), "tornado-nccl-rank-" + rank);
            threads[rank].setDaemon(true);
            threads[rank].start();
        }
    }

    private void runRank(int rank) {
        long done = 0;
        while (true) {
            long next = awaitStep(done);
            if (closed) {
                return;
            }
            done = next;
            try {
                results[rank] = plans[rank].execute();
            } catch (Throwable t) {
                failures[rank] = t;
            }
            if (running.decrementAndGet() == 0) {
                LockSupport.unpark(caller);
            }
        }
    }

    /** Waits until the step counter moves past {@code done}: spins for a while, then parks. */
    private long awaitStep(long done) {
        long spinUntil = System.nanoTime() + SPIN_WAIT_NANOS;
        long current;
        while ((current = step.get()) == done && !closed) {
            if (System.nanoTime() < spinUntil) {
                Thread.onSpinWait();
            } else {
                LockSupport.park(this);
            }
        }
        return current;
    }

    /**
     * Executes every plan once, concurrently, and returns when all of them have finished, with the
     * result of each plan in rank order. If any plan fails, the first failure is rethrown after the
     * others have finished. Steps of one group must not be executed from several threads at once.
     */
    public TornadoExecutionResult[] execute() {
        if (closed) {
            throw new TornadoRuntimeException("[ERROR] The NCCL plan group is closed");
        }
        caller = Thread.currentThread();
        Arrays.fill(failures, null);
        running.set(plans.length);
        step.incrementAndGet();
        for (Thread thread : threads) {
            LockSupport.unpark(thread);
        }

        long spinUntil = System.nanoTime() + SPIN_WAIT_NANOS;
        while (running.get() != 0) {
            if (System.nanoTime() < spinUntil) {
                Thread.onSpinWait();
            } else {
                LockSupport.park(this);
            }
        }

        for (int rank = 0; rank < failures.length; rank++) {
            if (failures[rank] != null) {
                TornadoRuntimeException failure = new TornadoRuntimeException("[ERROR] Plan of NCCL rank " + rank + " failed: " + failures[rank].getMessage());
                failure.initCause(failures[rank]);
                throw failure;
            }
        }
        return results.clone();
    }

    @Override
    public void close() {
        closed = true;
        for (Thread thread : threads) {
            LockSupport.unpark(thread);
        }
    }
}
