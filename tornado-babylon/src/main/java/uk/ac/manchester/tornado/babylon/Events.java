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
package uk.ac.manchester.tornado.babylon;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * JFR events of the Babylon frontend and the HAT backend, for profiling HAT programs and
 * code-model kernels with JDK Flight Recorder ({@code profile.sh jfr}).
 */
public final class Events {

    private Events() {
    }

    /** A kernel's code model translated to TC IR (lowering, SSA, translation; TC's own pipeline follows). */
    @Name("tornado.babylon.Translate")
    @Label("Babylon code model to TC IR")
    @Category({ "TornadoVM", "Babylon" })
    public static final class Translate extends Event {
        @Label("Kernel")
        public String kernel;

        @Label("Operations")
        @Description("Operations of the kernel's own code model (not its callees)")
        public int operations;
    }

    /** One HAT dispatch (dispatchKernel or dispatchTile) on TornadoVM, including the plan on the first. */
    @Name("tornado.hat.Dispatch")
    @Label("HAT dispatch")
    @Category({ "TornadoVM", "HAT" })
    public static final class Dispatch extends Event {
        @Label("Kernel")
        public String kernel;

        @Label("Range")
        public String range;

        @Label("Tile")
        @Description("dispatchTile (CUDA Tile) rather than dispatchKernel")
        public boolean tile;

        @Label("New plan")
        @Description("This dispatch built the execution plan (first launch of this kernel, range and arguments): it includes compilation")
        public boolean newPlan;
    }
}
