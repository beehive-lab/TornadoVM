/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.unittests.common;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Helpers for the library-task tests that run one plan from two threads. TornadoVM gives each
 * thread its own stream for a plan, so a library call has to follow the stream of the thread
 * running the plan; if it stays on the stream of the thread that first ran it, it is unordered
 * with the kernels that produce its inputs. The tests let a slow kernel write a value the library
 * call reads, then run the plan again from another thread with a new value.
 */
public final class LibraryStreamPerThread {

    /** Iterations of the dependent loop in the fill kernels: a few milliseconds on a GPU. */
    public static final int SPINS = 1_000_000;

    private static final int LOCAL_SIZE = 256;

    private LibraryStreamPerThread() {
    }

    /**
     * Writes {@code value[0]} to every element, slowly, so a library call that does not wait for it
     * reads stale data. One block strides over the array, which leaves the rest of the GPU free: a
     * library kernel that is not ordered after this one then runs alongside it rather than queueing
     * behind it for want of room, and so does read the stale data.
     */
    public static void slowFill(KernelContext context, FloatArray a, FloatArray value, int spins) {
        int id = context.globalIdx;
        if (id < context.globalGroupSizeX) {
            float v = value.get(0);
            float x = v;
            for (int s = 0; s < spins; s++) {
                x = x * 1.0000001f - v * 0.0000001f;
            }
            for (int i = id; i < a.getSize(); i += context.globalGroupSizeX) {
                a.set(i, x);
            }
        }
    }

    /** {@link #slowFill(KernelContext, FloatArray, FloatArray, int)} for doubles. */
    public static void slowFillDouble(KernelContext context, DoubleArray a, DoubleArray value, int spins) {
        int id = context.globalIdx;
        if (id < context.globalGroupSizeX) {
            double v = value.get(0);
            double x = v;
            for (int s = 0; s < spins; s++) {
                x = x * 1.0000001 - v * 0.0000001;
            }
            for (int i = id; i < a.getSize(); i += context.globalGroupSizeX) {
                a.set(i, x);
            }
        }
    }

    /** The grid for one of the fill kernels: a single block. */
    public static GridScheduler gridScheduler(String taskName) {
        WorkerGrid worker = new WorkerGrid1D(LOCAL_SIZE);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        return new GridScheduler(taskName, worker);
    }

    /** Executes the plan from a new thread, and rethrows whatever it threw there. */
    public static void executeOnAnotherThread(TornadoExecutionPlan plan) throws InterruptedException {
        Throwable[] failure = new Throwable[1];
        Thread other = new Thread(() -> {
            try {
                plan.execute();
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        other.start();
        other.join();
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }
}
