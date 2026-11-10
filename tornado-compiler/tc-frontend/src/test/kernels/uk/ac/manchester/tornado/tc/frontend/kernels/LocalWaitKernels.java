/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.tc.frontend.kernels;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Threads waiting in a loop for a flag in local memory. On a GPU whose 64-lane wavefronts run two
 * 32-lane warps in lockstep, a wait for a write from the waiting thread's own wavefront can hang. A
 * wait for a value written before it starts completes. A wait for a write from another wavefront can
 * also hang, for example when two wavefronts release each other's waiters, or in a ticket-style wait
 * in which every thread writes after its own wait.
 */
public final class LocalWaitKernels {

    private LocalWaitKernels() {
    }

    /** Threads 32 and up wait for thread 0: thread 32 shares thread 0's wavefront. */
    public static void sameWavefront(KernelContext context, IntArray out) {
        int[] flag = context.allocateIntLocalArray(1);
        if (context.localIdx == 0) {
            flag[0] = 0;
        }
        context.localBarrier();
        if (context.localIdx >= 32) {
            while (context.atomicCAS(flag, 0, 1, 1) != 1) {
            }
        } else if (context.localIdx == 0) {
            context.atomicExchange(flag, 0, 1);
        }
        out.set(context.globalIdx, context.localIdx);
    }

    /** Threads 65 and up wait for thread 64: both in the second wavefront. */
    public static void sameSecondWavefront(KernelContext context, IntArray out) {
        int[] flag = context.allocateIntLocalArray(1);
        if (context.localIdx == 0) {
            flag[0] = 0;
        }
        context.localBarrier();
        if (context.localIdx > 64) {
            while (context.atomicCAS(flag, 0, 1, 1) != 1) {
            }
        } else if (context.localIdx == 64) {
            context.atomicExchange(flag, 0, 1);
        }
        out.set(context.globalIdx, context.localIdx);
    }

    /** The only write comes before a barrier, so the wait ends at its first test. */
    public static void writtenBeforeBarrier(KernelContext context, IntArray out) {
        int[] flag = context.allocateIntLocalArray(1);
        if (context.localIdx == 0) {
            flag[0] = 1;
        }
        context.localBarrier();
        while (context.atomicCAS(flag, 0, 1, 1) != 1) {
        }
        out.set(context.globalIdx, context.localIdx);
    }

    /** The thread that writes is the thread that waits, and it wrote first. */
    public static void ownWrite(KernelContext context, IntArray out) {
        int[] flag = context.allocateIntLocalArray(128);
        flag[context.localIdx] = 1;
        while (context.atomicCAS(flag, context.localIdx, 1, 1) != 1) {
        }
        out.set(context.globalIdx, context.localIdx);
    }

    /** Thread 0 writes, then threads 32 and up wait: all threads meet again between the two. */
    public static void writtenBeforeWaiting(KernelContext context, IntArray out) {
        int[] flag = context.allocateIntLocalArray(1);
        if (context.localIdx == 0) {
            flag[0] = 0;
        }
        context.localBarrier();
        if (context.localIdx == 0) {
            context.atomicExchange(flag, 0, 1);
        }
        if (context.localIdx >= 32) {
            while (context.atomicCAS(flag, 0, 1, 1) != 1) {
            }
        }
        out.set(context.globalIdx, context.localIdx);
    }

    /** With 128 threads, threads 0-63 wait for thread 64, which is in the other wavefront. */
    public static void otherWavefront(KernelContext context, IntArray out) {
        int[] flag = context.allocateIntLocalArray(1);
        if (context.localIdx == 0) {
            flag[0] = 0;
        }
        context.localBarrier();
        if (context.localIdx < 64) {
            while (context.atomicCAS(flag, 0, 1, 1) != 1) {
            }
        } else if (context.localIdx == 64) {
            context.atomicExchange(flag, 0, 1);
        }
        out.set(context.globalIdx, context.localIdx);
    }

    /** Threads 1-63 wait; thread 0 shares their wavefront, but thread 64, in the next one, also writes. */
    public static void writersInTwoWavefronts(KernelContext context, IntArray out) {
        int[] flag = context.allocateIntLocalArray(1);
        if (context.localIdx == 0) {
            flag[0] = 0;
        }
        context.localBarrier();
        if (context.localIdx == 0 || context.localIdx == 64) {
            context.atomicExchange(flag, 0, 1);
        } else if (context.localIdx < 64) {
            while (context.atomicCAS(flag, 0, 1, 1) != 1) {
            }
        }
        out.set(context.globalIdx, context.localIdx);
    }

    /**
     * Threads 0-7 wait for thread 40, in their wavefront. Launched with a two-dimensional work-group of
     * rows of 56 threads, thread 40 of the second row is in a wavefront without waiters and sets the flag
     * for every row, so the wait completes; the compiler sees no launch shape and refuses it.
     */
    public static void rowsOfFiftySix(KernelContext context, IntArray out) {
        int[] flag = context.allocateIntLocalArray(1);
        if (context.localIdx == 0) {
            flag[0] = 0;
        }
        context.localBarrier();
        if (context.localIdx == 40) {
            context.atomicExchange(flag, 0, 1);
        } else if (context.localIdx < 8) {
            while (context.atomicCAS(flag, 0, 1, 1) != 1) {
            }
        }
        out.set(context.globalIdx, context.localIdx);
    }

    /**
     * In each row of a two-dimensional work-group, threads 0-7 wait for thread 40. With rows of 56
     * threads, thread 40 of the second row is in a wavefront without waiters and sets the flag.
     */
    public static void twoDimensional(KernelContext context, IntArray out) {
        int[] flag = context.allocateIntLocalArray(1);
        if (context.localIdx == 0 && context.localIdy == 0) {
            flag[0] = 0;
        }
        context.localBarrier();
        if (context.localIdx == 40) {
            context.atomicExchange(flag, 0, 1);
        } else if (context.localIdx < 8) {
            while (context.atomicCAS(flag, 0, 1, 1) != 1) {
            }
        }
        out.set(context.globalIdx, context.localIdx);
    }
}
