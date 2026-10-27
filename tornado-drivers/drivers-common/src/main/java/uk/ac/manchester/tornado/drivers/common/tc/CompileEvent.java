/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.common.tc;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * A JFR event for one compilation of one kernel: TC's ({@code tc}) or the driver's ({@code nvrtc},
 * {@code opencl}). {@code background} marks the compilations a plan starts ahead of its first run
 * ({@code -Dtornado.parallel.compile}). Recorded only while JFR records {@code tornado.Compile}.
 */
@Name("tornado.Compile")
@Label("TornadoVM Compile")
@Category({ "TornadoVM", "Compilation" })
@Description("One kernel compiled by TC or by the device driver")
@StackTrace(false)
public final class CompileEvent extends Event {

    @Label("Phase")
    public String phase;

    @Label("Kernel")
    public String kernel;

    @Label("Task")
    public String task;

    @Label("Background")
    public boolean background;

    public static CompileEvent begin(String phase, String kernel, String task, boolean background) {
        var event = new CompileEvent();
        if (event.isEnabled()) {
            event.phase = phase;
            event.kernel = kernel;
            event.task = task;
            event.background = background;
            event.begin();
        }
        return event;
    }

    public void finish() {
        if (isEnabled()) {
            commit();
        }
    }
}
