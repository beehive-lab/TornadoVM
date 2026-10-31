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
package uk.ac.manchester.tornado.runtime.common;

import uk.ac.manchester.tornado.api.options.ProfilerFlags;

/**
 * Whether the TornadoVM profiler is on: switched on for every execution by {@code -Dtornado.profiler} (and
 * {@code -Dtornado.log.profiler}), or for the executions of a plan by
 * {@link uk.ac.manchester.tornado.api.TornadoExecutionPlan#withProfiler}.
 */
public final class ProfilerActivation {

    private static volatile boolean enabledByPlan;
    private static volatile boolean keepReportByPlan;

    private ProfilerActivation() {
    }

    /**
     * Switches the profiler on for the plan about to run.
     *
     * @param keepReport
     *     keep the report instead of printing it, as {@code tornado.log.profiler} does
     */
    public static void enableForPlan(boolean keepReport) {
        enabledByPlan = true;
        if (keepReport) {
            keepReportByPlan = true;
        }
    }

    public static void disableForPlan() {
        enabledByPlan = false;
        keepReportByPlan = false;
    }

    /**
     * @return whether a plan switched the profiler on, regardless of {@code -Dtornado.profiler}
     */
    public static boolean isEnabledByPlan() {
        return enabledByPlan;
    }

    public static boolean isEnabled() {
        return enabledByPlan || ProfilerFlags.PROFILER.get();
    }

    /**
     * @return whether the profiler report is kept instead of printed
     */
    public static boolean keepsReport() {
        return keepReportByPlan || ProfilerFlags.PROFILER_LOG.get();
    }
}
