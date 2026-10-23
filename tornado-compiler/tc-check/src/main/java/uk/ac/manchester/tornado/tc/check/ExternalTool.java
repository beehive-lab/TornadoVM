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
