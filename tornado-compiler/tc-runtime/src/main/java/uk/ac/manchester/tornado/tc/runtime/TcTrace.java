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
package uk.ac.manchester.tornado.tc.runtime;

import java.util.function.Supplier;

import uk.ac.manchester.tornado.tc.codegen.CEmitter;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.lower.S;

/**
 * Observes a compilation as it happens, for tools that show the pipeline (the IR visualiser): the
 * live IR after each stage and after each optimisation pass, the structured kernel and the emitted
 * source with its line origins. The objects are the compiler's own and keep their identity from one
 * call to the next (passes change the IR in place), so a tool can follow one instruction through the
 * pipeline; they must not be changed. Installed per thread with {@link #with}; off by default.
 */
public interface TcTrace {

    /**
     * After pipeline stage {@code stage} ({@code pass} null), or after one optimisation pass inside
     * it. {@code changed} is false when the pass left the function unchanged.
     */
    default void ir(String stage, String pass, boolean changed, long nanos, Function function) {
    }

    /** The structured (non-SSA) kernel of {@code function}, before emission. */
    default void structured(Function function, S.Kernel kernel) {
    }

    /** The emitted source of {@code function}; {@link CEmitter.Result#origin} maps its lines back. */
    default void emitted(Function function, CEmitter.Result result) {
    }

    /** Runs {@code work} with {@code trace} observing every compilation on this thread. */
    static <T> T with(TcTrace trace, Supplier<T> work) {
        var previous = Holder.CURRENT.get();
        Holder.CURRENT.set(trace);
        try {
            return work.get();
        } finally {
            Holder.CURRENT.set(previous);
        }
    }

    /** The trace installed on this thread, or one that ignores everything. */
    static TcTrace current() {
        var trace = Holder.CURRENT.get();
        return trace != null ? trace : Holder.NONE;
    }

    /** Per-thread state of {@link TcTrace}. */
    final class Holder {
        private static final ThreadLocal<TcTrace> CURRENT = new ThreadLocal<>();
        private static final TcTrace NONE = new TcTrace() {
        };

        private Holder() {
        }
    }
}
