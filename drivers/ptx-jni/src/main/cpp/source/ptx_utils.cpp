/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
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
