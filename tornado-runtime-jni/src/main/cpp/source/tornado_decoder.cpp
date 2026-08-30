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

#include "tornado_decoder.h"
#include "tornado_bytecodes.h"

/* Sizes of how many operand bytes sit after the opcode. Named and grouped by the bytecode.*/
enum {
    OPERANDS_NONE = 0,
    OPERANDS_INDEX = 4,
    OPERANDS_OBJECT_EVENT = 8,
    OPERANDS_INIT = 12,
    /* objectIndex, eventId, offset, sizeBatch */
    OPERANDS_TRANSFER = 24,
    /* sizeBatch, argCount; the object indices follow */
    OPERANDS_ALLOC_HEADER = 12,
    /* callWrapperIndex, taskIndex, numArgs, eventId, offset, batchThreads; the args follow */
    OPERANDS_LAUNCH_HEADER = 32,
    /* One entry of a LAUNCH argument list: a PUSH_* opcode plus its index. */
    BYTES_PER_PUSH_ARGUMENT = 5
};

/*
 * True when `bytes` can be read starting at `position` without running past `limit`.
 */
static inline bool fits(int32_t position, int32_t bytes, int32_t limit) {
    return position >= 0 && bytes >= 0 && limit >= bytes && position <= limit - bytes;
}

/*
 * The two readers below assemble the value from individual bytes. These are little-endian.
 * They are counterparts to the methods getInt() and getLong() in Java.
 */
static inline int32_t read_i32(const uint8_t *code, int32_t position) {
    const uint8_t *p = code + position;
    return (int32_t) ((uint32_t) p[0] | ((uint32_t) p[1] << 8) | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24));
}

static inline int64_t read_i64(const uint8_t *code, int32_t position) {
    const uint8_t *p = code + position;
    return (int64_t) ((uint64_t) p[0] | ((uint64_t) p[1] << 8) | ((uint64_t) p[2] << 16) | ((uint64_t) p[3] << 24) | ((uint64_t) p[4] << 32) | ((uint64_t) p[5] << 40) | ((uint64_t) p[6] << 48)
            | ((uint64_t) p[7] << 56));
}

/* True when the byte at `position` is the opcode `expected`. */
static inline bool opcode_is(const uint8_t *code, int32_t position, int32_t limit, TornadoBytecode expected) {
    return fits(position, 1, limit) && static_cast<TornadoBytecode>(code[position]) == expected;
}

/*
 * Returns the number of bytes of operands present in the bytecode array for the bytecode at `position`.
 */
