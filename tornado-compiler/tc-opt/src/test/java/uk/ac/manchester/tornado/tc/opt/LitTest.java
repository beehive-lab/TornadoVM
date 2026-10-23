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
package uk.ac.manchester.tornado.tc.opt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import uk.ac.manchester.tornado.tc.check.Lit;
import uk.ac.manchester.tornado.tc.opt.tools.OptTool;

/** Runs every {@code .tcir} file under {@code src/test/lit} (the module's LLVM-style file tests). */
public class LitTest {

    private static final Path ROOT = Path.of("src", "test", "lit");

    private static final Lit LIT = new Lit(Map.of("tc-opt", (args, stdin) -> {
        var result = OptTool.run(args, stdin);
        return new Lit.ToolResult(result.exitCode(), result.stdout(), result.stderr());
    }));

    public static List<Object[]> files() {
        return Lit.discover(ROOT, ".tcir");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("files")
    public void run(String name, Path file) {
        var outcome = LIT.run(file);
        assertTrue(outcome.passed(), name + "\n" + outcome.log());
    }
}
