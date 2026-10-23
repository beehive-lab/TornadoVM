/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.tc.check;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Clean-room and hygiene rules that every TC module enforces on its own sources in a unit test
 * (see {@code compiler-replacement/provenance.md} in mikepapadim/tornado-compiler-spec): no imports
 * of Graal, JVMCI, GraalVM SDK or ASM classes, the project licence header on every file, and no
 * unused imports (dead code left behind by a refactoring).
 */
public final class SourceRules {

    private static final Pattern FORBIDDEN_IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?"
            + "(tornado\\.graal|jdk\\.vm\\.ci|org\\.graalvm|jdk\\.graal|org\\.objectweb\\.asm)\\b.*$", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?[\\w.]+\\.(\\w+);$", Pattern.MULTILINE);
    private static final String LICENCE_MARKER = "Licensed under the Apache License, Version 2.0";

    private SourceRules() {
    }

    /** Violations found under {@code sourceRoot}; empty when the sources comply. */
    public static List<String> check(Path sourceRoot) {
        var violations = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                var text = Files.readString(file);
                var m = FORBIDDEN_IMPORT.matcher(text);
                while (m.find()) {
                    violations.add(file + ": forbidden import: " + m.group().strip());
                }
                var body = IMPORT.matcher(text).replaceAll("");
                var imports = IMPORT.matcher(text);
                while (imports.find()) {
                    if (!Pattern.compile("\\b" + imports.group(1) + "\\b").matcher(body).find()) {
                        violations.add(file + ": unused import: " + imports.group().strip());
                    }
                }
                if (!file.getFileName().toString().equals("module-info.java") && !text.contains(LICENCE_MARKER)) {
                    violations.add(file + ": missing licence header");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return violations;
    }
}
