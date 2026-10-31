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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every {@code tornado.*} option is read through this package, so its default, parsing and precedence are the
 * same everywhere. Reading one straight from the system properties is a mistake this test reports.
 */
public class NoRawOptionReadsTest {

    /** The repository root, from the tornado-api module directory Maven runs the tests in. */
    private static final Path REPOSITORY = Path.of("..");

    private static final Pattern RAW_READ = Pattern.compile(
            "(System\\.getProperty|System\\.getProperties\\(\\)\\.getProperty|Boolean\\.getBoolean|Integer\\.getInteger|Long\\.getLong)\\(\\s*\"tornado\\.");

    /** The test harness reads its own keys, and saves and restores options around a test. */
    private static final List<String> EXCLUDED = List.of("tornado-unittests", "/api/options/");

    @Test
    public void optionsAreOnlyReadThroughTheCatalog() throws IOException {
        List<String> rawReads = new ArrayList<>();
        try (Stream<Path> files = Files.walk(REPOSITORY)) {
            files.filter(this::isProductionSource).forEach(file -> collectRawReads(file, rawReads));
        }
        assertEquals(List.of(), rawReads, "Read these options with their constant in uk.ac.manchester.tornado.api.options instead");
    }

    @Test
    public void scansTheWholeRepository() throws IOException {
        try (Stream<Path> files = Files.walk(REPOSITORY)) {
            assertTrue(files.filter(this::isProductionSource).count() > 500, "the repository root was not found");
        }
    }

    private boolean isProductionSource(Path file) {
        String path = file.toString().replace('\\', '/');
        return path.endsWith(".java") && path.contains("/src/main/java/") && !path.contains("/target/") && EXCLUDED.stream().noneMatch(path::contains);
    }

    private static void collectRawReads(Path file, List<String> rawReads) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = RAW_READ.matcher(lines.get(i));
                if (matcher.find()) {
                    rawReads.add(REPOSITORY.relativize(file) + ":" + (i + 1));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
