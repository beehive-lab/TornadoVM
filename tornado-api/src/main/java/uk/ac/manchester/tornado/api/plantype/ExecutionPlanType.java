/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.plantype;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public sealed class ExecutionPlanType extends TornadoExecutionPlan //
        permits OffConcurrentDevices, OffMemoryLimit, OffPrintKernel, //
        OffProfiler, OffThreadInfo, WithWarmUp, WithBatch, WithClearProfiles,  //
        WithCompilerFlags, WithConcurrentDevices, WithDefaultScheduler, //
        WithDevicePlan, WithDynamicReconfiguration, WithFreeDeviceMemory, //
        WithGridScheduler, WithMemoryLimit, WithPrintKernel, WithProfiler, //
        WithResetDevice, WithThreadInfo {

    TornadoExecutionPlan parentLink;

    public ExecutionPlanType(TornadoExecutionPlan parent) {

        // Set link between the previous action (parent) and the new one
        this.parentLink = parent;

        // Copy the reference for the executor
        this.tornadoExecutor = parent.getTornadoExecutor();

        // Copy the reference for the execution frame
        this.executionFrame = parent.getExecutionFrame();

        // Copy the reference for the executor list
        this.executorList = parent.getExecutionList();

        // Set child reference to this instance
        this.child = this;
    }

}
