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

#include "tornado_interpreter.h"
#include "tornado_bytecodes.h"
#include "tornado_state.h"

#include <vector>
#include <cstring>

// Packs the same status and bytecode position decoded by NativeBytecodeInterpreter.java.
static inline int64_t pack(int32_t status, int32_t position) {
    return ((int64_t) status << 32) | ((int64_t) (uint32_t) position);
}

// Reads one FLAG_* value passed by TornadoVMInterpreter.advanceWithNativeInterpreter().
static inline bool flag_set(int32_t flags, int32_t bit) {
    return (flags & bit) != 0;
}

// Native counterpart of TornadoVMInterpreter.waitListForWrite().
static int32_t *row_for_write(const TornadoInterpreterContext *ctx, int32_t eventId) {
    if (eventId < 0 || eventId >= ctx->event_row_count || ctx->event_rows == nullptr) {
        return nullptr;
    }
    if (ctx->event_rows[eventId] != nullptr) {
        return ctx->event_rows[eventId];
    }
    if (ctx->ensure_event_row == nullptr) {
        return nullptr;
    }
    int32_t *row = ctx->ensure_event_row(ctx->ensure_event_row_user, eventId);
    ctx->event_rows[eventId] = row;
    return row;
}

// Updates the Java execute() loop's lastEvent value through its one-element table.
static void store_last_event(const TornadoInterpreterContext *ctx, int32_t event) {
    if (ctx->last_event != nullptr) {
        *ctx->last_event = event;
    }
}

// Reads the state flags published from XPUDeviceBufferState in Java.
static uint8_t object_flag(const TornadoInterpreterContext *ctx, int32_t index) {
    if (ctx->object_flags == nullptr || index < 0 || index >= ctx->object_count) {
        return 0;
    }
    return ctx->object_flags[index];
}

/*
 * KernelContext and AtomicInteger appear in the bytecode but have no per-object
 * device buffer. Atomics live in the shared region already bound as kernel argument 3.
 */
static bool is_non_buffer_object(const TornadoInterpreterContext *ctx, int32_t index) {
    if ((object_flag(ctx, index) & TORNADO_OBJ_KERNEL_CONTEXT) != 0) {
        return true;
    }
    return ctx->object_kinds != nullptr && index >= 0 && index < ctx->object_count && ctx->object_kinds[index] == TORNADO_OBJECT_KIND_ATOMIC;
}

// Reads one Java state-table entry and treats a missing entry as zero.
static int64_t table_long(const int64_t *table, int32_t count, int32_t index) {
    if (table == nullptr || index < 0 || index >= count) {
        return 0;
    }
    return table[index];
}

// Same element-width lookup used by the Java array wrappers.
static int64_t array_element_bytes(uint8_t kind) {
    switch (kind) {
        case TORNADO_OBJECT_KIND_BYTE_ARRAY: return 1;
        case TORNADO_OBJECT_KIND_CHAR_ARRAY:
        case TORNADO_OBJECT_KIND_SHORT_ARRAY: return 2;
        case TORNADO_OBJECT_KIND_INT_ARRAY:
        case TORNADO_OBJECT_KIND_FLOAT_ARRAY: return 4;
        case TORNADO_OBJECT_KIND_LONG_ARRAY:
        case TORNADO_OBJECT_KIND_DOUBLE_ARRAY: return 8;
        default: return 0;
    }
}

/*
 * Native counterpart of TornadoVMInterpreter.executeAlloc(): reuse a published
 * handle or allocate through the selected backend. Java only publishes layout.
 * Returns 0 to advance, or a packed status.
 */