int32_t tornado_operand_bytes(const uint8_t *code, int32_t position, int32_t limit) {
    if (!fits(position, 1, limit)) {
        return TORNADO_DECODE_ERROR;
    }

    // `operands` is the position of the first operand, `position` is the position of the opcode
    const int32_t operands = position + 1;

    switch (static_cast<TornadoBytecode>(code[position])) {
        case TornadoBytecode::BEGIN:
        case TornadoBytecode::END:
            return OPERANDS_NONE;

        case TornadoBytecode::BARRIER:
        case TornadoBytecode::ADD_DEPENDENCY:
        case TornadoBytecode::CONTEXT:
        case TornadoBytecode::DEALLOC:
        case TornadoBytecode::PUSH_CONSTANT_ARGUMENT:
        case TornadoBytecode::PUSH_REFERENCE_ARGUMENT:
        case TornadoBytecode::CUDA_GRAPH_BEGIN_CAPTURE:
        case TornadoBytecode::CUDA_GRAPH_END_CAPTURE:
        case TornadoBytecode::CUDA_GRAPH_LAUNCH:
        case TornadoBytecode::CUDA_GRAPH_DESTROY:
            return fits(operands, OPERANDS_INDEX, limit) ? OPERANDS_INDEX : TORNADO_DECODE_ERROR;

        case TornadoBytecode::ON_DEVICE:
        case TornadoBytecode::PERSIST:
            return fits(operands, OPERANDS_OBJECT_EVENT, limit) ? OPERANDS_OBJECT_EVENT : TORNADO_DECODE_ERROR;

        case TornadoBytecode::INIT:
            return fits(operands, OPERANDS_INIT, limit) ? OPERANDS_INIT : TORNADO_DECODE_ERROR;

        case TornadoBytecode::TRANSFER_HOST_TO_DEVICE_ONCE:
        case TornadoBytecode::TRANSFER_HOST_TO_DEVICE_ALWAYS:
        case TornadoBytecode::TRANSFER_DEVICE_TO_HOST_ALWAYS:
        case TornadoBytecode::TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING:
            return fits(operands, OPERANDS_TRANSFER, limit) ? OPERANDS_TRANSFER : TORNADO_DECODE_ERROR;

        case TornadoBytecode::ALLOC: {
            if (!fits(operands, OPERANDS_ALLOC_HEADER, limit)) {
                return TORNADO_DECODE_ERROR;
            }
            // ALLOC has [opcode, sizeBatch, argCount], sizeBatch is long, thus we skip 8 bytes,
            // argCount is int thus we use read_i32
            const int32_t argCount = read_i32(code, operands + 8);
            if (argCount < 0 || argCount > (limit - operands - OPERANDS_ALLOC_HEADER) / 4) {
                return TORNADO_DECODE_ERROR;
            }
            return OPERANDS_ALLOC_HEADER + (argCount * 4);
        }

        case TornadoBytecode::LAUNCH: {
            if (!fits(operands, OPERANDS_LAUNCH_HEADER, limit)) {
                return TORNADO_DECODE_ERROR;
            }
            // LAUNCH has [opcode, callWrapperIndex, taskIndex, numArgs, eventId, offset, batchThreads],
            // Thus to read numArgs, we need to skip 8 bytes, as callWrapperIndex and taskIndex are ints,
            // and numArgs is int thus we use read_i32
            const int32_t numArgs = read_i32(code, operands + 8);
            if (numArgs < 0 || numArgs > (limit - operands - OPERANDS_LAUNCH_HEADER) / BYTES_PER_PUSH_ARGUMENT) {
                return TORNADO_DECODE_ERROR;
            }
            return OPERANDS_LAUNCH_HEADER + (numArgs * BYTES_PER_PUSH_ARGUMENT);
        }

        default:
            return TORNADO_DECODE_ERROR;
    }
}

/**
 * Returns the position of the next bytecode after the bytecode at `position`.
 */
int32_t tornado_next_bytecode(const uint8_t *code, int32_t position, int32_t limit) {
    // number of bytes of operands to skip after the opcode
    const int32_t operandBytes = tornado_operand_bytes(code, position, limit);
    if (operandBytes == TORNADO_DECODE_ERROR) {
        return TORNADO_DECODE_ERROR;
    }
    return position + 1 + operandBytes;
}

/**
 * Skips the push arguments for the given number of arguments.
 */
int32_t tornado_skip_push_arguments(const uint8_t *code, int32_t position, int32_t limit, int32_t numArgs) {
    if (numArgs < 0) {
        return TORNADO_DECODE_ERROR;
    }

    int32_t pc = position;
    for (int32_t i = 0; i < numArgs; i++) {
        // not used anywhere but the function signature requires it
        TornadoPushArgument argument;
        pc = tornado_decode_push_argument(code, pc, limit, &argument);
        if (pc == TORNADO_DECODE_ERROR) {
            return TORNADO_DECODE_ERROR;
        }
    }
    return pc;
}

/**
 * Skips to after the end capture for the given graph id.
 */
