/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.tc.frontend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import uk.ac.manchester.tornado.tc.check.Lit;
import uk.ac.manchester.tornado.tc.frontend.tools.JavaTool;

/** Runs every {@code .tcir} file under {@code src/test/lit} (the module's LLVM-style file tests). */
public class LitTest {

    private static final Path ROOT = Path.of("src", "test", "lit");

    private static final Lit LIT = new Lit(Map.of("tc-java", (args, stdin) -> {
        var result = JavaTool.run(args, stdin);
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