static int64_t run_alloc(const TornadoInterpreterContext *ctx, const uint8_t *code, int32_t pc, const TornadoAllocOperands *ops) {
    // Constant-only kernels (TestHello.testHello) still emit ALLOC with no objects.
    // object_count is 0, so JNI leaves buffer_handles null. Java executeAlloc is a
    // no-op for that case; matching it here avoids a false STATUS_ERROR.
    if (ops->argCount == 0) {
        store_last_event(ctx, -1);
        return 0;
    }
    if (ctx->allocate_buffer == nullptr || ctx->release_buffer == nullptr || ctx->buffer_handles == nullptr) {
        return pack(TORNADO_STATUS_ERROR, pc);
    }
    std::vector<int32_t> allocatedIndexes;
    allocatedIndexes.reserve((size_t) ops->argCount);
    for (int32_t i = 0; i < ops->argCount; i++) {
        int32_t objectIndex = 0;
        if (!tornado_decode_alloc_argument(code, ops, i, &objectIndex)) {
            return pack(TORNADO_STATUS_BAIL, pc);
        }
        if (objectIndex < 0 || objectIndex >= ctx->object_count) {
            return pack(TORNADO_STATUS_ERROR, pc);
        }
        if (is_non_buffer_object(ctx, objectIndex)) {
            continue;
        }
        const uint8_t kind = ctx->object_kinds == nullptr ? TORNADO_OBJECT_KIND_UNSUPPORTED : ctx->object_kinds[objectIndex];
        if (kind == TORNADO_OBJECT_KIND_UNSUPPORTED) {
            return pack(TORNADO_STATUS_BAIL, pc);
        }
        if (table_long(ctx->buffer_sizes, ctx->object_count, objectIndex) <= 0) {
            return pack(TORNADO_STATUS_ERROR, pc);
        }
    }
    for (int32_t i = 0; i < ops->argCount; i++) {
        int32_t objectIndex = 0;
        if (!tornado_decode_alloc_argument(code, ops, i, &objectIndex)) {
            return pack(TORNADO_STATUS_BAIL, pc);
        }
        if (objectIndex < 0 || objectIndex >= ctx->object_count) {
            return pack(TORNADO_STATUS_ERROR, pc);
        }
        if (is_non_buffer_object(ctx, objectIndex)) {
            continue;
        }
        int64_t &handle = ctx->buffer_handles[objectIndex];
        const uint8_t flags = object_flag(ctx, objectIndex);
        if (handle > 0) {
            if ((flags & TORNADO_OBJ_NATIVE_ALLOCATION_PREPARED) != 0) {
                const int64_t bytes = table_long(ctx->buffer_sizes, ctx->object_count, objectIndex);
                const int32_t access = ctx->object_accesses == nullptr ? 0 : ctx->object_accesses[objectIndex];
                const int prepareStatus = ctx->prepare_reused_buffer == nullptr ? TORNADO_COPY_UNSUPPORTED : ctx->prepare_reused_buffer(ctx->backend_user, handle, bytes, access);
                if (prepareStatus != TORNADO_COPY_OK) {
                    return pack(TORNADO_STATUS_ERROR, pc);
                }
                ctx->object_flags[objectIndex] = (uint8_t) ((flags & ~TORNADO_OBJ_NATIVE_ALLOCATION_PREPARED) | TORNADO_OBJ_NATIVE_ALLOCATED);
                continue;
            }
            if (ops->sizeBatch > 0) {
                continue;
            }
            if ((flags & (TORNADO_OBJ_LOCKED | TORNADO_OBJ_PERSISTENT)) == 0) {
                return pack(TORNADO_STATUS_ERROR, pc);
            }
            continue;
        }
        const int64_t bytes = table_long(ctx->buffer_sizes, ctx->object_count, objectIndex);
        const int32_t access = ctx->object_accesses == nullptr ? 0 : ctx->object_accesses[objectIndex];
        const int allocationStatus = bytes <= 0 ? TORNADO_COPY_FAILED : ctx->allocate_buffer(ctx->backend_user, bytes, access, &handle);
        if (allocationStatus != TORNADO_COPY_OK) {
            for (int32_t rollback : allocatedIndexes) {
                ctx->release_buffer(ctx->backend_user, ctx->buffer_handles[rollback]);
                ctx->buffer_handles[rollback] = 0;
                ctx->object_flags[rollback] &= (uint8_t) ~TORNADO_OBJ_NATIVE_ALLOCATED;
            }
            return pack(TORNADO_STATUS_ERROR, pc);
        }
        allocatedIndexes.push_back(objectIndex);
        ctx->object_flags[objectIndex] = (uint8_t) ((flags & ~TORNADO_OBJ_NATIVE_ALLOCATION_PREPARED) | TORNADO_OBJ_NATIVE_ALLOCATED);
    }
    store_last_event(ctx, -1);
    return 0;
}

