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
package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.plan.types.OffConcurrentDevices;
import uk.ac.manchester.tornado.api.plan.types.OffMemoryLimit;
import uk.ac.manchester.tornado.api.plan.types.OffPrintKernel;
import uk.ac.manchester.tornado.api.plan.types.OffProfiler;
import uk.ac.manchester.tornado.api.plan.types.OffThreadInfo;
import uk.ac.manchester.tornado.api.plan.types.WithAllGraphs;
import uk.ac.manchester.tornado.api.plan.types.WithBatch;
import uk.ac.manchester.tornado.api.plan.types.WithClearProfiles;
import uk.ac.manchester.tornado.api.plan.types.WithCompilerFlags;
import uk.ac.manchester.tornado.api.plan.types.WithConcurrentDevices;
import uk.ac.manchester.tornado.api.plan.types.WithDefaultScheduler;
import uk.ac.manchester.tornado.api.plan.types.WithDevice;
import uk.ac.manchester.tornado.api.plan.types.WithDynamicReconfiguration;
import uk.ac.manchester.tornado.api.plan.types.WithFreeDeviceMemory;
import uk.ac.manchester.tornado.api.plan.types.WithGraph;
import uk.ac.manchester.tornado.api.plan.types.WithGridScheduler;
import uk.ac.manchester.tornado.api.plan.types.WithMemoryLimit;
import uk.ac.manchester.tornado.api.plan.types.WithPreCompilation;
import uk.ac.manchester.tornado.api.plan.types.WithPrintKernel;
import uk.ac.manchester.tornado.api.plan.types.WithProfiler;
import uk.ac.manchester.tornado.api.plan.types.WithResetDevice;
import uk.ac.manchester.tornado.api.plan.types.WithThreadInfo;
import uk.ac.manchester.tornado.api.plan.types.WithWarmUpIterations;
import uk.ac.manchester.tornado.api.plan.types.WithWarmUpTime;

public abstract sealed class ExecutionPlanType extends TornadoExecutionPlan //
        permits OffConcurrentDevices, OffMemoryLimit, OffPrintKernel, OffProfiler, //
        OffThreadInfo, WithAllGraphs, WithPreCompilation, WithBatch, WithClearProfiles, WithCompilerFlags, //
        WithConcurrentDevices, WithDefaultScheduler, WithDevice, WithDynamicReconfiguration, //
        WithFreeDeviceMemory, WithGraph, WithGridScheduler, WithMemoryLimit, WithPrintKernel, WithProfiler, //
        WithResetDevice, WithThreadInfo, WithWarmUpIterations, WithWarmUpTime { //

    public ExecutionPlanType(TornadoExecutionPlan parentNode) {

        // Set link between the previous action (parent) and the new one
        this.parentLink = parentNode;

        // Propagate the root node of the current execution plan
        this.rootNode = parentNode.rootNode;

        // Set Link the root node to the leaf
        this.rootNode.childLink = this;

        // Copy the reference for the executor
        this.tornadoExecutor = parentNode.tornadoExecutor;

        // Copy the reference for the execution frame
        this.executionFrame = parentNode.executionFrame;

        // Set child reference to this instance
        this.childLink = this;
    }
}
