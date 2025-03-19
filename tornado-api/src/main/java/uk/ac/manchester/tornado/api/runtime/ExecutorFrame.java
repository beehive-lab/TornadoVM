/*
 * Copyright (c) 2024, 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.runtime;

import uk.ac.manchester.tornado.api.DRMode;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;

/**
 * Class to store all objects and parameters related to the dispatch of an execution plan.
 */
public class ExecutorFrame {

    private final long executionPlanId;
    private DRMode dynamicReconfigurationMode;
    private Policy dynamicReconfigurationPolicy;
    private GridScheduler gridScheduler;
    private ProfilerMode profilerMode;

    public ExecutorFrame(long id) {
        this.executionPlanId = id;
    }

    public ExecutorFrame setPolicy(Policy policy) {
        this.dynamicReconfigurationPolicy = policy;
        return this;
    }

    public ExecutorFrame setMode(DRMode drMode) {
        this.dynamicReconfigurationMode = drMode;
        return this;
    }

    public ExecutorFrame setGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
        return this;
    }

    public Policy getDynamicReconfigurationPolicy() {
        return dynamicReconfigurationPolicy;
    }

    public DRMode getDRMode() {
        return dynamicReconfigurationMode;
    }

    public GridScheduler getGridScheduler() {
        return gridScheduler;
    }

    public long getExecutionPlanId() {
        return this.executionPlanId;
    }

    public void setProfilerMode(ProfilerMode profilerMode) {
        this.profilerMode = profilerMode;
    }

    public void setProfilerOff() {
        this.profilerMode = null;
    }

    public ProfilerMode getProfilerMode() {
        return profilerMode;
    }
}