/*
 * Native counterpart of the four Java transfer handlers: copy between the
 * published host address and device handle.
 */
static int64_t run_transfer(const TornadoInterpreterContext *ctx, int32_t pc, const TornadoTransferOperands *operand, bool toDevice, bool once, bool blocking) {
    if (operand->objectIndex < 0 || operand->objectIndex >= ctx->object_count) {
        return pack(TORNADO_STATUS_ERROR, pc);
    }
    const int32_t flags = ctx->flags;
    // Native CUDA allocation preparation intentionally does not register host memory
    // through Java. Keep CUDA copies synchronous so pageable MemorySegments and pinned
    // primitive-array views remain valid for the complete driver operation.
    const uint8_t objFlags = object_flag(ctx, operand->objectIndex);
    const bool forceBlocking = blocking || ctx->backend == TORNADO_BACKEND_CUDA || (objFlags & TORNADO_OBJ_STAGED_HOST_BUFFER) != 0;
    if (is_non_buffer_object(ctx, operand->objectIndex)) {
        if (!blocking) {
            store_last_event(ctx, toDevice ? -1 : 0);
        }
        return 0;
    }
    // FIRST_EXECUTION means once for a resident whole-object buffer. In batch mode
    // the same device buffer is reused for different host chunks, so every chunk
    // must be uploaded. This matches transferHostToDeviceOnce() in Java.
    if (once && operand->sizeBatch == 0 && (objFlags & TORNADO_OBJ_HAS_CONTENT) != 0) {
        tornado_reset_event_indexes(ctx->event_indexes, ctx->event_row_count, operand->eventId);
        store_last_event(ctx, -1);
        return 0;
    }

    const int64_t handle = table_long(ctx->buffer_handles, ctx->object_count, operand->objectIndex);
    int64_t host = table_long(ctx->host_pointers, ctx->object_count, operand->objectIndex);
    if (host == 0 && ctx->resolve_host_pointer != nullptr) {
        host = ctx->resolve_host_pointer(ctx->resolve_host_pointer_user, operand->objectIndex);
    }
    const int64_t allocationBytes = table_long(ctx->buffer_sizes, ctx->object_count, operand->objectIndex);
    const int64_t bufferOffset = table_long(ctx->buffer_offsets, ctx->object_count, operand->objectIndex);
    const int64_t dataOffset = table_long(ctx->data_offsets, ctx->object_count, operand->objectIndex);
    int64_t dataBytes = allocationBytes - dataOffset;
    const int64_t partialBytes = table_long(ctx->partial_copy_sizes, ctx->object_count, operand->objectIndex);
    const uint8_t kind = ctx->object_kinds == nullptr ? 0 : ctx->object_kinds[operand->objectIndex];
    if (kind == TORNADO_OBJECT_KIND_UNSUPPORTED) return pack(TORNADO_STATUS_BAIL, pc);
    if (handle == 0 || host == 0 || allocationBytes <= 0 || dataBytes < 0 || ctx->copy_buffer == nullptr) return pack(TORNADO_STATUS_ERROR, pc);

    int status = TORNADO_COPY_OK;
    if (!toDevice && partialBytes > 0 && kind == TORNADO_OBJECT_KIND_SEGMENT) {
        status = ctx->copy_buffer(ctx->backend_user, false, true, handle, operand->offset, partialBytes, host, operand->offset);
    } else if (kind == TORNADO_OBJECT_KIND_SEGMENT && operand->sizeBatch == 0) {
        status = ctx->copy_buffer(ctx->backend_user, toDevice, forceBlocking, handle, bufferOffset, allocationBytes, host, operand->offset);
    } else {
        if (operand->sizeBatch > 0) dataBytes = operand->sizeBatch;
        if (toDevice && dataOffset > 0) {
            if (kind == TORNADO_OBJECT_KIND_SEGMENT) {
                status = ctx->copy_buffer(ctx->backend_user, true, forceBlocking, handle, bufferOffset, dataOffset, host, 0);
            } else {
                std::vector<uint8_t> header((size_t) dataOffset, 0);
                const int64_t elementBytes = array_element_bytes(kind);
                if (dataOffset < (int64_t) sizeof(int32_t) || elementBytes == 0 || dataBytes % elementBytes != 0) return pack(TORNADO_STATUS_ERROR, pc);
                const int32_t arrayLength = operand->sizeBatch > 0 ? (int32_t) operand->sizeBatch : (int32_t) (dataBytes / elementBytes);
                memcpy(header.data() + dataOffset - sizeof(arrayLength), &arrayLength, sizeof(arrayLength));
                status = ctx->copy_buffer(ctx->backend_user, true, true, handle, bufferOffset, dataOffset, (int64_t) (uintptr_t) header.data(), 0);
            }
        }
        if (status == TORNADO_COPY_OK) {
            const int64_t hostDataOffset = operand->offset + ((kind == TORNADO_OBJECT_KIND_SEGMENT) ? dataOffset : 0);
            const bool pinMustStayValid = kind != TORNADO_OBJECT_KIND_SEGMENT;
            status = ctx->copy_buffer(ctx->backend_user, toDevice, forceBlocking || pinMustStayValid, handle, bufferOffset + dataOffset, dataBytes, host, hostDataOffset);
        }
    }
    if (status != TORNADO_COPY_OK) {
        return pack(TORNADO_STATUS_ERROR, pc);
    }

    if (toDevice && ctx->object_flags != nullptr && operand->objectIndex >= 0 && operand->objectIndex < ctx->object_count) {
        ctx->object_flags[operand->objectIndex] = (uint8_t) (objFlags | TORNADO_OBJ_HAS_CONTENT);
    }
    tornado_reset_event_indexes(ctx->event_indexes, ctx->event_row_count, operand->eventId);
    if (!blocking) {
        store_last_event(ctx, -1);
    }
    return 0;
}

