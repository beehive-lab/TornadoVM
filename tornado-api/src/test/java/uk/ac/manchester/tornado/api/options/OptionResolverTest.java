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

public class OptionResolverTest {

    private static final Option<Boolean> FLAG = Option.bool("tornado.test.flag").defaultValue(false).description("A flag.") //
            .category(OptionCategory.DEBUG).deprecatedKey("tornado.testFlag").build();

    private static final Option<Integer> NUMBER = Option.integer("tornado.test.number").defaultValue(7).description("A number.") //
            .category(OptionCategory.RUNTIME).build();

    private static final Option<String> TEXT = Option.string("tornado.test.text").defaultValue("default text").description("A text.") //
            .category(OptionCategory.RUNTIME).build();

    private static final Option<String> UNSET = Option.string("tornado.test.unset").noDefault("the consumer's choice").description("No default.") //
            .category(OptionCategory.RUNTIME).build();

    private static final TaskOption<Integer> BLOCK = TaskOption.integer("test.block").defaultValue(256).description("A block size.") //
            .category(OptionCategory.THREAD_SCHEDULING).withGlobalFallback().build();

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
        assertEquals(List.of("-Dtornado.testFlag is deprecated and will be removed in a future release; use -Dtornado.test.flag instead."), warnings);
    }

    @Test
    public void currentKeyWinsOverDeprecatedKey() {
        OptionResolver resolver = resolver(MapSource.of("tornado.testFlag", "true", "tornado.test.flag", "false"));
        assertEquals(false, resolver.resolve(FLAG));
        assertTrue(warnings.isEmpty());
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
