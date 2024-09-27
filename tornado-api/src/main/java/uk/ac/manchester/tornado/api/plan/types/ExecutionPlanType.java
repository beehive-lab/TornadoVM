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
package uk.ac.manchester.tornado.api.plan.types;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

public sealed class ExecutionPlanType extends TornadoExecutionPlan //
        permits OffConcurrentDevices, OffMemoryLimit, OffPrintKernel, //
        OffProfiler, OffThreadInfo, WithWarmUp, WithBatch, WithClearProfiles,  //
        WithCompilerFlags, WithConcurrentDevices, WithDefaultScheduler, //
        WithDevicePlan, WithDynamicReconfiguration, WithFreeDeviceMemory, //
        WithGridScheduler, WithMemoryLimit, WithPrintKernel, WithProfiler, //
        WithResetDevice, WithThreadInfo {

    public ExecutionPlanType(TornadoExecutionPlan parentNode) {

        // Set link between the previous action (parent) and the new one
        this.parentLink = parentNode;
        parentLink.updateChildFromRoot(this);

        // Copy the reference for the executor
        this.tornadoExecutor = parentNode.getTornadoExecutor();

        // Copy the reference for the execution frame
        this.executionFrame = parentNode.getExecutionFrame();

        // Set child reference to this instance
        this.childLink = this;
    }

}