static int64_t constant_bytes(const TornadoConstant &constant) {
    switch (constant.tag) {
        case TORNADO_CONSTANT_BYTE: return 1;
        case TORNADO_CONSTANT_CHAR:
        case TORNADO_CONSTANT_SHORT:
        case TORNADO_CONSTANT_HALF: return 2;
        case TORNADO_CONSTANT_INT:
        case TORNADO_CONSTANT_FLOAT: return 4;
        case TORNADO_CONSTANT_LONG:
        case TORNADO_CONSTANT_DOUBLE: return 8;
        default: return 0;
    }
}

/* Native counterpart of executeLaunch for the dependency-free, non-batched MVP path. */
static int64_t run_launch(const TornadoInterpreterContext *ctx, const uint8_t *code, int32_t pc, const TornadoLaunchOperands *operand) {
    if (ctx->kernel_handles == nullptr || ctx->launch_metadata == nullptr || ctx->set_kernel_argument == nullptr || ctx->launch_kernel == nullptr
            || operand->taskIndex < 0 || operand->taskIndex >= ctx->kernel_count) {
        return pack(TORNADO_STATUS_BAIL, pc);
    }

    const int64_t kernel = ctx->kernel_handles[operand->taskIndex];
    const int64_t *meta = ctx->launch_metadata + ((int64_t) operand->taskIndex * TORNADO_LAUNCH_META_STRIDE);
    const int64_t launchFlags = meta[TORNADO_LAUNCH_META_FLAGS];
    if (kernel == 0 || (launchFlags & TORNADO_LAUNCH_SUPPORTED) == 0) {
        return pack(TORNADO_STATUS_BAIL, pc);
    }

    const int64_t frameBuffer = meta[TORNADO_LAUNCH_META_FRAME_BUFFER];
    const int64_t constantBuffer = meta[TORNADO_LAUNCH_META_CONSTANT_BUFFER];
    const int64_t atomicBuffer = meta[TORNADO_LAUNCH_META_ATOMIC_BUFFER];
    if (frameBuffer == 0 || constantBuffer == 0 || atomicBuffer == 0 || ctx->copy_buffer == nullptr) {
        return pack(TORNADO_STATUS_BAIL, pc);
    }

    // The three values live in the pinned launch table for the whole interpreter call,
    // so OpenCL, CUDA and Metal may enqueue this write without retaining a temporary stack pointer.
    const int64_t *kernelContext = meta + TORNADO_LAUNCH_META_CONTEXT;
    if (ctx->copy_buffer(ctx->backend_user, true, false, frameBuffer, 0, 3 * (int64_t) sizeof(int64_t), (int64_t) (uintptr_t) kernelContext, 0) != TORNADO_COPY_OK) {
        return pack(TORNADO_STATUS_ERROR, pc);
    }

    int32_t nativeArgument = 0;
    const int64_t specialReferences[] = { frameBuffer, constantBuffer };
    for (const int64_t &reference : specialReferences) {
        if (ctx->set_kernel_argument(ctx->backend_user, kernel, nativeArgument++, TORNADO_KERNEL_ARG_REFERENCE, &reference, sizeof(reference)) != TORNADO_COPY_OK) {
            return pack(TORNADO_STATUS_ERROR, pc);
        }
    }
    const int64_t localBytes = meta[TORNADO_LAUNCH_META_LOCAL_MEMORY] > 0 ? meta[TORNADO_LAUNCH_META_LOCAL_MEMORY] : (int64_t) sizeof(int64_t);
    if (ctx->set_kernel_argument(ctx->backend_user, kernel, nativeArgument++, TORNADO_KERNEL_ARG_LOCAL, nullptr, localBytes) != TORNADO_COPY_OK
            || ctx->set_kernel_argument(ctx->backend_user, kernel, nativeArgument++, TORNADO_KERNEL_ARG_REFERENCE, &atomicBuffer, sizeof(atomicBuffer)) != TORNADO_COPY_OK) {
        return pack(TORNADO_STATUS_ERROR, pc);
    }

    int32_t argumentPosition = operand->argsPosition;
    for (int32_t i = 0; i < operand->numArgs; i++) {
        TornadoPushArgument argument;
        argumentPosition = tornado_decode_push_argument(code, argumentPosition, ctx->limit, &argument);
        if (argumentPosition == TORNADO_DECODE_ERROR) {
            return pack(TORNADO_STATUS_BAIL, pc);
        }
        if (argument.kind == static_cast<uint8_t>(TornadoBytecode::PUSH_REFERENCE_ARGUMENT)) {
            if (argument.index < 0 || argument.index >= ctx->object_count) return pack(TORNADO_STATUS_ERROR, pc);
            if (is_non_buffer_object(ctx, argument.index)) continue;
            const int64_t reference = table_long(ctx->buffer_handles, ctx->object_count, argument.index);
            if (reference == 0 || ctx->set_kernel_argument(ctx->backend_user, kernel, nativeArgument++, TORNADO_KERNEL_ARG_REFERENCE, &reference, sizeof(reference)) != TORNADO_COPY_OK) {
                return pack(TORNADO_STATUS_ERROR, pc);
            }
        } else {
            TornadoConstant constant;
            if (!tornado_constant_at(ctx->constants, ctx->constants_bytes, argument.index, &constant)) return pack(TORNADO_STATUS_BAIL, pc);
            const int64_t bytes = constant_bytes(constant);
            if (bytes == 0 || ctx->set_kernel_argument(ctx->backend_user, kernel, nativeArgument++, TORNADO_KERNEL_ARG_VALUE, &constant.bits, bytes) != TORNADO_COPY_OK) {
                return pack(TORNADO_STATUS_ERROR, pc);
            }
        }
    }

    const int32_t dimensions = (int32_t) meta[TORNADO_LAUNCH_META_DIMENSIONS];
    const int64_t *globalOffset = meta + TORNADO_LAUNCH_META_GLOBAL_OFFSET;
    const int64_t *globalWork = meta + TORNADO_LAUNCH_META_GLOBAL_WORK;
    const int64_t *localWork = (launchFlags & TORNADO_LAUNCH_HAS_LOCAL_WORK) == 0 ? nullptr : meta + TORNADO_LAUNCH_META_LOCAL_WORK;
    if (dimensions < 1 || dimensions > 3 || ctx->launch_kernel(ctx->backend_user, kernel, dimensions, globalOffset, globalWork, localWork) != TORNADO_COPY_OK) {
        return pack(TORNADO_STATUS_ERROR, pc);
    }

    tornado_reset_event_indexes(ctx->event_indexes, ctx->event_row_count, operand->eventId);
    store_last_event(ctx, -1);
    return 0;
}

