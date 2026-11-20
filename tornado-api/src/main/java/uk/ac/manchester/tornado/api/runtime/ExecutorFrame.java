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

import uk.ac.manchester.tornado.api.AutoTuneConfig;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;

/**
 * Class to store all objects and parameters related to the dispatch of an execution plan.
 *  It also holds the auto-tuning configuration when auto-tuning is enabled.
 */
public class ExecutorFrame {

    private final long executionPlanId;
    private GridScheduler gridScheduler;
    private ProfilerMode profilerMode;
    private AutoTuneConfig autoTuneConfig;

    public ExecutorFrame(long id) {
        this.executionPlanId = id;
    }


    public ExecutorFrame setGridScheduler(GridScheduler gridScheduler) {
        this.gridScheduler = gridScheduler;
        return this;
    }


    public GridScheduler getGridScheduler() {
        return gridScheduler;
    }

    /** Sets the auto-tuning configuration. {@code null} disables auto-tuning. */
    public void setAutoTuneConfig(AutoTuneConfig autoTuneConfig) {
            this.autoTuneConfig = autoTuneConfig;
    }

    /** Returns the auto-tuning configuration, or {@code null} if auto-tuning is disabled. */
    public AutoTuneConfig getAutoTuneConfig() {
        return autoTuneConfig;
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
