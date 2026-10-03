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

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
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
 * If the plan of one rank fails, the other ranks would wait for it forever inside NCCL. The group
 * therefore aborts the communicators its ranks have used ({@link NcclCommunicator#abort()}), which
 * makes the waiting ranks return, and {@link #execute()} throws the failure of the rank that failed
 * first. Aborted communicators cannot be used again: create a new communicator and new plans.
 * </p>
 *
 * <p>
 * A step can also hang without any rank failing, for instance on a {@code send} that no rank
 * receives. {@link #withStepTimeout(Duration)} bounds a step: when it runs out, the group aborts
 * the communicators in the same way and {@link #execute()} throws, naming the ranks still running.
 * </p>
 *
 * <p>
 * Closing the group stops its threads; it does not close the plans.
 * </p>
 */
public final class NcclPlanGroup implements AutoCloseable {

    private static final long SPIN_WAIT_NANOS = Long.getLong("tornado.nccl.spinWaitMicros", 1000L) * 1000L;

    /** How often the caller re-aborts, after a rank failed, while other ranks have not returned yet. */
    private static final long ABORT_POLL_NANOS = 1_000_000L;

    private final TornadoExecutionPlan[] plans;
    private final Thread[] threads;
    private final TornadoExecutionResult[] results;
    private final Throwable[] failures;

    /** Communicators the NCCL tasks of the rank threads have used; aborted if a rank fails. */
    private final Set<NcclCommunicator> used = ConcurrentHashMap.newKeySet();
    /** The rank whose plan failed first in the current step, or -1. */
    private final AtomicInteger firstFailure = new AtomicInteger(-1);

    /** Incremented for every step; a rank thread runs its plan once per new value. */
    private final AtomicLong step = new AtomicLong();
    /** Rank threads still running the current step. */
    private final AtomicInteger running = new AtomicInteger();
    private volatile Thread caller;
    private volatile boolean closed;

    /** Longest a step may take before the group gives up on it; 0 waits forever. */
    private volatile long stepTimeoutNanos;
    /** Ranks that have finished the current step (1) or not (0), for the timeout message. */
    private final AtomicIntegerArray finished;
    /** Set when ranks never returned, even after their communicators were aborted. */
    private volatile String unusableReason;

    public NcclPlanGroup(TornadoExecutionPlan... plans) {
        if (plans == null || plans.length == 0) {
            throw new TornadoRuntimeException("[ERROR] An NCCL plan group needs at least one plan");
        }
        this.plans = plans.clone();
        this.results = new TornadoExecutionResult[plans.length];
        this.failures = new Throwable[plans.length];
        this.finished = new AtomicIntegerArray(plans.length);
        this.threads = new Thread[plans.length];
        for (int rank = 0; rank < plans.length; rank++) {
            final int r = rank;
            threads[rank] = new Thread(() -> runRank(r), "tornado-nccl-rank-" + rank);
            threads[rank].setDaemon(true);
            threads[rank].start();
        }
    }

    private void runRank(int rank) {
        NcclCommunicator.trackUsesOnThisThread(used);
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
                if (firstFailure.compareAndSet(-1, rank)) {
                    // The other ranks may be waiting for this one inside NCCL. The caller aborts
                    // the communicators: this thread may have failed in the middle of capturing a
                    // CUDA graph, and its stream is then still capturing, which NCCL's abort
                    // cannot work from.
                    LockSupport.unpark(caller);
                }
            }
            finished.set(rank, 1);
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
     * Bounds every step: if the plans of all ranks have not finished {@code timeout} after
     * {@link #execute()} started them, the group aborts the communicators its ranks have used and
     * {@code execute()} throws. Without a timeout (the default), a step that hangs without any rank
     * failing, such as a {@code send} that no rank receives, waits forever.
     */
    public NcclPlanGroup withStepTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new TornadoRuntimeException("[ERROR] The step timeout of an NCCL plan group must be positive");
        }
        stepTimeoutNanos = timeout.toNanos();
        return this;
    }

    /**
     * Executes every plan once, concurrently, and returns when all of them have finished, with the
     * result of each plan in rank order. If any plan fails, or the step runs past its timeout, the
     * communicators the ranks have used are aborted and the failure is thrown once the ranks have
     * returned. Steps of one group must not be executed from several threads at once.
     */
    public TornadoExecutionResult[] execute() {
        if (closed) {
            throw new TornadoRuntimeException("[ERROR] The NCCL plan group is closed");
        }
        if (unusableReason != null) {
            throw new TornadoRuntimeException("[ERROR] The NCCL plan group cannot run any more: " + unusableReason);
        }
        caller = Thread.currentThread();
        Arrays.fill(failures, null);
        firstFailure.set(-1);
        for (int rank = 0; rank < plans.length; rank++) {
            finished.set(rank, 0);
        }
        running.set(plans.length);
        final long timeout = stepTimeoutNanos;
        final long start = System.nanoTime();
        step.incrementAndGet();
        for (Thread thread : threads) {
            LockSupport.unpark(thread);
        }

        String timedOutRanks = null;
        long abortedAt = 0;
        long spinUntil = start + SPIN_WAIT_NANOS;
        while (running.get() != 0) {
            long now = System.nanoTime();
            if (firstFailure.get() >= 0 || timedOutRanks != null) {
                // Release the ranks waiting for the failed one. A rank still running may only now
                // reach a communicator, so keep aborting what the ranks have used until all return.
                abortUsed();
                if (abortedAt == 0) {
                    abortedAt = now;
                } else if (timeout > 0 && now - abortedAt > timeout) {
                    unusableReason = "ranks " + unfinishedRanks() + " did not return even after their NCCL communicators were aborted";
                    throw new TornadoRuntimeException("[ERROR] NCCL step abandoned: " + unusableReason + ". Their threads are still running; create a new plan group.");
                }
                LockSupport.parkNanos(this, ABORT_POLL_NANOS);
            } else if (timeout > 0 && now - start > timeout) {
                timedOutRanks = unfinishedRanks();
            } else if (now < spinUntil) {
                Thread.onSpinWait();
            } else if (timeout > 0) {
                LockSupport.parkNanos(this, timeout - (now - start));
            } else {
                LockSupport.park(this);
            }
        }

        int rank = firstFailure.get();
        boolean anyAborted = used.stream().anyMatch(NcclCommunicator::isAborted);
        String aborted = anyAborted ? " The NCCL communicators of this group were aborted; create new ones and new plans." : "";
        if (timedOutRanks != null) {
            TornadoRuntimeException failure = new TornadoRuntimeException(
                    "[ERROR] NCCL step did not finish within " + Duration.ofNanos(timeout).toMillis() + " ms; ranks still running: " + timedOutRanks + "." + aborted);
            if (rank >= 0) {
                failure.initCause(failures[rank]);
            }
            throw failure;
        }
        if (rank >= 0) {
            TornadoRuntimeException failure = new TornadoRuntimeException("[ERROR] Plan of NCCL rank " + rank + " failed: " + failures[rank].getMessage() + "." + aborted);
            failure.initCause(failures[rank]);
            throw failure;
        }
        return results.clone();
    }

    private String unfinishedRanks() {
        StringBuilder ranks = new StringBuilder("[");
        for (int rank = 0; rank < plans.length; rank++) {
            if (finished.get(rank) == 0) {
                ranks.append(ranks.length() > 1 ? ", " : "").append(rank);
            }
        }
        return ranks.append("]").toString();
    }

    private void abortUsed() {
        for (NcclCommunicator communicator : used) {
            try {
                communicator.abort();
            } catch (RuntimeException e) {
                // Best effort: the failure being reported is the rank's, not the abort's.
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        for (Thread thread : threads) {
            LockSupport.unpark(thread);
        }
    }
}
