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

#ifndef TORNADO_STATE_H
#define TORNADO_STATE_H

#include <stdint.h>

/*
 * The bookkeeping the interpreter keeps alongside the bytecode stream: the event dependency
 * lists and the per-object batch counters. These are the parts of TornadoVMInterpreter that
 * touch no Java object and no device, so they can be expressed as plain integer arithmetic.
 *
 * Every function takes the arrays it works on as parameters. Nothing here allocates or owns
 * memory.
 *
 */

/*
 * Clears every wait-list row to -1 and zeroes every index.
 * Counterpart of TornadoVMInterpreter.initWaitEventList.
 */
void tornado_init_wait_event_list(int32_t *const *rows, int32_t rowCount, int32_t rowLength, int32_t *indexes);

/*
 * Rewinds one dependency list so the next tornado_add_dependency writes at its start.
 * Counterpart of TornadoVMInterpreter.resetEventIndexes.
 */
void tornado_reset_event_indexes(int32_t *indexes, int32_t indexCount, int32_t eventList);

/*
 * The wait-list to pass to a device operation, or null when there is nothing to wait on.
 * Counterpart of TornadoVMInterpreter.waitListFor.
 */
const int32_t *tornado_wait_list_for(const int32_t *const *rows, int32_t rowCount, int32_t eventId);

/*
 * Appends `lastEvent` to the dependency list `row` and advances its index.
 * Counterpart of the body of TornadoVMInterpreter.executeDependency.
 */
bool tornado_add_dependency(int32_t *row, int32_t rowLength, int32_t *indexes, int32_t eventId, int32_t lastEvent);

/*
 * Advances every object's chunk counter by one.
 * Counterpart of TornadoVMInterpreter.increaseBatchNumber.
 */
void tornado_increase_batch_number(int32_t *currentBatchNumbers, int32_t objectCount, int64_t sizeBatch);

/*
 * Rewinds every object's chunk counter, so a re-execution of a batched plan behaves like the
 * first. Counterpart of the replaceAll that opens TornadoVMInterpreter.execute.
 */
void tornado_reset_batch_numbers(int32_t *currentBatchNumbers, int32_t objectCount);

/*
 * Whether an object still has even chunks left to process, in which case its deallocation is
 * skipped. Counterpart of the batch guard in TornadoVMInterpreter.executeDeAlloc.
 */
bool tornado_is_batch_pending(const int32_t *currentBatchNumbers, const int32_t *totalEvenBatches, int32_t objectCount, int32_t objectIndex);

/*
 * Whether a kernel has to be recompiled because it writes the loop index into its output and
 * this is not the first chunk. Counterpart of TornadoVMInterpreter.currentBatchUsesThreadId.
 */
bool tornado_current_batch_uses_thread_id(int32_t currentBatch, bool indexInWrite);

/*
 * Maps a task's index within the whole task graph to its index within the tasks assigned to
 * this device. Counterpart of TornadoVMInterpreter.globalToLocalTaskIndex, which resolves the
 * same thing with a list scan on every use.
 */
int32_t tornado_global_to_local_task_index(const int32_t *taskIndexMap, int32_t mapLength, int32_t taskIndex);

#endif /* TORNADO_STATE_H */
