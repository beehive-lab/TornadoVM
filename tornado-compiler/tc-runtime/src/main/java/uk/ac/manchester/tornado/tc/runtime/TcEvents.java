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
package uk.ac.manchester.tornado.tc.runtime;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR events of the TC pipeline. Always compiled in, cheap when disabled; record with
 * {@code -XX:StartFlightRecording} and inspect with {@code jfr print --events tc.*} or JMC.
 */
final class TcEvents {

    private TcEvents() {
    }

    @Name("tc.Compilation")
    @Label("TC Compilation")
    @Category({ "TornadoVM", "TC" })
    @Description("One kernel compiled by TC, from TC-SSA to target source")
    @StackTrace(false)
    static final class Compilation extends Event {
        @Label("Function")
        String function;

        @Label("Target")
        String target;

        @Label("Succeeded")
        boolean succeeded;

        @Label("Source lines")
        int sourceLines;

        @Label("Diagnostic")
        String diagnostic;
    }

    @Name("tc.Stage")
    @Label("TC Stage")
    @Category({ "TornadoVM", "TC" })
    @Description("One stage of a TC compilation (verification, a lowering or the emitter)")
    @StackTrace(false)
    static final class Stage extends Event {
        @Label("Function")
        String function;

        @Label("Stage")
        String stage;

        @Label("IR size after")
        int sizeAfter;
    }
}
