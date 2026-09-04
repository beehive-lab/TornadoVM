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

/* Stateless event-list helpers used by the native interpreter. */

/*
 * Rewinds one dependency list so the next tornado_add_dependency writes at its start.
 * Counterpart of TornadoVMInterpreter.resetEventIndexes.
 */
void tornado_reset_event_indexes(int32_t *indexes, int32_t indexCount, int32_t eventList);

/*
 * Appends `lastEvent` to the dependency list `row` and advances its index.
 * Counterpart of the body of TornadoVMInterpreter.executeDependency.
 */
bool tornado_add_dependency(int32_t *row, int32_t rowLength, int32_t *indexes, int32_t eventId, int32_t lastEvent);

#endif /* TORNADO_STATE_H */
