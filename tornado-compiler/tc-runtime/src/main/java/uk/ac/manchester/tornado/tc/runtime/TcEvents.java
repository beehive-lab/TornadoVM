/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
