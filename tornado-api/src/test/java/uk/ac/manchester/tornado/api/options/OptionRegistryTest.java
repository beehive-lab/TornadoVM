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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class OptionRegistryTest {

    private final OptionRegistry registry = OptionRegistry.catalog();

    @Test
    public void collectsEveryCatalog() {
        Set<OptionCategory> categories = new HashSet<>();
        registry.options().forEach(option -> categories.add(option.category()));
        registry.taskOptions().forEach(option -> categories.add(option.category()));
        assertEquals(Set.of(OptionCategory.values()), categories);
        assertTrue(registry.find("tornado.debug").isPresent());
    }

    @Test
    public void knownKeysAreNotReported() {
        assertEquals(List.of(), registry.diagnose(List.of("tornado.debug", "tornado.opencl.gpu.block.x", "tornado.threadInfo", "java.version", "s0.t0.device")));
    }

    @Test
    public void testHarnessKeysAreNotReported() {
        assertEquals(List.of(), registry.diagnose(List.of("tornado.unittests.verbose", "tornado.test.upload.cold")));
    }

    @Test
    public void unknownKeyIsReportedWithTheClosestOption() {
        List<OptionDiagnostic> diagnostics = registry.diagnose(List.of("tornado.printKernell"));
        assertEquals(1, diagnostics.size());
        assertEquals(OptionDiagnostic.Kind.UNKNOWN, diagnostics.get(0).kind());
        assertEquals("-Dtornado.printKernell is not a TornadoVM option and is ignored. Did you mean -Dtornado.print.kernel?", diagnostics.get(0).message());
    }

    @Test
    public void unknownKeyFarFromAnyOptionHasNoSuggestion() {
        assertEquals("-Dtornado.completely.different is not a TornadoVM option and is ignored.", registry.diagnose(List.of("tornado.completely.different")).get(0).message());
    }

    @Test
    public void removedKeyIsReportedWithItsReason() {
        List<OptionDiagnostic> diagnostics = registry.diagnose(List.of("tornado.vmflush"));
        assertEquals(OptionDiagnostic.Kind.REMOVED, diagnostics.get(0).kind());
        assertEquals("-Dtornado.vmflush was removed and is ignored. TornadoVM always behaves as with its default (true).", diagnostics.get(0).message());
    }

    @Test
    public void keyCannotBeDeclaredTwice() {
        Option<Boolean> first = Option.bool("tornado.same").defaultValue(true).description("First.").category(OptionCategory.DEBUG).build();
        Option<Boolean> second = Option.bool("tornado.other").defaultValue(true).description("Second.").category(OptionCategory.DEBUG).deprecatedKey("tornado.same").build();
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
