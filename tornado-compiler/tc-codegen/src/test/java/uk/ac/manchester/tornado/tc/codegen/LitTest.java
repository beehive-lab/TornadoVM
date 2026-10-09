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
package uk.ac.manchester.tornado.tc.codegen;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import uk.ac.manchester.tornado.tc.check.ExternalTool;
import uk.ac.manchester.tornado.tc.check.Lit;
import uk.ac.manchester.tornado.tc.codegen.tools.EmitTool;

/**
 * Runs every {@code .tcir} file under {@code src/test/lit}. When {@code nvcc} is available, tests
 * marked {@code REQUIRES: nvcc} also compile the emitted CUDA to PTX for sm_89.
 */
@RunWith(Parameterized.class)
public class LitTest {

    private static final Path ROOT = Path.of("src", "test", "lit");
    private static final Lit LIT;

    static {
        var tools = new HashMap<String, Lit.Tool>();
        tools.put("tc-emit", (args, stdin) -> {
            var result = EmitTool.run(args, stdin);
            return new Lit.ToolResult(result.exitCode(), result.stdout(), result.stderr());
        });
        var features = new HashSet<String>();
        ExternalTool.find("nvcc", "/usr/local/cuda/bin").ifPresent(nvcc -> {
            features.add("nvcc");
            tools.put("nvcc-ptx", ExternalTool.of(".cu", List.of(nvcc.toString(), "--ptx", "-arch=sm_89", "-o", "/dev/null", "%in")));
        });
        LIT = new Lit(tools, features);
    }

    @Parameters(name = "{0}")
    public static List<Object[]> files() {
        return Lit.discover(ROOT, ".tcir");
    }

    @Parameter(0)
    public String name;

    @Parameter(1)
    public Path file;

    @Test
    public void run() {
        var outcome = LIT.run(file);
        assumeFalse(outcome.log(), outcome.unsupported());
        assertTrue(name + "\n" + outcome.log(), outcome.passed());
    }
}