int32_t tornado_skip_to_after_end_capture(const uint8_t *code, int32_t position, int32_t limit, int32_t graphId) {
    int32_t pc = position;

    while (pc < limit) {
        if (opcode_is(code, pc, limit, TornadoBytecode::CUDA_GRAPH_END_CAPTURE)) {
            TornadoIndexOperand operand;
            const int32_t next = tornado_decode_index(code, pc, limit, &operand);
            if (next == TORNADO_DECODE_ERROR) {
                return TORNADO_DECODE_ERROR;
            }
            /*
             * A capture region closing some other graph is stepped over, not stopped at, so
             * that a nested or adjacent region does not end this scan early.
             */
            if (operand.index == graphId) {
                return next;
            }
            pc = next;
        } else {
            const int32_t next = tornado_next_bytecode(code, pc, limit);
            if (next == TORNADO_DECODE_ERROR) {
                return TORNADO_DECODE_ERROR;
            }
            pc = next;
        }
    }

    return limit;
}

/**
 * Decodes the init operands from the bytecode array at `position`.
 */
int32_t tornado_decode_init(const uint8_t *code, int32_t position, int32_t limit, TornadoInitOperands *out) {
    if (!opcode_is(code, position, limit, TornadoBytecode::INIT) || !fits(position + 1, OPERANDS_INIT, limit)) {
        return TORNADO_DECODE_ERROR;
    }
    const int32_t operands = position + 1;
    out->numContexts = read_i32(code, operands);
    out->numStacks = read_i32(code, operands + 4);
    out->numDeps = read_i32(code, operands + 8);
    return operands + OPERANDS_INIT;
}

/**
 * Decodes the index operands from the bytecode array at `position`.
 */
int32_t tornado_decode_index(const uint8_t *code, int32_t position, int32_t limit, TornadoIndexOperand *out) {
    if (!fits(position, 1, limit)) {
        return TORNADO_DECODE_ERROR;
    }

    switch (static_cast<TornadoBytecode>(code[position])) {
        case TornadoBytecode::BARRIER:
        case TornadoBytecode::ADD_DEPENDENCY:
        case TornadoBytecode::CONTEXT:
        case TornadoBytecode::DEALLOC:
        case TornadoBytecode::PUSH_CONSTANT_ARGUMENT:
        case TornadoBytecode::PUSH_REFERENCE_ARGUMENT:
        case TornadoBytecode::CUDA_GRAPH_BEGIN_CAPTURE:
        case TornadoBytecode::CUDA_GRAPH_END_CAPTURE:
        case TornadoBytecode::CUDA_GRAPH_LAUNCH:
        case TornadoBytecode::CUDA_GRAPH_DESTROY:
            break;
        default:
            return TORNADO_DECODE_ERROR;
    }

    const int32_t operands = position + 1;
    if (!fits(operands, OPERANDS_INDEX, limit)) {
        return TORNADO_DECODE_ERROR;
    }
    out->index = read_i32(code, operands);
    return operands + OPERANDS_INDEX;
}

/**
 * Decodes the object event operands from the bytecode array at `position`.
 */
int32_t tornado_decode_object_event(const uint8_t *code, int32_t position, int32_t limit, TornadoObjectEventOperands *out) {
    if (!fits(position, 1, limit)) {
        return TORNADO_DECODE_ERROR;
    }

    const TornadoBytecode op = static_cast<TornadoBytecode>(code[position]);
    if (op != TornadoBytecode::ON_DEVICE && op != TornadoBytecode::PERSIST) {
        return TORNADO_DECODE_ERROR;
    }

    const int32_t operands = position + 1;
    if (!fits(operands, OPERANDS_OBJECT_EVENT, limit)) {
        return TORNADO_DECODE_ERROR;
    }
    out->objectIndex = read_i32(code, operands);
    out->eventId = read_i32(code, operands + 4);
    return operands + OPERANDS_OBJECT_EVENT;
}

/**
 * Decodes the transfer operands from the bytecode array at `position`.
 */
