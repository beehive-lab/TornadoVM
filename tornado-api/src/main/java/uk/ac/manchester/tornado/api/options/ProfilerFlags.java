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
package uk.ac.manchester.tornado.api.options;

/**
 * Options of the TornadoVM profiler and the Chrome event tracer.
 */
public final class ProfilerFlags {

    private static final OptionCategory CATEGORY = OptionCategory.PROFILER;

    public static final Option<Boolean> PROFILER = Option.bool("tornado.profiler") //
            .defaultValue(false) //
            .description("Enable the TornadoVM profiler and print its report on standard output after each execution.") //
            .category(CATEGORY).dynamic().build();

    public static final Option<Boolean> PROFILER_LOG = Option.bool("tornado.log.profiler") //
            .defaultValue(false) //
            .description("Keep the profiler report instead of printing it, so it can be read with the execution result or "
                    + "written to `tornado.profiler.dump.dir`.") //
            .category(CATEGORY).dynamic().build();

    public static final Option<String> PROFILER_DUMP_DIR = Option.string("tornado.profiler.dump.dir") //
            .defaultValue("") //
            .description("File the profiler report is written to, as JSON.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> TIME_IN_NANOSECONDS = Option.bool("tornado.ns.time") //
            .defaultValue(true) //
            .description("Report profiler times in nanoseconds; `false` reports them in milliseconds.") //
            .category(CATEGORY).build();

    public static final Option<Boolean> CHROME_EVENT_TRACER = Option.bool("tornado.chrome.event.tracer.enabled") //
            .defaultValue(false) //
            .description("Record runtime events in the Chrome trace format (open in `chrome://tracing` or Perfetto).") //
            .category(CATEGORY).dynamic().build();

    public static final Option<String> CHROME_EVENT_TRACER_FILE = Option.string("tornado.chrome.event.tracer.filename") //
            .defaultValue("chrome.json") //
            .description("File the Chrome trace is written to.") //
            .category(CATEGORY).dynamic().build();

    private ProfilerFlags() {
    }
}
