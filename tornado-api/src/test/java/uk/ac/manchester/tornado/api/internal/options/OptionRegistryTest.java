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
package uk.ac.manchester.tornado.api.internal.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.options.DebugFlags;
import uk.ac.manchester.tornado.api.options.InvalidOptionValueException;
import uk.ac.manchester.tornado.api.options.Option;
import uk.ac.manchester.tornado.api.options.OptionCategory;

public class OptionRegistryTest {

    private final OptionRegistry registry = OptionRegistry.catalog();

    /** Each key set to {@code true}, the way the start-up check sees them: only the keys matter. */
    private List<OptionDiagnostic> diagnose(String... keys) {
        String[] keysAndValues = new String[keys.length * 2];
        for (int i = 0; i < keys.length; i++) {
            keysAndValues[2 * i] = keys[i];
            keysAndValues[2 * i + 1] = "true";
        }
        return registry.diagnose(MapSource.of(keysAndValues), List.of());
    }

    @Test
    public void collectsEveryCatalog() {
        Set<OptionCategory> categories = new HashSet<>();
        registry.options().forEach(option -> categories.add(option.category()));
        registry.taskOptions().forEach(option -> categories.add(option.category()));
        assertEquals(Set.of(OptionCategory.values()), categories);
        assertTrue(registry.find("tornado.debug").isPresent());
        assertEquals(Optional.of(DebugFlags.THREAD_INFO), registry.find("tornado.threadInfo"));
    }

    @Test
    public void knownKeysAreNotReported() {
        assertEquals(List.of(), diagnose("tornado.debug", "tornado.opencl.gpu.block.x", "java.version", "s0.t0.device"));
    }

    @Test
    public void testHarnessKeysAreNotReported() {
        assertEquals(List.of(), diagnose("tornado.unittests.verbose", "tornado.test.upload.cold"));
    }

    @Test
    public void deprecatedKeyIsReportedAtStartUpWithItsDeadline() {
        List<OptionDiagnostic> diagnostics = diagnose("tornado.threadInfo");
        assertEquals(1, diagnostics.size());
        assertEquals(OptionDiagnostic.Kind.DEPRECATED, diagnostics.get(0).kind());
        assertEquals("-Dtornado.threadInfo is deprecated and will stop working in TornadoVM 8.1.0; use -Dtornado.thread.info instead.", diagnostics.get(0).message());
    }

    @Test
    public void deprecatedKeySetWithItsNewNameToAnotherValueIsAConflict() {
        List<OptionDiagnostic> diagnostics = registry.diagnose(MapSource.of("tornado.threadInfo", "true", "tornado.thread.info", "false"), List.of());
        assertEquals(1, diagnostics.size());
        assertEquals(OptionDiagnostic.Kind.CONFLICT, diagnostics.get(0).kind());
        assertEquals(List.of(), registry.diagnose(MapSource.of("tornado.threadInfo", "true", "tornado.thread.info", "true"), List.of()));
    }

    @Test
    public void unknownKeyIsReportedWithTheClosestOption() {
        List<OptionDiagnostic> diagnostics = diagnose("tornado.printKernell");
        assertEquals(1, diagnostics.size());
        assertEquals(OptionDiagnostic.Kind.UNKNOWN, diagnostics.get(0).kind());
        assertEquals("-Dtornado.printKernell is not a TornadoVM option and is ignored. Did you mean -Dtornado.print.kernel?"
                + " If the application reads it, add it to -Dtornado.options.allow.", diagnostics.get(0).message());
    }

    @Test
    public void unknownKeyFarFromAnyOptionHasNoSuggestion() {
        assertEquals("-Dtornado.completely.different is not a TornadoVM option and is ignored. If the application reads it, add it to -Dtornado.options.allow.",
                diagnose("tornado.completely.different").get(0).message());
    }

    @Test
    public void allowedKeysAndPrefixesAreNotReported() {
        MapSource source = MapSource.of("tornado.backend.name", "x", "tornado.app.one", "1", "tornado.app.two", "2", "tornado.other", "3");
        List<OptionDiagnostic> diagnostics = registry.diagnose(source, List.of("tornado.backend.name", "tornado.app."));
        assertEquals(List.of("tornado.other"), diagnostics.stream().map(OptionDiagnostic::key).toList());
    }

    @Test
    public void removedKeyIsReportedWithItsReason() {
        List<OptionDiagnostic> diagnostics = diagnose("tornado.vmflush");
        assertEquals(OptionDiagnostic.Kind.REMOVED, diagnostics.get(0).kind());
        assertEquals("-Dtornado.vmflush was removed and is ignored. TornadoVM always behaves as with its default (true).", diagnostics.get(0).message());
    }

    @Test
    public void formerAnnotationLoaderKeysAreRemoved() {
        assertEquals(List.of(OptionDiagnostic.Kind.REMOVED, OptionDiagnostic.Kind.REMOVED),
                diagnose("tornado.load.annotation.implementation", "tornado.load.annotation.parallel").stream().map(OptionDiagnostic::kind).toList());
    }

