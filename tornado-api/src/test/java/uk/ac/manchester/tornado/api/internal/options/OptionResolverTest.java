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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.manchester.tornado.api.options.InvalidOptionValueException;
import uk.ac.manchester.tornado.api.options.Option;
import uk.ac.manchester.tornado.api.options.OptionCategory;
import uk.ac.manchester.tornado.api.options.TaskOption;

public class OptionResolverTest {

    private static final Option<Boolean> FLAG = Option.bool("tornado.test.flag").defaultValue(false).description("A flag.") //
            .category(OptionCategory.DEBUG).deprecatedKey("tornado.testFlag", "9.9.9").build();

    private static final Option<Integer> NUMBER = Option.integer("tornado.test.number").defaultValue(7).description("A number.") //
            .category(OptionCategory.RUNTIME).build();

    private static final Option<String> TEXT = Option.string("tornado.test.text").defaultValue("default text").description("A text.") //
            .category(OptionCategory.RUNTIME).build();

    private static final Option<String> UNSET = Option.string("tornado.test.unset").noDefault("the consumer's choice").description("No default.") //
            .category(OptionCategory.RUNTIME).build();

    private static final TaskOption<Integer> BLOCK = TaskOption.integer("test.block").defaultValue(256).description("A block size.") //
            .category(OptionCategory.THREAD_SCHEDULING).withGlobalFallback().build();

    private static final TaskOption<Integer> RENAMED = TaskOption.integer("test.renamed").description("A renamed per-task option.") //
            .category(OptionCategory.THREAD_SCHEDULING).withGlobalFallback().deprecatedSuffix("test.oldName", "9.9.9").build();

    private final List<String> warnings = new ArrayList<>();

    private OptionResolver resolver(OptionSource... layers) {
        return new OptionResolver(new LayeredSource(List.of(layers)), warnings::add);
    }

    @Test
    public void unsetOptionTakesItsDefault() {
        OptionResolver resolver = resolver(MapSource.of());
        assertEquals(false, resolver.resolve(FLAG));
        assertEquals(7, resolver.resolve(NUMBER));
        assertEquals(Optional.empty(), resolver.find(NUMBER));
    }

    @Test
    public void commandLineWinsOverPropertiesFile() {
        OptionResolver resolver = resolver(MapSource.of("tornado.test.number", "1"), MapSource.of("tornado.test.number", "2", "tornado.test.flag", "true"));
        assertEquals(1, resolver.resolve(NUMBER));
        assertEquals(true, resolver.resolve(FLAG));
    }

    @Test
    public void deprecatedKeyStillWorksAndWarnsOnce() {
        OptionResolver resolver = resolver(MapSource.of("tornado.testFlag", "true"));
        assertEquals(true, resolver.resolve(FLAG));
        assertEquals(true, resolver.resolve(FLAG));
        assertEquals(List.of("-Dtornado.testFlag is deprecated and will stop working in TornadoVM 9.9.9; use -Dtornado.test.flag instead."), warnings);
    }

    @Test
    public void currentKeyWinsOverDeprecatedKeyAndAConflictIsReported() {
        OptionResolver resolver = resolver(MapSource.of("tornado.testFlag", "true", "tornado.test.flag", "false"));
        assertEquals(false, resolver.resolve(FLAG));
        assertEquals(false, resolver.resolve(FLAG));
        assertEquals(List.of("-Dtornado.test.flag and its former name -Dtornado.testFlag are both set, to different values; -Dtornado.test.flag is used."), warnings);
    }

    @Test
    public void bothKeysWithTheSameValueAreNotAConflict() {
        OptionResolver resolver = resolver(MapSource.of("tornado.testFlag", "true", "tornado.test.flag", "true"));
        assertEquals(true, resolver.resolve(FLAG));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    public void deprecatedTaskSuffixStillWorksForTheTaskAndTheGlobalFallback() {
        assertEquals(Optional.of(8), resolver(MapSource.of("s0.t0.test.oldName", "8")).lookup(RENAMED, "s0.t0"));
        assertEquals(Optional.of(16), resolver(MapSource.of("tornado.test.oldName", "16")).lookup(RENAMED, "s0.t0"));
        assertEquals(Optional.of(4), resolver(MapSource.of("s0.t0.test.oldName", "8", "s0.t0.test.renamed", "4")).lookup(RENAMED, "s0.t0"));
        assertEquals(3, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).startsWith("-Ds0.t0.test.oldName is deprecated"), warnings.get(0));
    }

