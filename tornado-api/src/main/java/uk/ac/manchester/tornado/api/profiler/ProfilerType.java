/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.api.profiler;

/**
 * Profile-type that helps to classify all timers
 */
public enum ProfilerType {

    // @formatter:off
    METHOD("Method"),
    IP("System-IP"),
    TOTAL_DISPATCH_KERNEL_TIME("Total-Kernel-Dispatch-Time"),
    TOTAL_DISPATCH_DATA_TRANSFERS_TIME("Total-Data-Transfer-Dispatch-Time"),
    COPY_IN_TIME("CopyIn-Time"),
    COPY_OUT_TIME("CopyOut-Time"),
    COPY_OUT_TIME_SYNC("CopyOut-Time-Sync"),
    COPY_OUT_SIZE_BYTES_SYNC("CopyOut-Size-Sync-(Bytes)"),
    DEVICE_ID("Device-ID"),
    DEVICE("Device"),
    TOTAL_COPY_IN_SIZE_BYTES("CopyIn-Size-(Bytes)"),
    TOTAL_COPY_OUT_SIZE_BYTES("CopyOut-Size-(Bytes)"),
    TASK_COMPILE_DRIVER_TIME("Task-Compile-Driver"),
    TASK_COMPILE_GRAAL_TIME("Task-Compile-Graal"),

    TASK_CODE_GENERATION_TIME("Task-Code-Generation"),
    TASK_KERNEL_TIME("Task-Kernel"),
    TOTAL_BYTE_CODE_GENERATION("Total-Bytecode-Gen"),
    TOTAL_DRIVER_COMPILE_TIME("Total-Driver-Compilation-Time"),
    TOTAL_GRAAL_COMPILE_TIME("Total-Graal-Compilation-Time"),

    TOTAL_CODE_GENERATION_TIME("Total-Task-Code-Generation-Time"),
    TOTAL_KERNEL_TIME("Kernel-Time"),
    TOTAL_TASK_GRAPH_TIME("TS-Total-Time"),

    BACKEND("Backend");
    // @formatter:on

    String description;

    ProfilerType(String description) {
        this.description = description;
    }

    public synchronized String getDescription() {
        return this.description;
    }

}
