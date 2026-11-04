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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.internal.options.OptionRegistry;
import uk.ac.manchester.tornado.api.internal.options.RemovedOption;

/**
 * Deprecated keys and removed-option warnings are kept until their deadline, a TornadoVM version. Once the project
 * version reaches it, this test fails: delete the deprecated key (or the {@code RemovedFlags} entry) and its
 * documentation.
 */
public class DeprecationDeadlineTest {

    /** The root pom, from the tornado-api module directory Maven runs the tests in. */
    private static final Path ROOT_POM = Path.of("..", "pom.xml");

    private static final Pattern REVISION = Pattern.compile("<revision>(\\d+)\\.(\\d+)\\.(\\d+)[^<]*</revision>");

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    @Test
    public void noDeadlineHasPassed() throws IOException {
        int[] project = projectVersion();
        OptionRegistry registry = OptionRegistry.catalog();
        List<String> overdue = new ArrayList<>();
        for (Option<?> option : registry.options()) {
            option.deprecations().stream().filter(d -> reached(project, d.dropIn())).forEach(d -> overdue.add("-D" + d.key() + " (former name of " + option.key() + ")"));
        }
        for (TaskOption<?> option : registry.taskOptions()) {
            option.deprecations().stream().filter(d -> reached(project, d.dropIn())).forEach(d -> overdue.add("-D<id>." + d.key() + " (former name of <id>." + option.suffix() + ")"));
        }
        for (RemovedOption removed : registry.removed()) {
            if (reached(project, removed.dropIn())) {
                overdue.add("-D" + removed.key() + " (RemovedFlags)");
            }
        }
        assertEquals(List.of(), overdue, "Version " + Arrays.toString(project) + " reached the deadline of these keys: delete them");
    }

    @Test
    public void everyDeadlineIsAVersion() {
        OptionRegistry registry = OptionRegistry.catalog();
        registry.options().forEach(option -> option.deprecations().forEach(d -> assertTrue(VERSION.matcher(d.dropIn()).matches(), d.toString())));
        registry.taskOptions().forEach(option -> option.deprecations().forEach(d -> assertTrue(VERSION.matcher(d.dropIn()).matches(), d.toString())));
        registry.removed().forEach(removed -> assertTrue(VERSION.matcher(removed.dropIn()).matches(), removed.toString()));
    }

    @Test
    public void comparesVersionsByNumber() {
        assertTrue(reached(new int[] { 8, 1, 0 }, "8.1.0"));
        assertTrue(reached(new int[] { 8, 10, 0 }, "8.9.0"));
        assertTrue(!reached(new int[] { 8, 0, 9 }, "8.1.0"));
    }

    private static int[] projectVersion() throws IOException {
        Matcher matcher = REVISION.matcher(Files.readString(ROOT_POM));
        assertTrue(matcher.find(), "no <revision>x.y.z</revision> in " + ROOT_POM);
        return new int[] { Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)) };
    }

    private static boolean reached(int[] project, String deadline) {
        Matcher matcher = VERSION.matcher(deadline);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a version: " + deadline);
        }
        for (int i = 0; i < 3; i++) {
            int part = Integer.parseInt(matcher.group(i + 1));
            if (project[i] != part) {
                return project[i] > part;
            }
        }
        return true;
    }
}
