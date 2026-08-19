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
package uk.ac.manchester.tornado.drivers.cuda;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

/**
 * Registry for actions that run when a CUDA stream reaches a given point, using
 * {@code cuLaunchHostFunc}. A caller registers an action, gets a token, and enqueues that token on a
 * queue; the driver invokes the native trampoline when the preceding stream work has completed, and
 * the trampoline calls {@link #fireFromDriverThread(long)}.
 *
 * <p>The action is <b>not</b> run on the driver's thread. That thread may not call into CUDA and may
 * not block without stalling other streams, so this class only hands the action to its own
 * single-threaded executor. Everything the action does is therefore ordinary Java on an ordinary
 * thread, at the cost of a hand-off.
 *
 * <p>Tokens are one-shot: firing removes the registration. A token that is registered but never
 * enqueued (or whose enqueue failed) leaks its entry, so callers that fail to enqueue must
 * {@link #cancel(long)} it.
 */
public final class CUDAHostCallbacks {

    private static final Map<Long, Runnable> ACTIONS = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1);
    private static final TornadoLogger LOGGER = new TornadoLogger(CUDAHostCallbacks.class);

    /**
     * Single daemon thread: callbacks are rare (one per staged chunk at most) and running them in
     * order keeps the ordering of stream completions they were enqueued in.
     */
    private static final ExecutorService DISPATCHER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TornadoVM-CUDA-host-callbacks");
        thread.setDaemon(true);
        return thread;
    });

    private CUDAHostCallbacks() {
    }

    /**
     * Registers {@code action} and returns its token. The token must be enqueued on a queue with
     * {@link CUDACommandQueue#enqueueHostCallback(long)}, or released with {@link #cancel(long)}.
     */
    public static long register(Runnable action) {
        long token = NEXT_TOKEN.getAndIncrement();
        ACTIONS.put(token, action);
        return token;
    }

    /** Drops a registration whose enqueue did not happen, so the action is never run. */
    public static void cancel(long token) {
        ACTIONS.remove(token);
    }

    /**
     * Called by the native trampoline on a CUDA driver thread. Does nothing but move the action to
     * {@link #DISPATCHER}; must stay free of CUDA calls, blocking and anything that can throw into the
     * driver.
     *
     * @param token
     *     the token the callback was enqueued with
     */
    @SuppressWarnings("unused") // invoked from CUDAHostFunc.cpp
    static void fireFromDriverThread(long token) {
        Runnable action = ACTIONS.remove(token);
        if (action == null) {
            return;
        }
        try {
            DISPATCHER.execute(() -> {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    LOGGER.warn("host callback action failed: %s", e);
                }
            });
        } catch (RuntimeException e) {
            // Executor rejected the task (JVM shutting down): run nothing rather than risk the
            // driver thread.
            LOGGER.warn("host callback could not be dispatched: %s", e);
        }
    }

    /** Number of registered-but-not-yet-fired tokens. Diagnostics and tests only. */
    public static int pending() {
        return ACTIONS.size();
    }
}
