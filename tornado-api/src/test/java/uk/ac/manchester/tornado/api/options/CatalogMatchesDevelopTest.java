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

/**
 * Every {@code tornado.*} key read on the {@code develop} branch before the options were consolidated (commit
 * 733af65), with the default it had there. Each must still be an option, under its own or a deprecated key and
 * with the same default, or be listed as removed.
 */
public class CatalogMatchesDevelopTest {

    /** Key on develop and its default there; null where develop had none. */
    private static final Map<String, String> DEVELOP_DEFAULTS = new LinkedHashMap<>();

    /** Per-task suffix on develop and its default there; null where develop had none. */
    private static final Map<String, Integer> DEVELOP_TASK_DEFAULTS = new LinkedHashMap<>();

    /** A per-task suffix that was removed; its global fallback key is listed in {@link RemovedFlags}. */
    private static final Integer REMOVED = Integer.MIN_VALUE;

    static {
        // TornadoOptions
        DEVELOP_DEFAULTS.put("tornado.opencl.compiler.flags", "-cl-mad-enable -cl-fast-relaxed-math -w");
        DEVELOP_DEFAULTS.put("tornado.metal.compiler.flags", "");
        DEVELOP_DEFAULTS.put("tornado.enable.fma", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.cuda.compile.profile", "default");
        DEVELOP_DEFAULTS.put("tornado.cuda.compiler.flags", "");
        DEVELOP_DEFAULTS.put("tornado.ns.time", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.backend", "0");
        DEVELOP_DEFAULTS.put("tornado.enabled.backends", "");
        DEVELOP_DEFAULTS.put("tornado.device", "0");
        DEVELOP_DEFAULTS.put("tornado.threadInfo", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.printKernel", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.metal.priority", "0");
        DEVELOP_DEFAULTS.put("tornado.cuda.priority", "0");
        DEVELOP_DEFAULTS.put("tornado.opencl.priority", "10");
        DEVELOP_DEFAULTS.put("tornado.device.memory", "4GB");
        DEVELOP_DEFAULTS.put("tornado.print.bytecodes", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.staged.transfers", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.staged.chunk.size", Integer.toString(16 * 1024 * 1024));
        DEVELOP_DEFAULTS.put("tornado.staged.ring.depth", "4");
        DEVELOP_DEFAULTS.put("tornado.staged.min.size", Integer.toString(16 * 1024 * 1024));
        DEVELOP_DEFAULTS.put("tornado.staged.fill.threads", Integer.toString(Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2))));
        DEVELOP_DEFAULTS.put("tornado.dump.bytecodes.dir", "");
        DEVELOP_DEFAULTS.put("tornado.experimental.reduce", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.events.dump", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.print.kernel.dir", "");
        DEVELOP_DEFAULTS.put("tornado.circularevents", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.virtual.device", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.device.desc", "etc/virtual-device-template.json");
        DEVELOP_DEFAULTS.put("tornado.profiler.dump.dir", "");
        DEVELOP_DEFAULTS.put("tornado.recover.bailout", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.feature.extraction", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.enable.ip.logging", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.dump.to.ip", "");
        DEVELOP_DEFAULTS.put("tornado.check.parameters", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.logger.codegen", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.logger.buildlir", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.metal.fastmath", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.enable.streamOut.blocking", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.concurrent.devices", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.max.events", "32768");
        DEVELOP_DEFAULTS.put("tornado.scheduler.block", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.log.profiler", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.profiler", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.reuse.device.buffers", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.deallocate.buffers", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.debug", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.fullDebug", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.opencl.syncflush", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.vmflush", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.eventpool.maxwaitevents", "64");
        DEVELOP_DEFAULTS.put("tornado.eventpool.size", "1024");
        DEVELOP_DEFAULTS.put("tornado.vm.deps", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.opencl.profiling.enable", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.metal.profiling.enable", "TRUE");
        DEVELOP_DEFAULTS.put("tornado.ooo-execution.enable", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.experimental.partial.unroll", "FALSE");
        DEVELOP_DEFAULTS.put("tornado.clean.atomics.space", "FALSE");
        // tornado-api
        DEVELOP_DEFAULTS.put("tornado.panama.objectHeader", null);
        DEVELOP_DEFAULTS.put("tornado.load.api.implementation", "uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph");
        DEVELOP_DEFAULTS.put("tornado.load.runtime.implementation", "uk.ac.manchester.tornado.runtime.TornadoCoreRuntime");
        DEVELOP_DEFAULTS.put("tornado.load.tornado.implementation", "uk.ac.manchester.tornado.runtime.common.Tornado");
        DEVELOP_DEFAULTS.put("tornado.chrome.event.tracer.filename", "chrome.json");
        DEVELOP_DEFAULTS.put("tornado.chrome.event.tracer.enabled", "false");
        // tornado-runtime
        DEVELOP_DEFAULTS.put("tornado.tvm.maxbytecodesize", "4096");
        DEVELOP_DEFAULTS.put("tornado.features.dump.dir", "");
        // drivers-common
        DEVELOP_DEFAULTS.put("tornado.tc.compileTimes", "false");
        DEVELOP_DEFAULTS.put("tornado.tc.verbose", "false");
        DEVELOP_DEFAULTS.put("tornado.tc.stats", "false");
        DEVELOP_DEFAULTS.put("tornado.tc.memoize", "true");
        DEVELOP_DEFAULTS.put("tornado.tc.varyingScalars", "3");
        DEVELOP_DEFAULTS.put("tornado.tc.dynamicShapes", "false");
        // Metal
        DEVELOP_DEFAULTS.put("tornado.metal.codecache.enable", "False");
        DEVELOP_DEFAULTS.put("tornado.metal.codecache.dump", "False");
        DEVELOP_DEFAULTS.put("tornado.metal.source.dump", "False");
        DEVELOP_DEFAULTS.put("tornado.metal.codecache.dir", "/var/metal-codecache");
        DEVELOP_DEFAULTS.put("tornado.metal.source.dir", "/var/metal-compiler");
        DEVELOP_DEFAULTS.put("tornado.metal.log.dir", "/var/metal-logs");
        DEVELOP_DEFAULTS.put("tornado.metal.dispatch.batch", "True");
        DEVELOP_DEFAULTS.put("tornado.metal.dispatch.batchSize", "2");
        DEVELOP_DEFAULTS.put("tornado.metal.dispatch.spinWait", "True");
        DEVELOP_DEFAULTS.put("tornado.mlx.async", "false");
        // CUDA
        DEVELOP_DEFAULTS.put("tornado.cuda.graph.asyncLaunch", "false");
        DEVELOP_DEFAULTS.put("tornado.cuda.codecache.enable", "True");
        DEVELOP_DEFAULTS.put("tornado.cuda.codecache.dir", "/var/cuda-codecache");
        DEVELOP_DEFAULTS.put("tornado.cuda.compute.streams", "4");
        DEVELOP_DEFAULTS.put("tornado.cuda.sched", "yield");
        DEVELOP_DEFAULTS.put("tornado.cuda.nvcc", null);
        DEVELOP_DEFAULTS.put("tornado.cuda.devrt.path", null);
        DEVELOP_DEFAULTS.put("tornado.cuda.dp.pendingLaunchCount", null);
        DEVELOP_DEFAULTS.put("tornado.cuda.payloadAlignment", "32");
        DEVELOP_DEFAULTS.put("tornado.cuda.host.pinning", "True");
        DEVELOP_DEFAULTS.put("tornado.cuda.host.pinning.all", "False");
        // OpenCL (also read by the CUDA code cache on develop)
        DEVELOP_DEFAULTS.put("tornado.opencl.codecache.enable", "False");
        DEVELOP_DEFAULTS.put("tornado.opencl.codecache.dump", "False");
        DEVELOP_DEFAULTS.put("tornado.opencl.source.dump", "False");
        DEVELOP_DEFAULTS.put("tornado.opencl.codecache.dir", "/var/opencl-codecache");
        DEVELOP_DEFAULTS.put("tornado.opencl.source.dir", "/var/opencl-compiler");
        DEVELOP_DEFAULTS.put("tornado.opencl.log.dir", "/var/opencl-logs");
        // TC
        DEVELOP_DEFAULTS.put("tornado.tc.dump", "none");
        DEVELOP_DEFAULTS.put("tornado.tc.dumpDir", "tc-dumps");
        DEVELOP_DEFAULTS.put("tornado.tc.verify", "end");
        DEVELOP_DEFAULTS.put("tornado.tc.only", "");
        DEVELOP_DEFAULTS.put("tornado.tc.report", "");
        DEVELOP_DEFAULTS.put("tornado.enable.mathOptimizations", "true");
        DEVELOP_DEFAULTS.put("tornado.tc.nativeHalf", "true");
        DEVELOP_DEFAULTS.put("tornado.tc.signedOverflowUndefined", "false");
        // Native libraries and tools
        DEVELOP_DEFAULTS.put("tornado.mlx.metallib", null);
        DEVELOP_DEFAULTS.put("tornado.cudnn.deterministic", "false");
        DEVELOP_DEFAULTS.put("tornado.nccl.spinWaitMicros", "1000");
        DEVELOP_DEFAULTS.put("tornado.nccl.benchmark.cudaGraph", "false");
        DEVELOP_DEFAULTS.put("tornado.fuzz.genDir", null);

        DEVELOP_TASK_DEFAULTS.put("device", null);
        DEVELOP_TASK_DEFAULTS.put("global.workgroup.size", null);
        DEVELOP_TASK_DEFAULTS.put("local.workgroup.size", null);
        DEVELOP_TASK_DEFAULTS.put("coarseness", null);
        // 256 in the code, but every SDK install set 512 in etc/tornado.properties, which is now the default.
        DEVELOP_TASK_DEFAULTS.put("opencl.gpu.block.x", 512);
        DEVELOP_TASK_DEFAULTS.put("opencl.gpu.block2d.x", 4);
        DEVELOP_TASK_DEFAULTS.put("opencl.gpu.block2d.y", 4);
        // Read into fields nothing used: removed.
        DEVELOP_TASK_DEFAULTS.put("metal.threads.per.threadgroup.x", REMOVED);
        DEVELOP_TASK_DEFAULTS.put("metal.threads.per.threadgroup.y", REMOVED);
        DEVELOP_TASK_DEFAULTS.put("metal.threads.per.threadgroup.z", REMOVED);
    }

    private final OptionRegistry registry = OptionRegistry.catalog();

    @Test
    public void everyDevelopKeyIsAnOptionWithTheSameDefaultOrIsRemoved() {
        for (Map.Entry<String, String> entry : DEVELOP_DEFAULTS.entrySet()) {
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
    public void everyDevelopTaskSuffixIsATaskOptionWithTheSameDefault() {
        for (Map.Entry<String, Integer> entry : DEVELOP_TASK_DEFAULTS.entrySet()) {
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
            assertEquals(Optional.empty(), option.defaultValue(), option.key() + " had no default on develop");
            return;
        }
        Object expected = developDefault.isBlank() && !option.parser().acceptsBlank() ? null : option.parser().parse(developDefault);
        if (expected == null) {
            fail(option.key() + ": blank develop default for a typed option");
        }
        assertEquals(Optional.of(expected), option.defaultValue(), option.key());
    }
}
