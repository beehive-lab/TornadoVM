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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.internal.options.OptionRegistry;

/**
 * The default of every option, written out, so that no default changes, and no option appears or disappears,
 * without this list changing in the same commit.
 *
 * <p>
 * The list started as every {@code tornado.*} key read before the options were consolidated (develop at 733af65,
 * after 8.0.0), with the default it had there. Each must still be an option, under its own or a deprecated key and
 * with the same default, or be listed as removed; and every option of the catalog must be listed. To add an option
 * or change a default, change this list too.
 */
public class OptionDefaultsTest {

    /** Key and its default; null where there is none. */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    /** Per-task suffix and its default; null where there is none. */
    private static final Map<String, Integer> TASK_DEFAULTS = new LinkedHashMap<>();

    /** A per-task suffix that was removed; its global fallback key is listed in {@code RemovedFlags}. */
    private static final Integer REMOVED = Integer.MIN_VALUE;

    static {
        // TornadoOptions
        DEFAULTS.put("tornado.opencl.compiler.flags", "-cl-mad-enable -cl-fast-relaxed-math -w");
        DEFAULTS.put("tornado.metal.compiler.flags", "");
        DEFAULTS.put("tornado.enable.fma", "TRUE");
        DEFAULTS.put("tornado.cuda.compile.profile", "default");
        DEFAULTS.put("tornado.cuda.compiler.flags", "");
        DEFAULTS.put("tornado.ns.time", "TRUE");
        DEFAULTS.put("tornado.backend", "0");
        DEFAULTS.put("tornado.enabled.backends", "");
        DEFAULTS.put("tornado.device", "0");
        DEFAULTS.put("tornado.threadInfo", "FALSE");
        DEFAULTS.put("tornado.printKernel", "FALSE");
        DEFAULTS.put("tornado.metal.priority", "0");
        DEFAULTS.put("tornado.cuda.priority", "0");
        DEFAULTS.put("tornado.opencl.priority", "10");
        DEFAULTS.put("tornado.device.memory", "4GB");
        DEFAULTS.put("tornado.print.bytecodes", "FALSE");
        DEFAULTS.put("tornado.staged.transfers", "FALSE");
        DEFAULTS.put("tornado.staged.chunk.size", Integer.toString(16 * 1024 * 1024));
        DEFAULTS.put("tornado.staged.ring.depth", "4");
        DEFAULTS.put("tornado.staged.min.size", Integer.toString(16 * 1024 * 1024));
        DEFAULTS.put("tornado.staged.fill.threads", Integer.toString(Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2))));
        DEFAULTS.put("tornado.dump.bytecodes.dir", "");
        DEFAULTS.put("tornado.experimental.reduce", "TRUE");
        DEFAULTS.put("tornado.events.dump", "FALSE");
        DEFAULTS.put("tornado.print.kernel.dir", "");
        DEFAULTS.put("tornado.circularevents", "TRUE");
        DEFAULTS.put("tornado.virtual.device", "FALSE");
        DEFAULTS.put("tornado.device.desc", "etc/virtual-device-template.json");
        DEFAULTS.put("tornado.profiler.dump.dir", "");
        DEFAULTS.put("tornado.recover.bailout", "FALSE");
        DEFAULTS.put("tornado.feature.extraction", "FALSE");
        DEFAULTS.put("tornado.enable.ip.logging", "FALSE");
        DEFAULTS.put("tornado.dump.to.ip", "");
        DEFAULTS.put("tornado.check.parameters", "TRUE");
        DEFAULTS.put("tornado.logger.codegen", "FALSE");
        DEFAULTS.put("tornado.logger.buildlir", "FALSE");
        DEFAULTS.put("tornado.metal.fastmath", "FALSE");
        DEFAULTS.put("tornado.enable.streamOut.blocking", "TRUE");
        DEFAULTS.put("tornado.concurrent.devices", "FALSE");
        DEFAULTS.put("tornado.max.events", "32768");
        DEFAULTS.put("tornado.scheduler.block", "FALSE");
        DEFAULTS.put("tornado.log.profiler", "FALSE");
        DEFAULTS.put("tornado.profiler", "FALSE");
        DEFAULTS.put("tornado.reuse.device.buffers", "TRUE");
        DEFAULTS.put("tornado.deallocate.buffers", "TRUE");
        DEFAULTS.put("tornado.debug", "FALSE");
        DEFAULTS.put("tornado.fullDebug", "FALSE");
        DEFAULTS.put("tornado.opencl.syncflush", "FALSE");
        DEFAULTS.put("tornado.vmflush", "TRUE");
        DEFAULTS.put("tornado.eventpool.maxwaitevents", "64");
        DEFAULTS.put("tornado.eventpool.size", "1024");
        DEFAULTS.put("tornado.vm.deps", "FALSE");
        DEFAULTS.put("tornado.opencl.profiling.enable", "TRUE");
        DEFAULTS.put("tornado.metal.profiling.enable", "TRUE");
        DEFAULTS.put("tornado.ooo-execution.enable", "FALSE");
        DEFAULTS.put("tornado.experimental.partial.unroll", "FALSE");
        DEFAULTS.put("tornado.clean.atomics.space", "FALSE");
        // tornado-api
        DEFAULTS.put("tornado.panama.objectHeader", null);
        DEFAULTS.put("tornado.load.api.implementation", "uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph");
        DEFAULTS.put("tornado.load.runtime.implementation", "uk.ac.manchester.tornado.runtime.TornadoCoreRuntime");
        DEFAULTS.put("tornado.load.tornado.implementation", "uk.ac.manchester.tornado.runtime.common.Tornado");
        DEFAULTS.put("tornado.chrome.event.tracer.filename", "chrome.json");
        DEFAULTS.put("tornado.chrome.event.tracer.enabled", "false");
        // tornado-runtime
        DEFAULTS.put("tornado.tvm.maxbytecodesize", "4096");
        DEFAULTS.put("tornado.features.dump.dir", "");
        // drivers-common
        DEFAULTS.put("tornado.tc.compileTimes", "false");
        DEFAULTS.put("tornado.tc.verbose", "false");
        DEFAULTS.put("tornado.tc.stats", "false");
        DEFAULTS.put("tornado.tc.memoize", "true");
        DEFAULTS.put("tornado.tc.varyingScalars", "3");
        DEFAULTS.put("tornado.tc.dynamicShapes", "false");
        // Metal
        DEFAULTS.put("tornado.metal.codecache.enable", "False");
        DEFAULTS.put("tornado.metal.codecache.dump", "False");
        DEFAULTS.put("tornado.metal.source.dump", "False");
        DEFAULTS.put("tornado.metal.codecache.dir", "/var/metal-codecache");
        DEFAULTS.put("tornado.metal.source.dir", "/var/metal-compiler");
        DEFAULTS.put("tornado.metal.log.dir", "/var/metal-logs");
        DEFAULTS.put("tornado.metal.dispatch.batch", "True");
        DEFAULTS.put("tornado.metal.dispatch.batchSize", "2");
        DEFAULTS.put("tornado.metal.dispatch.spinWait", "True");
        DEFAULTS.put("tornado.mlx.async", "false");
        // CUDA
        DEFAULTS.put("tornado.cuda.graph.asyncLaunch", "false");
        DEFAULTS.put("tornado.cuda.codecache.enable", "True");
        DEFAULTS.put("tornado.cuda.codecache.dir", "/var/cuda-codecache");
        DEFAULTS.put("tornado.cuda.compute.streams", "4");
        DEFAULTS.put("tornado.cuda.sched", "yield");
        DEFAULTS.put("tornado.cuda.nvcc", null);
        DEFAULTS.put("tornado.cuda.devrt.path", null);
        DEFAULTS.put("tornado.cuda.dp.pendingLaunchCount", null);
        DEFAULTS.put("tornado.cuda.payloadAlignment", "32");
        DEFAULTS.put("tornado.cuda.host.pinning", "True");
        DEFAULTS.put("tornado.cuda.host.pinning.all", "False");
        // OpenCL (also read by the CUDA code cache on develop)
        DEFAULTS.put("tornado.opencl.codecache.enable", "False");
        DEFAULTS.put("tornado.opencl.codecache.dump", "False");
        DEFAULTS.put("tornado.opencl.source.dump", "False");
        DEFAULTS.put("tornado.opencl.codecache.dir", "/var/opencl-codecache");
        DEFAULTS.put("tornado.opencl.source.dir", "/var/opencl-compiler");
        DEFAULTS.put("tornado.opencl.log.dir", "/var/opencl-logs");
        // TC
        DEFAULTS.put("tornado.tc.dump", "none");
        DEFAULTS.put("tornado.tc.dumpDir", "tc-dumps");
        DEFAULTS.put("tornado.tc.verify", "end");
        DEFAULTS.put("tornado.tc.only", "");
        // Blank on develop, which meant no report: the closed set names it `none`.
        DEFAULTS.put("tornado.tc.report", "none");
        DEFAULTS.put("tornado.enable.mathOptimizations", "true");
        DEFAULTS.put("tornado.tc.nativeHalf", "true");
        DEFAULTS.put("tornado.tc.signedOverflowUndefined", "false");
        // Native libraries and tools
        DEFAULTS.put("tornado.mlx.metallib", null);
        DEFAULTS.put("tornado.cudnn.deterministic", "false");
        DEFAULTS.put("tornado.nccl.spinWaitMicros", "1000");
        DEFAULTS.put("tornado.nccl.benchmark.cudaGraph", "false");
        DEFAULTS.put("tornado.fuzz.genDir", null);

        // Added with the options catalog
        DEFAULTS.put("tornado.options.file", null);
        DEFAULTS.put("tornado.options.allow", null);

        TASK_DEFAULTS.put("device", null);
        TASK_DEFAULTS.put("global.workgroup.size", null);
        TASK_DEFAULTS.put("local.workgroup.size", null);
        TASK_DEFAULTS.put("coarseness", null);
        // 256 in the code, but every SDK install set 512 in etc/tornado.properties, which is now the default.
        TASK_DEFAULTS.put("opencl.gpu.block.x", 512);
        TASK_DEFAULTS.put("opencl.gpu.block2d.x", 4);
        TASK_DEFAULTS.put("opencl.gpu.block2d.y", 4);
        // Read into fields nothing used: removed.
        TASK_DEFAULTS.put("metal.threads.per.threadgroup.x", REMOVED);
        TASK_DEFAULTS.put("metal.threads.per.threadgroup.y", REMOVED);
        TASK_DEFAULTS.put("metal.threads.per.threadgroup.z", REMOVED);
    }

    private final OptionRegistry registry = OptionRegistry.catalog();

    @Test
    public void everyListedKeyIsAnOptionWithThatDefaultOrIsRemoved() {
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            String key = entry.getKey();
            Optional<Option<?>> option = optionFor(key);
            boolean removed = registry.removed().stream().anyMatch(removedOption -> removedOption.key().equals(key));
            if (option.isEmpty()) {
                assertTrue(removed, key + " is neither an option nor removed");
                continue;
            }
            assertDefault(option.get(), entry.getValue());
        }
    }

    @Test
    public void everyCatalogOptionIsListed() {
        for (Option<?> option : registry.options()) {
            assertTrue(DEFAULTS.containsKey(option.key()) || option.deprecatedKeys().stream().anyMatch(DEFAULTS::containsKey),
                    option.key() + " is not listed in OptionDefaultsTest: add it with its default");
        }
        for (TaskOption<?> option : registry.taskOptions()) {
            assertTrue(TASK_DEFAULTS.containsKey(option.suffix()), "<id>." + option.suffix() + " is not listed in OptionDefaultsTest: add it with its default");
        }
    }

    @Test
    public void everyListedTaskSuffixIsATaskOptionWithThatDefault() {
        for (Map.Entry<String, Integer> entry : TASK_DEFAULTS.entrySet()) {
            if (REMOVED.equals(entry.getValue())) {
                assertTrue(registry.taskOptions().stream().noneMatch(option -> option.suffix().equals(entry.getKey())), entry.getKey());
                assertTrue(registry.removed().stream().anyMatch(removed -> removed.key().equals("tornado." + entry.getKey())), entry.getKey());
                continue;
            }
            TaskOption<?> option = registry.taskOptions().stream().filter(candidate -> candidate.suffix().equals(entry.getKey())).findFirst() //
                    .orElseThrow(() -> new AssertionError("No task option " + entry.getKey()));
            assertEquals(Optional.ofNullable(entry.getValue()), option.defaultValue(), entry.getKey());
        }
    }

    private Optional<Option<?>> optionFor(String key) {
        return registry.options().stream().filter(option -> option.key().equals(key) || option.deprecatedKeys().contains(key)).findFirst();
    }

    private static void assertDefault(Option<?> option, String developDefault) {
        if (developDefault == null) {
            assertEquals(Optional.empty(), option.defaultValue(), option.key() + " has no default in the list");
            return;
        }
        Object expected = developDefault.isBlank() && !option.parser().acceptsBlank() ? null : option.parser().parse(developDefault);
        if (expected == null) {
            fail(option.key() + ": blank default listed for a typed option");
        }
        assertEquals(Optional.of(expected), option.defaultValue(), option.key());
    }
}