// Native counterpart of the while loop in TornadoVMInterpreter.execute().
int64_t tornado_interpret(const TornadoInterpreterContext *ctx) {
    if (ctx == nullptr || ctx->code == nullptr) {
        return pack(TORNADO_STATUS_EOF, 0);
    }

    const uint8_t *code = ctx->code;
    const int32_t limit = ctx->limit;
    const int32_t flags = ctx->flags;
    const bool warmup = flag_set(flags, TORNADO_FLAG_WARMUP);
    int32_t pc = ctx->position;

    while (pc < limit) {
        switch (static_cast<TornadoBytecode>(code[pc])) {
            case TornadoBytecode::END:
                if (!warmup && flag_set(flags, TORNADO_FLAG_USE_VM_FLUSH) && (ctx->flush_backend == nullptr || ctx->flush_backend(ctx->backend_user) != TORNADO_COPY_OK)) {
                    return pack(TORNADO_STATUS_ERROR, pc);
                }
                return pack(TORNADO_STATUS_END, pc + 1);

            /*
             * Not in the Java execute() loop: the constructor already consumed INIT,
             * CONTEXT (ensureLoaded) and BEGIN. Skip so a walk from byte 0 is defined.
             */
            case TornadoBytecode::INIT:
            case TornadoBytecode::BEGIN:
            case TornadoBytecode::CONTEXT: {
                const int32_t next = tornado_next_bytecode(code, pc, limit);
                if (next == TORNADO_DECODE_ERROR) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                pc = next;
                break;
            }

            case TornadoBytecode::ADD_DEPENDENCY: {
                TornadoIndexOperand operand;
                const int32_t next = tornado_decode_index(code, pc, limit, &operand);
                if (next == TORNADO_DECODE_ERROR) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (!warmup && flag_set(flags, TORNADO_FLAG_USE_DEPENDENCIES) && ctx->last_event != nullptr && *ctx->last_event != -1) {
                    int32_t *row = row_for_write(ctx, operand.index);
                    if (row == nullptr || ctx->event_indexes == nullptr) {
                        return pack(TORNADO_STATUS_ERROR, pc);
                    }
                    if (!tornado_add_dependency(row, ctx->event_row_length, ctx->event_indexes, operand.index, *ctx->last_event)) {
                        return pack(TORNADO_STATUS_ERROR, pc);
                    }
                }
                pc = next;
                break;
            }

            case TornadoBytecode::ON_DEVICE:
            case TornadoBytecode::PERSIST: {
                TornadoObjectEventOperands operand;
                const int32_t next = tornado_decode_object_event(code, pc, limit, &operand);
                if (next == TORNADO_DECODE_ERROR) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (!warmup) {
                    tornado_reset_event_indexes(ctx->event_indexes, ctx->event_row_count, operand.eventId);
                    if (ctx->last_event != nullptr) {
                        *ctx->last_event = -1;
                    }
                }
                pc = next;
                break;
            }

            case TornadoBytecode::ALLOC: {
                TornadoAllocOperands operand;
                const int32_t next = tornado_decode_alloc(code, pc, limit, &operand);
                if (next == TORNADO_DECODE_ERROR) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (flag_set(flags, TORNADO_FLAG_BATCHED_EXECUTION)) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (!warmup && !flag_set(flags, TORNADO_FLAG_GRAPH_INSTANTIATED)) {
                    const int64_t failed = run_alloc(ctx, code, pc, &operand);
                    if (failed != 0) {
                        return failed;
                    }
                }
                pc = next;
                break;
            }

            case TornadoBytecode::DEALLOC: {
                TornadoIndexOperand operand;
                const int32_t next = tornado_decode_index(code, pc, limit, &operand);
                if (next == TORNADO_DECODE_ERROR) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (flag_set(flags, TORNADO_FLAG_BATCHED_EXECUTION)) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (!warmup && !flag_set(flags, TORNADO_FLAG_GRAPH_INSTANTIATED)) {
                    if (is_non_buffer_object(ctx, operand.index)) {
                        store_last_event(ctx, -1);
                    } else {
                        const uint8_t flags = object_flag(ctx, operand.index);
                        if ((flags & TORNADO_OBJ_LOCKED) == 0) {
                            if (flag_set(ctx->flags, TORNADO_FLAG_FORCE_DEALLOCATION)) {
                                return pack(TORNADO_STATUS_BAIL, pc);
                            }
                            const int64_t handle = table_long(ctx->buffer_handles, ctx->object_count, operand.index);
                            if (handle <= 0) {
                                return pack(TORNADO_STATUS_ERROR, pc);
                            }
                            ctx->buffer_handles[operand.index] = 0;
                            ctx->object_flags[operand.index] = (uint8_t) ((flags & ~TORNADO_OBJ_HAS_CONTENT) | TORNADO_OBJ_NATIVE_DEALLOCATED);
                        }
                        store_last_event(ctx, -1);
                    }
                }
                pc = next;
                break;
            }

            case TornadoBytecode::TRANSFER_HOST_TO_DEVICE_ONCE:
            case TornadoBytecode::TRANSFER_HOST_TO_DEVICE_ALWAYS:
            case TornadoBytecode::TRANSFER_DEVICE_TO_HOST_ALWAYS:
            case TornadoBytecode::TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING: {
                TornadoTransferOperands operand;
                const int32_t next = tornado_decode_transfer(code, pc, limit, &operand);
                if (next == TORNADO_DECODE_ERROR) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (flag_set(flags, TORNADO_FLAG_BATCHED_EXECUTION) || flag_set(flags, TORNADO_FLAG_USE_DEPENDENCIES)) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (!warmup) {
                    const TornadoBytecode op = static_cast<TornadoBytecode>(code[pc]);
                    const bool toDevice = op == TornadoBytecode::TRANSFER_HOST_TO_DEVICE_ONCE || op == TornadoBytecode::TRANSFER_HOST_TO_DEVICE_ALWAYS;
                    const bool once = op == TornadoBytecode::TRANSFER_HOST_TO_DEVICE_ONCE;
                    // Both Java D2H handlers call streamOutBlocking().
                    const bool blocking = !toDevice;
                    const int64_t failed = run_transfer(ctx, pc, &operand, toDevice, once, blocking);
                    if (failed != 0) {
                        return failed;
                    }
                }
                pc = next;
                break;
            }

            case TornadoBytecode::LAUNCH: {
                TornadoLaunchOperands operand;
                const int32_t next = tornado_decode_launch(code, pc, limit, &operand);
                if (next == TORNADO_DECODE_ERROR) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (flag_set(flags, TORNADO_FLAG_BATCHED_EXECUTION) || flag_set(flags, TORNADO_FLAG_USE_DEPENDENCIES)) {
                    return pack(TORNADO_STATUS_BAIL, pc);
                }
                if (!warmup) {
                    const int64_t failed = run_launch(ctx, code, pc, &operand);
                    if (failed != 0) return failed;
                }
                pc = next;
                break;
            }

            default:
                /*
                 * Not implemented here. Leave the cursor on the opcode so that the Java
                 * interpreter decodes and executes it exactly as it would have on its own.
                 */
                return pack(TORNADO_STATUS_BAIL, pc);
        }
    }

    return pack(TORNADO_STATUS_EOF, pc);
}
