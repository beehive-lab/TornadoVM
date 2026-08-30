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

#ifndef TORNADO_DECODER_H
#define TORNADO_DECODER_H

#include <stdint.h>

/*
 * Decoding of the TornadoVM bytecode stream.
 *
 * The stream is a flat byte array: one opcode byte followed by that opcode's operands,
 * written little-endian and without padding.
 *
 * The layout of each bytecode is defined by the assembler that writes it,
 * tornado-runtime/src/main/java/uk/ac/manchester/tornado/runtime/graph/TornadoVMBytecodeBuilder.java
 * (inner class BytecodeAssembler). If any changes are made to the bytecode layout, they
 * should be reflected here.
 *
*/

/*
 * Returned instead of a `position` by any function below that cannot decode the input
 * (an unknown opcode, or operands that run past `limit`).
 */
enum { TORNADO_DECODE_ERROR = -1 };

/* INIT: written once at the beginning of the buffer. */
struct TornadoInitOperands {
    int32_t numContexts;
    int32_t numStacks;
    int32_t numDeps;
};

/*
 * A single int operand. Shared by BARRIER, ADD_DEPENDENCY, CONTEXT, DEALLOC, both
 * PUSH_*_ARGUMENT and all four CUDA_GRAPH_* bytecodes.
 */
struct TornadoIndexOperand {
    int32_t index;
};

/* ON_DEVICE and PERSIST. */
struct TornadoObjectEventOperands {
    int32_t objectIndex;
    int32_t eventId;
};

/* All four transfer bytecodes, which share the same layout. */
struct TornadoTransferOperands {
    int32_t objectIndex;
    int32_t eventId;
    int64_t offset;
    int64_t sizeBatch;
};

/*
 * ALLOC.
 */
struct TornadoAllocOperands {
    int64_t sizeBatch;
    int32_t argCount;
    int32_t argsPosition;
};

/*
 * LAUNCH.
 */
struct TornadoLaunchOperands {
    int32_t callWrapperIndex;
    int32_t taskIndex;
    int32_t numArgs;
    int32_t eventId;
    int64_t offset;
    int64_t batchThreads;
    int32_t argsPosition;
};

/* One entry of a LAUNCH argument list. */
struct TornadoPushArgument {
    /* PUSH_CONSTANT_ARGUMENT or PUSH_REFERENCE_ARGUMENT. */
    uint8_t kind;
    /* Index into the constants list or the objects list, depending on `kind`. */
    int32_t index;
};

/*
 * Number of operand bytes that follow the opcode at `position`. 
 * Returns TORNADO_DECODE_ERROR in case of error.
 */
int32_t tornado_operand_bytes(const uint8_t *code, int32_t position, int32_t limit);

/*
 * Position of the bytecode after the one at `position`. This is the counterpart of
 * TornadoVMInterpreter.skipBytecodeOperands.
 * 
 * Returns TORNADO_DECODE_ERROR in case of error (unlike java version which silently does nothing in case of unknown opcode).
 */
int32_t tornado_next_bytecode(const uint8_t *code, int32_t position, int32_t limit);

/*
 * Position after `numArgs` (opcode, index) pairs starting at `position`. The counterpart of
 * TornadoVMInterpreter.popArgumentsFromCall.
 */
int32_t tornado_skip_push_arguments(const uint8_t *code, int32_t position, int32_t limit, int32_t numArgs);

/*
 * Position after the CUDA_GRAPH_END_CAPTURE bytecode that closes `graphId`, scanning forward
 * from `position`. Returns `limit` when the stream ends first, matching
 * TornadoVMInterpreter.skipToAfterEndCapture
 */
int32_t tornado_skip_to_after_end_capture(const uint8_t *code, int32_t position, int32_t limit, int32_t graphId);

/*
 * The decoders below each take a `position` pointing at their own opcode, fill `out` and
 * return the position of the next bytecode. They return TORNADO_DECODE_ERROR when the
 * opcode at `position` is not one they decode, or when its operands run past `limit`.
 */

int32_t tornado_decode_init(const uint8_t *code, int32_t position, int32_t limit, TornadoInitOperands *out);

/* BARRIER, ADD_DEPENDENCY, CONTEXT, DEALLOC, PUSH_*_ARGUMENT and the CUDA_GRAPH_* bytecodes. */
int32_t tornado_decode_index(const uint8_t *code, int32_t position, int32_t limit, TornadoIndexOperand *out);

/* ON_DEVICE and PERSIST. */
int32_t tornado_decode_object_event(const uint8_t *code, int32_t position, int32_t limit, TornadoObjectEventOperands *out);

/* The four transfer bytecodes. */
int32_t tornado_decode_transfer(const uint8_t *code, int32_t position, int32_t limit, TornadoTransferOperands *out);

/* ALLOC. */
int32_t tornado_decode_alloc(const uint8_t *code, int32_t position, int32_t limit, TornadoAllocOperands *out);

/*
 * Reads one object index out of the inline list of an already decoded ALLOC. `argIndex` must
 * be in [0, ops->argCount). Returns false and leaves `out` untouched otherwise.
 */
bool tornado_decode_alloc_argument(const uint8_t *code, const TornadoAllocOperands *ops, int32_t argIndex, int32_t *out);

/* LAUNCH. */
int32_t tornado_decode_launch(const uint8_t *code, int32_t position, int32_t limit, TornadoLaunchOperands *out);

/*
 * Reads one entry of a LAUNCH argument list and returns the position of the next entry.
 * Returns TORNADO_DECODE_ERROR in case of error
 */
int32_t tornado_decode_push_argument(const uint8_t *code, int32_t position, int32_t limit, TornadoPushArgument *out);

#endif /* TORNADO_DECODER_H */
