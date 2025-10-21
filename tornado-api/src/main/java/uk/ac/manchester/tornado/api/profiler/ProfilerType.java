/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.profiler;

/**
 * Profile-type that helps to classify all timers.
 */
public enum ProfilerType {

    // @formatter:off
    METHOD("Method"),
    IP("System-IP"),
    TOTAL_DISPATCH_KERNEL_TIME("Total-Kernel-Dispatch-Time"),
    TOTAL_DISPATCH_DATA_TRANSFERS_TIME("Total-Data-Transfer-Dispatch-Time"),
    COPY_IN_TIME("CopyIn-Time"),
    COPY_OUT_TIME("CopyOut-Time"),
    COPY_IN_TIME_SYNC("CopyIn-Time-Sync"),
    COPY_OUT_TIME_SYNC("CopyOut-Time-Sync"),
    COPY_IN_SIZE_BYTES_SYNC("CopyIn-Size-Sync-(Bytes)"),
    COPY_OUT_SIZE_BYTES_SYNC("CopyOut-Size-Sync-(Bytes)"),
    DEVICE_ID("Device-ID"),
    DEVICE("Device"),
    ALLOCATION_BYTES("Allocation-(Bytes)"),
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
    POWER_USAGE_mW("Task-Power-Usage-(mW)"),
    SYSTEM_POWER_CONSUMPTION_W("System-Power-Consumption-(W)"),
    SYSTEM_VOLTAGE_V("System-Voltage-(V)"),
    SYSTEM_CURRENT_A("System-Current-(A)"),
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
