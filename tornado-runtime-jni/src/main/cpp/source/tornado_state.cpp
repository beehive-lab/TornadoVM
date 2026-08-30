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

#include "tornado_state.h"

/* The value that marks an unused entry of a dependency list. */
enum { TORNADO_NO_EVENT = -1 };

void tornado_init_wait_event_list(int32_t *const *rows, int32_t rowCount, int32_t rowLength, int32_t *indexes) {
    for (int32_t i = 0; i < rowCount; i++) {
        int32_t *row = rows[i];
        if (row == nullptr) {
            continue;
        }
        for (int32_t j = 0; j < rowLength; j++) {
            row[j] = TORNADO_NO_EVENT;
        }
    }

    for (int32_t i = 0; i < rowCount; i++) {
        indexes[i] = 0;
    }
}

void tornado_reset_event_indexes(int32_t *indexes, int32_t indexCount, int32_t eventList) {
    if (eventList < 0 || eventList >= indexCount) {
        return;
    }
    indexes[eventList] = 0;
}

const int32_t *tornado_wait_list_for(const int32_t *const *rows, int32_t rowCount, int32_t eventId) {
    if (eventId < 0 || eventId >= rowCount) {
        return nullptr;
    }
    return rows[eventId];
}

bool tornado_add_dependency(int32_t *row, int32_t rowLength, int32_t *indexes, int32_t eventId, int32_t lastEvent) {
    const int32_t next = indexes[eventId];
    if (next >= rowLength) {
        return false;
    }
    row[next] = lastEvent;
    indexes[eventId] = next + 1;
    return true;
}

void tornado_increase_batch_number(int32_t *currentBatchNumbers, int32_t objectCount, int64_t sizeBatch) {
    if (sizeBatch == 0) {
        return;
    }
    for (int32_t i = 0; i < objectCount; i++) {
        currentBatchNumbers[i]++;
    }
}

void tornado_reset_batch_numbers(int32_t *currentBatchNumbers, int32_t objectCount) {
    for (int32_t i = 0; i < objectCount; i++) {
        currentBatchNumbers[i] = 0;
    }
}

bool tornado_is_batch_pending(const int32_t *currentBatchNumbers, const int32_t *totalEvenBatches, int32_t objectCount, int32_t objectIndex) {
    if (objectCount == 0 || objectIndex < 0 || objectIndex >= objectCount) {
        return false;
    }
    return currentBatchNumbers[objectIndex] < totalEvenBatches[objectIndex];
}

bool tornado_current_batch_uses_thread_id(int32_t currentBatch, bool indexInWrite) {
    return currentBatch > 0 && indexInWrite;
}

int32_t tornado_global_to_local_task_index(const int32_t *taskIndexMap, int32_t mapLength, int32_t taskIndex) {
    if (taskIndex < 0 || taskIndex >= mapLength) {
        return 0;
    }
    const int32_t local = taskIndexMap[taskIndex];
    return local < 0 ? 0 : local;
}
