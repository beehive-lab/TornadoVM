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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.internal.options.OptionRegistry;
import uk.ac.manchester.tornado.api.internal.options.RemovedOption;

/**
 * The options catalog is the one source of the flags: every {@code -Dtornado.*} key that the documentation, the
 * READMEs, the Claude skills or the launchers mention must be a current flag. Former names and removed flags may
 * appear only on the generated flags page, which lists them.
 */
public class FlagMentionsTest {

    /** The repository root, from the tornado-api module directory Maven runs the tests in. */
    private static final Path REPOSITORY = Path.of("..");

    private static final Path FLAGS_PAGE = Path.of("docs", "source", "flags.rst");

    private static final Pattern FLAG = Pattern.compile("-D(tornado\\.[a-z][A-Za-z0-9_.-]*[A-Za-z0-9])");

    /** Paths left out: build output, vendored sources, and the changelog, which records flags as they were. */
    private static final List<String> EXCLUDED = List.of("/target/", "/.git/", "/dist/", "/bin/sdk/", "/OpenCL-Headers/", "/node_modules/", "CHANGELOG");

    /** Prefixes owned by the test harnesses and the benchmark suite, as in the start-up check. */
    private static final List<String> HARNESS_PREFIXES = List.of("tornado.unittests.", "tornado.test.", "tornado.benchmarks.");

    @Test
    public void everyMentionedFlagIsCurrent() throws IOException {
        OptionRegistry registry = OptionRegistry.catalog();
        Set<String> current = new HashSet<>();
        Set<String> former = new HashSet<>();
        registry.options().forEach(option -> {
            current.add(option.key());
            former.addAll(option.deprecatedKeys());
        });
        registry.taskOptions().forEach(option -> option.globalFallbackKey().ifPresent(current::add));
        registry.removed().stream().map(RemovedOption::key).forEach(former::add);

        List<String> problems = new ArrayList<>();
        for (Path file : documents()) {
            boolean flagsPage = REPOSITORY.relativize(file).normalize().equals(FLAGS_PAGE);
            Matcher matcher = FLAG.matcher(read(file));
            while (matcher.find()) {
                String key = matcher.group(1);
                if (current.contains(key) || HARNESS_PREFIXES.stream().anyMatch(key::startsWith) || (flagsPage && former.contains(key))) {
                    continue;
                }
                problems.add(REPOSITORY.relativize(file) + ": -D" + key + (former.contains(key) ? " (former or removed name)" : " (no such flag)"));
            }
        }
        assertEquals(List.of(), problems, "Use the current flag names: tornado --list-flags --all");
    }

    @Test
    public void scansTheDocsSkillsAndLaunchers() throws IOException {
        List<String> names = documents().stream().map(file -> REPOSITORY.relativize(file).toString().replace('\\', '/')).toList();
        assertTrue(names.contains("docs/source/flags.rst"), "the repository root was not found");
        assertTrue(names.contains(".claude/skills/tornadovm-flags/SKILL.md"), names.toString());
        assertTrue(names.contains("tornado-assembly/src/bin/tornado.py"), names.toString());
    }

    private static List<Path> documents() throws IOException {
        try (Stream<Path> files = Files.walk(REPOSITORY)) {
            return files.filter(Files::isRegularFile).filter(FlagMentionsTest::isDocument).toList();
        }
    }

    private static boolean isDocument(Path file) {
        String path = file.toString().replace('\\', '/');
        if (EXCLUDED.stream().anyMatch(path::contains)) {
            return false;
        }
        return path.endsWith(".md") || path.endsWith(".rst") || path.contains("/tornado-assembly/src/bin/");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }
    }
}
