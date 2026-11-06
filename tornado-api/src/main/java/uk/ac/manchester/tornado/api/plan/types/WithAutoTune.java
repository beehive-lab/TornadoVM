/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.AutoTuneConfig;
import uk.ac.manchester.tornado.api.ExecutionPlanType;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;

/**
 * Execution-plan node created by {@code withAutoTune}; it records the {@link AutoTuneConfig} for the plan's trace output.
 */
public final class WithAutoTune extends ExecutionPlanType {

    private final AutoTuneConfig autoTuneConfiguration;

    public WithAutoTune(TornadoExecutionPlan parent, AutoTuneConfig config) {
        super(parent);
        this.autoTuneConfiguration = config;
    }

    public WithAutoTune(TornadoExecutionPlan parent) {
        super(parent);
        this.autoTuneConfiguration = new AutoTuneConfig();
    }

    @Override
    public String toString() {
        return parentLink.toString() + "\n -> withAutoTune(<" + autoTuneConfiguration + ">)";
    }
}
