/*
 * MIT License
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef TORNADO_INTERPRETER_H
#define TORNADO_INTERPRETER_H

#include <stdint.h>

#include "tornado_context.h"

/*
 * Outcome of a call to tornado_interpret. These values MUST be kept in sync with
 * the STATUS_* constants in
 * tornado-runtime/src/main/java/uk/ac/manchester/tornado/runtime/interpreter/NativeBytecodeInterpreter.java
 */
enum TornadoInterpreterStatus {
    /* An END bytecode was consumed. The returned position is just past it. */
    TORNADO_STATUS_END = 0,
    /* The buffer was exhausted without reaching END. The returned position is the limit. */
    TORNADO_STATUS_EOF = 1,
    /*
     * The next bytecode is not handled natively. The returned position points AT that
     * bytecode, so that the Java interpreter can decode and execute it from scratch.
     */
    TORNADO_STATUS_BAIL = 2,
    /* A handled bytecode or backend operation failed. */
    TORNADO_STATUS_ERROR = 3
};

/* Bit flags for the `flags` argument of tornado_interpret. */
enum TornadoInterpreterFlags {
    /*
     * Set when the Java interpreter is running a warm-up pass, which compiles kernels but
     * replays nothing.
     */
    TORNADO_FLAG_WARMUP = 1,
    /* Set when Java `useDependencies` is true. */
    TORNADO_FLAG_USE_DEPENDENCIES = 2,
    /*
     * Set when a CUDA/execution graph has already been instantiated
     * (`executionGraphHandles` is non-empty).
     */
    TORNADO_FLAG_GRAPH_INSTANTIATED = 8,
    /* Phase 5 batch memory operations still use the Java handlers. */
    TORNADO_FLAG_BATCHED_EXECUTION = 16,
    /* Java must release the whole free-buffer pool for this DEALLOC mode. */
    TORNADO_FLAG_FORCE_DEALLOCATION = 32,
    /* Flush the backend queue when END is consumed. */
    TORNADO_FLAG_USE_VM_FLUSH = 64
};

/*
 * Runs the TornadoVM bytecode loop over `ctx->code` starting at `ctx->position`, stopping
 * at the first bytecode that cannot be handled natively.
 *
 * `ctx` must not be null. `ctx->code` must hold at least `ctx->limit` readable bytes,
 * and `ctx->position` must satisfy 0 <= position <= limit. The table pointers may be
 * null when the corresponding count is 0.
 *
 * Returns a TornadoInterpreterStatus in the high 32 bits and the resulting position in
 * the low 32 bits. The position is always in [0, limit] and always a valid bytecode
 * boundary, so the Java interpreter can resume from it directly.
 *
 * ALLOC and TRANSFER call the selected backend directly. Normal DEALLOC moves the
 * handle into Java's existing buffer pool without a driver call.
 * KernelContext and AtomicInteger have no per-object buffer: ALLOC, TRANSFER,
 * DEALLOC and LAUNCH skip them. Java writes the shared atomics region before the
 * native call and copies AtomicInteger values back afterwards.
 * BARRIER is not handled here: the loop bails and Java runs enqueueMarker.
 *
 */
int64_t tornado_interpret(const TornadoInterpreterContext *ctx);

#endif /* TORNADO_INTERPRETER_H */