    @Test
    public void validateReportsEveryBadValue() {
        MapSource source = MapSource.of("tornado.debug", "ture", "tornado.cuda.sched", "blcoking", "tornado.opencl.gpu.block.x", "big", "tornado.thread.info", "true");
        List<String> keys = registry.validate(source).stream().map(InvalidOptionValueException::getKey).sorted().toList();
        assertEquals(List.of("tornado.cuda.sched", "tornado.debug", "tornado.opencl.gpu.block.x"), keys);
        assertEquals(List.of(), registry.validate(MapSource.of("tornado.cuda.sched", "Blocking", "tornado.tc.report", "html")));
    }

    @Test
    public void taskKeyWithAnUnknownSuffixIsReported() {
        List<OptionDiagnostic> diagnostics = registry.diagnoseTaskGraph(MapSource.of("s0.t0.devcie", "1:0", "s0.t0.device", "1:0"), "s0", List.of("t0", "t1"));
        assertEquals(1, diagnostics.size());
        assertEquals("-Ds0.t0.devcie: devcie is not a per-task option and is ignored. Did you mean -Ds0.t0.device?", diagnostics.get(0).message());
    }

    @Test
    public void taskKeyNamingAMissingTaskIsReported() {
        List<OptionDiagnostic> diagnostics = registry.diagnoseTaskGraph(MapSource.of("s0.t9.device", "1:0", "s0.device", "0:0"), "s0", List.of("t0", "t1"));
        assertEquals(1, diagnostics.size());
        assertEquals("-Ds0.t9.device is ignored: task graph s0 has no task t9 (tasks: t0, t1).", diagnostics.get(0).message());
    }

    @Test
    public void applicationPropertiesUnderTheGraphNameAreLeftAlone() {
        assertEquals(List.of(), registry.diagnoseTaskGraph(MapSource.of("benchmark.size", "1024", "benchmark.iterations", "10"), "benchmark", List.of("t0")));
        assertEquals("-Dbenchmark.devise is not a per-task option and is ignored. Did you mean -Dbenchmark.device?",
                registry.diagnoseTaskGraph(MapSource.of("benchmark.devise", "1:0"), "benchmark", List.of("t0")).get(0).message());
    }

    @Test
    public void taskKeyWithABadValueFails() {
        InvalidOptionValueException e = assertThrows(InvalidOptionValueException.class,
                () -> registry.diagnoseTaskGraph(MapSource.of("s0.t0.local.workgroup.size", "16,x"), "s0", List.of("t0")));
        assertEquals("s0.t0.local.workgroup.size", e.getKey());
    }

    @Test
    public void settingAStaticOptionAlreadyReadIsReported() {
        Option<Integer> number = Option.integer("tornado.test.runtime.number").defaultValue(7).description("A number.").category(OptionCategory.DEBUG).build();
        OptionRegistry own = new OptionRegistry(List.of(number), List.of(), List.of(new RemovedOption("tornado.test.gone", "Gone.")));
        OptionResolver previous = OptionResolver.install(new OptionResolver(MapSource.of(), message -> {
        }));
        try {
            assertEquals(Optional.empty(), own.checkSetAtRunTime("tornado.test.runtime.number", "8"));
            assertEquals(7, number.get());
            assertEquals(Optional.empty(), own.checkSetAtRunTime("tornado.test.runtime.number", "7"));
            assertTrue(own.checkSetAtRunTime("tornado.test.runtime.number", "8").orElseThrow().contains("already read it as 7 and keeps that value"));
            assertThrows(InvalidOptionValueException.class, () -> own.checkSetAtRunTime("tornado.test.runtime.number", "eight"));
            assertEquals(Optional.of("tornado.test.gone was removed and is ignored. Gone."), own.checkSetAtRunTime("tornado.test.gone", "x"));
        } finally {
            OptionResolver.install(previous);
        }
    }

    @Test
    public void keyCannotBeDeclaredTwice() {
        Option<Boolean> first = Option.bool("tornado.same").defaultValue(true).description("First.").category(OptionCategory.DEBUG).build();
        Option<Boolean> second = Option.bool("tornado.other").defaultValue(true).description("Second.").category(OptionCategory.DEBUG).deprecatedKey("tornado.same", "9.9.9").build();
        assertThrows(IllegalStateException.class, () -> new OptionRegistry(List.of(first, second), List.of(), List.of()));
    }

    @Test
    public void keyCannotBeBothDeclaredAndRemoved() {
        Option<Boolean> option = Option.bool("tornado.same").defaultValue(true).description("Option.").category(OptionCategory.DEBUG).build();
        assertThrows(IllegalStateException.class, () -> new OptionRegistry(List.of(option), List.of(), List.of(new RemovedOption("tornado.same", "Gone."))));
    }

    @Test
    public void editDistanceCountsSingleCharacterEdits() {
        assertEquals(0, OptionRegistry.editDistance("tornado", "tornado"));
        assertEquals(1, OptionRegistry.editDistance("tornado.debug", "tornado.debugs"));
        assertEquals(3, OptionRegistry.editDistance("kitten", "sitting"));
    }
}
