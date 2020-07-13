/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 */

#define VERBOSE 0

#define CUDA_CHECK_ERROR(name,func) if(VERBOSE) {\
        printf("uk.ac.manchester.tornado.drivers.ptx> Calling: %s\n",name); \
    } \
    result = func; \
    if (result != CUDA_SUCCESS) { \
        printf("uk.ac.manchester.tornado.drivers.ptx> Returned: %s = %d\n", name, result); \
        fflush(stdout); \
    } \

#define RECORD_EVENT_BEGIN() \
    CUevent beforeEvent, afterEvent; \
    result = cuEventCreate(&beforeEvent, CU_EVENT_DEFAULT); \
    if (result != 0) { \
        printf("Failed to create event! (%d)\n", result); fflush(stdout); \
    } \
    result = cuEventCreate(&afterEvent, CU_EVENT_DEFAULT); \
    if (result != 0) { \
        printf("Failed to create event! (%d)\n", result); fflush(stdout); \
    } \
\
    result = cuEventRecord(beforeEvent, stream); \


#define RECORD_EVENT_END() \
    result = cuEventRecord(afterEvent, stream); \