    @Test
    public void invalidValueNamesTheKey() {
        OptionResolver resolver = resolver(MapSource.of("tornado.test.number", "seven"));
        InvalidOptionValueException e = assertThrows(InvalidOptionValueException.class, () -> resolver.resolve(NUMBER));
        assertEquals("tornado.test.number", e.getKey());
        assertTrue(e.getMessage().startsWith("Invalid value 'seven' for -Dtornado.test.number: expected int"), e.getMessage());
    }

    @Test
    public void blankTextLeavesTypedOptionsUnsetButIsAString() {
        OptionResolver resolver = resolver(MapSource.of("tornado.test.number", "", "tornado.test.flag", " ", "tornado.test.text", ""));
        assertEquals(7, resolver.resolve(NUMBER));
        assertEquals(false, resolver.resolve(FLAG));
        assertEquals("", resolver.resolve(TEXT));
    }

    @Test
    public void optionWithoutDefaultIsFoundOnlyWhenSet() {
        assertEquals(Optional.empty(), resolver(MapSource.of()).find(UNSET));
        assertThrows(IllegalStateException.class, () -> resolver(MapSource.of()).resolve(UNSET));
        assertEquals(Optional.of("/opt/nvcc"), resolver(MapSource.of("tornado.test.unset", "/opt/nvcc")).find(UNSET));
    }

    @Test
    public void taskKeyWinsOverGlobalFallbackWhichWinsOverDefault() {
        assertEquals(Optional.empty(), resolver(MapSource.of()).lookup(BLOCK, "s0.t0"));
        assertEquals(Optional.of(64), resolver(MapSource.of("tornado.test.block", "64")).lookup(BLOCK, "s0.t0"));
        assertEquals(Optional.of(32), resolver(MapSource.of("tornado.test.block", "64", "s0.t0.test.block", "32")).lookup(BLOCK, "s0.t0"));
    }

    @Test
    public void taskOptionIsSetOnlyByItsOwnKey() {
        OptionResolver resolver = resolver(MapSource.of("tornado.test.block", "64", "s0.t1.test.block", "8"));
        assertFalse(resolver.isSetFor(BLOCK, "s0.t0"));
        assertTrue(resolver.isSetFor(BLOCK, "s0.t1"));
    }

    @Test
    public void propertiesFileIsReadAndMissingFileIsEmpty(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("tornado.properties");
        Files.writeString(file, "# comment\ntornado.test.number = 3\n");
        assertEquals(3, resolver(PropertiesFileSource.load(file, warnings::add)).resolve(NUMBER));
        assertEquals(7, resolver(PropertiesFileSource.load(directory.resolve("missing"), warnings::add)).resolve(NUMBER));
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void installingAResolverMakesStaticOptionsResolveAgain() {
        OptionResolver previous = OptionResolver.install(resolver(MapSource.of("tornado.test.number", "1")));
        try {
            assertEquals(1, NUMBER.get());
            assertTrue(NUMBER.isResolved());
            assertEquals(Optional.of(1), NUMBER.resolvedValue());
            OptionResolver.install(resolver(MapSource.of("tornado.test.number", "2")));
            assertFalse(NUMBER.isResolved());
            assertEquals(2, NUMBER.get());
        } finally {
            OptionResolver.install(previous);
        }
    }

    @Test
    public void optionsFileIsReadBetweenTheCommandLineAndTheInstallation(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("app.properties");
        Files.writeString(file, "tornado.test.number = 5\ntornado.test.text = from file\n");
        System.setProperty("tornado.options.file", file.toString());
        System.setProperty("tornado.test.text", "from -D");
        try {
            OptionResolver resolver = OptionResolver.createDefault();
            assertEquals(5, resolver.resolve(NUMBER));
            assertEquals("from -D", resolver.resolve(TEXT));
        } finally {
            System.clearProperty("tornado.options.file");
            System.clearProperty("tornado.test.text");
        }
    }

    @Test
    public void systemPropertiesAreReadLive() {
        SystemPropertySource source = new SystemPropertySource();
        System.setProperty("tornado.test.live", "first");
        try {
            assertEquals(Optional.of("first"), source.lookup("tornado.test.live"));
            System.setProperty("tornado.test.live", "second");
            assertEquals(Optional.of("second"), source.lookup("tornado.test.live"));
        } finally {
            System.clearProperty("tornado.test.live");
        }
    }
}
