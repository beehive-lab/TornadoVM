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
package uk.ac.manchester.tornado.tc.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Wraps an external program (for example {@code nvcc}, {@code opt}, {@code mlir-opt}) as a lit tool.
 * The tool's standard input is written to a temporary file with the given suffix, and {@code %in} in
 * the command is replaced by its path. TC never links such programs; they only check its output.
 */
public final class ExternalTool {

    private ExternalTool() {
    }

    /** A tool running {@code command} (with {@code %in} for the input file) plus the lit arguments. */
    public static Lit.Tool of(String suffix, List<String> command) {
        return (args, stdin) -> {
            try {
                var input = Files.createTempFile("tc-ext-", suffix);
                try {
                    Files.writeString(input, stdin);
                    var full = new ArrayList<String>();
                    command.forEach(c -> full.add(c.replace("%in", input.toString())));
                    full.addAll(args);
                    var process = new ProcessBuilder(full).redirectErrorStream(false).start();
                    var out = new String(process.getInputStream().readAllBytes());
                    var err = new String(process.getErrorStream().readAllBytes());
                    if (!process.waitFor(120, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        return new Lit.ToolResult(124, out, err + "\ntimed out");
                    }
                    return new Lit.ToolResult(process.exitValue(), out, err);
                } finally {
                    Files.deleteIfExists(input);
                }
            } catch (IOException e) {
                return new Lit.ToolResult(127, "", "cannot run " + command.get(0) + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Lit.ToolResult(130, "", "interrupted");
            }
        };
    }

    /** The first executable named {@code program} on PATH or in {@code extraDirs}. */
    public static Optional<Path> find(String program, String... extraDirs) {
        var dirs = new ArrayList<String>(List.of(System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)));
        dirs.addAll(List.of(extraDirs));
        return dirs.stream().filter(d -> !d.isEmpty()).map(d -> Path.of(d, program)).filter(Files::isExecutable).findFirst();
    }
}