int32_t tornado_decode_transfer(const uint8_t *code, int32_t position, int32_t limit, TornadoTransferOperands *out) {
    if (!fits(position, 1, limit)) {
        return TORNADO_DECODE_ERROR;
    }

    switch (static_cast<TornadoBytecode>(code[position])) {
        case TornadoBytecode::TRANSFER_HOST_TO_DEVICE_ONCE:
        case TornadoBytecode::TRANSFER_HOST_TO_DEVICE_ALWAYS:
        case TornadoBytecode::TRANSFER_DEVICE_TO_HOST_ALWAYS:
        case TornadoBytecode::TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING:
            break;
        default:
            return TORNADO_DECODE_ERROR;
    }

    const int32_t operands = position + 1;
    if (!fits(operands, OPERANDS_TRANSFER, limit)) {
        return TORNADO_DECODE_ERROR;
    }
    out->objectIndex = read_i32(code, operands);
    out->eventId = read_i32(code, operands + 4);
    out->offset = read_i64(code, operands + 8);
    out->sizeBatch = read_i64(code, operands + 16);
    return operands + OPERANDS_TRANSFER;
}

/**
 * Decodes the alloc operands from the bytecode array at `position`.
 */
int32_t tornado_decode_alloc(const uint8_t *code, int32_t position, int32_t limit, TornadoAllocOperands *out) {
    if (!opcode_is(code, position, limit, TornadoBytecode::ALLOC)) {
        return TORNADO_DECODE_ERROR;
    }

    const int32_t operandBytes = tornado_operand_bytes(code, position, limit);
    if (operandBytes == TORNADO_DECODE_ERROR) {
        return TORNADO_DECODE_ERROR;
    }

    const int32_t operands = position + 1;
    out->sizeBatch = read_i64(code, operands);
    out->argCount = read_i32(code, operands + 8);
    out->argsPosition = operands + OPERANDS_ALLOC_HEADER;
    return operands + operandBytes;
}

/**
 * Decodes an alloc argument from the bytecode array at `position`.
 */
bool tornado_decode_alloc_argument(const uint8_t *code, const TornadoAllocOperands *ops, int32_t argIndex, int32_t *out) {
    if (argIndex < 0 || argIndex >= ops->argCount) {
        return false;
    }
    *out = read_i32(code, ops->argsPosition + (argIndex * 4));
    return true;
}

/**
 * Decodes the launch operands from the bytecode array at `position`.
 */
int32_t tornado_decode_launch(const uint8_t *code, int32_t position, int32_t limit, TornadoLaunchOperands *out) {
    if (!opcode_is(code, position, limit, TornadoBytecode::LAUNCH)) {
        return TORNADO_DECODE_ERROR;
    }

    const int32_t operandBytes = tornado_operand_bytes(code, position, limit);
    if (operandBytes == TORNADO_DECODE_ERROR) {
        return TORNADO_DECODE_ERROR;
    }

    const int32_t operands = position + 1;
    out->callWrapperIndex = read_i32(code, operands);
    out->taskIndex = read_i32(code, operands + 4);
    out->numArgs = read_i32(code, operands + 8);
    out->eventId = read_i32(code, operands + 12);
    out->offset = read_i64(code, operands + 16);
    out->batchThreads = read_i64(code, operands + 24);
    out->argsPosition = operands + OPERANDS_LAUNCH_HEADER;
    return operands + operandBytes;
}

/**
 * Decodes a push argument from the bytecode array at `position`.
 */
int32_t tornado_decode_push_argument(const uint8_t *code, int32_t position, int32_t limit, TornadoPushArgument *out) {
    if (!fits(position, BYTES_PER_PUSH_ARGUMENT, limit)) {
        return TORNADO_DECODE_ERROR;
    }

    const TornadoBytecode op = static_cast<TornadoBytecode>(code[position]);
    if (op != TornadoBytecode::PUSH_CONSTANT_ARGUMENT && op != TornadoBytecode::PUSH_REFERENCE_ARGUMENT) {
        return TORNADO_DECODE_ERROR;
    }

    out->kind = code[position];
    out->index = read_i32(code, position + 1);
    return position + BYTES_PER_PUSH_ARGUMENT;
}
