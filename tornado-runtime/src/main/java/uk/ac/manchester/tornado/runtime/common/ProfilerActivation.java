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
 * Whether the TornadoVM profiler is on: switched on by {@code -Dtornado.profiler} (and {@code -Dtornado.log.profiler}),
 * or by {@link uk.ac.manchester.tornado.api.TornadoExecutionPlan#withProfiler} through the API.
 *
 * <p>
 * The switch is process-wide, not per plan: once a plan turns the profiler on with {@code withProfiler}, every plan
 * is profiled until one turns it off ({@code withoutProfiler}, or a device reset). The drivers read it when they
 * launch a kernel or copy data, without knowing the plan; making it per plan means passing it through them.
 */
public final class ProfilerActivation {

    private static volatile boolean enabledFromApi;
    private static volatile boolean keepReportFromApi;

    private ProfilerActivation() {
    }

    /**
     * Switches the profiler on, for every plan, as {@code withProfiler} asks.
     *
     * @param keepReport
     *     keep the report instead of printing it, as {@code tornado.log.profiler} does
     */
    public static void enableFromApi(boolean keepReport) {
        enabledFromApi = true;
        if (keepReport) {
            keepReportFromApi = true;
        }
    }

    public static void disableFromApi() {
        enabledFromApi = false;
        keepReportFromApi = false;
    }

    /**
     * @return whether the API switched the profiler on, regardless of {@code -Dtornado.profiler}
     */
    public static boolean isEnabledFromApi() {
        return enabledFromApi;
    }

    public static boolean isEnabled() {
        return enabledFromApi || ProfilerFlags.PROFILER.get();
    }

    /**
     * @return whether the profiler report is kept instead of printed
     */
    public static boolean keepsReport() {
        return keepReportFromApi || ProfilerFlags.PROFILER_LOG.get();
    }
}
