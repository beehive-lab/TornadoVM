/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
#include <cuda.h>
#include <iostream>
#include "ptx_utils.h"
#include "ptx_log.h"

CUresult record_events_create(CUevent* beforeEvent, CUevent* afterEvent) {
    CUresult result = cuEventCreate(beforeEvent, CU_EVENT_DEFAULT);
    LOG_PTX_AND_VALIDATE("cuEventCreate (beforeEvent)", result);
    result = cuEventCreate(afterEvent, CU_EVENT_DEFAULT);
    LOG_PTX_AND_VALIDATE("cuEventCreate (afterEvent)", result);
    return result;
}

CUresult record_event(CUevent* event, CUstream* stream) {
    CUresult result = cuEventRecord(*event, *stream);
    LOG_PTX_AND_VALIDATE("cuEventRecord", result);
    return result;
}
