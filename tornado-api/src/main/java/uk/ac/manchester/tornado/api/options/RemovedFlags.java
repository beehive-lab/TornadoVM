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

import java.util.List;

/**
 * Options removed from TornadoVM. They are kept here for one release so that setting one is reported instead of
 * being silently ignored.
 */
public final class RemovedFlags {

    private static final String NO_EFFECT = "It had no effect.";
    private static final String FIXED = "TornadoVM always behaves as with its default";

    public static final List<RemovedOption> ALL = List.of(new RemovedOption("tornado.logger.codegen", NO_EFFECT), //
            new RemovedOption("tornado.logger.buildlir", NO_EFFECT), //
            new RemovedOption("tornado.platform", NO_EFFECT + " Use tornado.backend."), //
            new RemovedOption("tornado.driver", NO_EFFECT + " Use tornado.backend."), //
            new RemovedOption("tornado.opencl.print", NO_EFFECT + " Use tornado.print.kernel."), //
            new RemovedOption("tornado.events.log", NO_EFFECT), //
            new RemovedOption("tornado.vectors.enable", NO_EFFECT), //
            new RemovedOption("tornado.kernels.parallelise", NO_EFFECT), //
            new RemovedOption("tornado.enable.fastMathOptimizations", NO_EFFECT + " Use tornado.enable.math.optimizations."), //
            new RemovedOption("tornado.enable.nativeFunctions", NO_EFFECT), //
            new RemovedOption("tornado.metal.threadgroupHint", NO_EFFECT + " Use <task>.metal.threads.per.threadgroup.{x,y,z}."), //
            new RemovedOption("tornado.debug.executionTime", NO_EFFECT + " Use tornado.profiler."), //
            new RemovedOption("tornado.unroll.factor", NO_EFFECT), //
            new RemovedOption("tornado.vmflush", FIXED + " (true)."), //
            new RemovedOption("tornado.vm.deps", FIXED + " (false)."), //
            new RemovedOption("tornado.opencl.syncflush", FIXED + " (false)."), //
            new RemovedOption("tornado.circularevents", FIXED + " (true)."), //
            new RemovedOption("tornado.experimental.reduce", FIXED + " (true)."), //
            new RemovedOption("tornado.check.parameters", FIXED + " (true)."), //
            new RemovedOption("tornado.clean.atomics.space", FIXED + " (false)."), //
            new RemovedOption("tornado.enable.streamOut.blocking", FIXED + " (true)."), //
            new RemovedOption("tornado.eventpool.size", FIXED + " (1024)."), //
            new RemovedOption("tornado.eventpool.maxwaitevents", FIXED + " (64)."), //
            new RemovedOption("tornado.events.dump", FIXED + " (false)."), //
            new RemovedOption("tornado.max.events", FIXED + " (32768)."), //
            new RemovedOption("tornado.enable.ip.logging", "Sending profiler logs over a socket was removed."), //
            new RemovedOption("tornado.dump.to.ip", "Sending profiler logs over a socket was removed. Use tornado.profiler.dump.dir."), //
            new RemovedOption("tornado.panama.objectHeader", "The array header size is derived from the JVM's compressed-pointer setting."), //
            new RemovedOption("tornado.nccl.benchmark.cudaGraph", "Pass --cuda-graph to the NCCL benchmark instead."));

    private RemovedFlags() {
    }
}
