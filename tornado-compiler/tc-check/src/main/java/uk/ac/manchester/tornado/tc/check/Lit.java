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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs text test files in the style of LLVM's lit (behaviour from its public documentation): each
 * file carries {@code RUN:} lines (after {@code ;}, {@code //} or {@code #}) whose commands are
 * pipelines of registered in-process tools. Supported: {@code %s} (test file), {@code %S} (its
 * directory), {@code %t} (a temporary path), {@code |} pipes with pipefail semantics, a leading
 * {@code not} that inverts a stage's exit status, {@code 2>&1}, and {@code \} line continuation.
 * {@code FileCheck} is built in.
 */
public final class Lit {

    /** An in-process tool, the equivalent of an LLVM tool binary. */
    @FunctionalInterface
    public interface Tool {
        ToolResult run(List<String> args, String stdin);
    }

    public record ToolResult(int exitCode, String stdout, String stderr) {
        public static ToolResult ok(String stdout) {
            return new ToolResult(0, stdout, "");
        }

        public static ToolResult failure(String stdout, String stderr) {
            return new ToolResult(1, stdout, stderr);
        }
    }

    /** Outcome of one test file; {@code log} records every command and, on failure, why. */
    public record Outcome(boolean passed, boolean unsupported, String log) {
        Outcome(boolean passed, String log) {
            this(passed, false, log);
        }
    }

    private static final Pattern RUN_LINE = Pattern.compile("^\\s*(?:;|//|#)\\s*RUN:\\s?(.*)$");
    private static final Pattern REQUIRES_LINE = Pattern.compile("^\\s*(?:;|//|#)\\s*REQUIRES:\\s*(.*)$");

    private final Map<String, Tool> tools = new HashMap<>();
    private final java.util.Set<String> features;
    private final java.util.Set<String> absentTools;

    public Lit(Map<String, Tool> tools) {
        this(tools, java.util.Set.of());
    }

    /** {@code features}: names a test can demand with {@code REQUIRES:} (for example {@code nvcc}). */
    public Lit(Map<String, Tool> tools, java.util.Set<String> features) {
        this(tools, features, java.util.Set.of());
    }

    /**
     * {@code absentTools}: tools a RUN line may name but this machine lacks (a CUDA compiler on a Mac,
     * Apple's Metal compiler on Linux). A RUN line using one is skipped and logged; the file's other
     * RUN lines still run.
     */
    public Lit(Map<String, Tool> tools, java.util.Set<String> features, java.util.Set<String> absentTools) {
        this.tools.putAll(tools);
        this.tools.put("FileCheck", Lit::fileCheck);
        this.features = java.util.Set.copyOf(features);
        this.absentTools = java.util.Set.copyOf(absentTools);
    }

    /**
     * All files below {@code dir} with one of {@code extensions}, sorted, as JUnit parameters. Files
     * under an {@code Inputs} directory are auxiliary inputs, not tests (as in LLVM).
     */
    public static List<Object[]> discover(Path dir, String... extensions) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> Stream.of(extensions).anyMatch(e -> p.toString().endsWith(e)))
                    .filter(p -> !dir.relativize(p).toString().replace('\\', '/').matches("(.*/)?Inputs/.*"))
                    .sorted()
                    .map(p -> new Object[] { dir.relativize(p).toString(), p })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Outcome run(Path testFile) {
        var log = new StringBuilder();
        List<String> commands;
        try {
            commands = runLines(Files.readString(testFile));
        } catch (IOException e) {
            return new Outcome(false, "cannot read " + testFile + ": " + e.getMessage());
        }
        if (commands.isEmpty()) {
            return new Outcome(false, testFile + ": no RUN lines found");
        }
        try {
            for (var line : Files.readAllLines(testFile)) {
                var m = REQUIRES_LINE.matcher(line);
                if (m.matches()) {
                    for (var feature : m.group(1).split(",")) {
                        if (!features.contains(feature.strip())) {
                            return new Outcome(true, true, "UNSUPPORTED: requires " + feature.strip());
                        }
                    }
                }
            }
        } catch (IOException e) {
            return new Outcome(false, "cannot read " + testFile + ": " + e.getMessage());
        }
        Path temp;
        try {
            temp = Files.createTempFile("tc-lit-", ".tmp");
        } catch (IOException e) {
            return new Outcome(false, "cannot create a temporary file: " + e.getMessage());
        }
        try {
            for (var command : commands) {
                var expanded = command.replace("%s", testFile.toString()).replace("%S", testFile.getParent().toString()).replace("%t", temp.toString());
                log.append("RUN: ").append(expanded).append('\n');
                var absent = splitPipeline(expanded).stream().map(Lit::tokenize).filter(a -> !a.isEmpty()).map(a -> a.get(0).equals("not") && a.size() > 1 ? a.get(1) : a.get(0))
                        .filter(absentTools::contains).findFirst();
                if (absent.isPresent()) {
                    log.append("  skipped: '").append(absent.get()).append("' is not available here\n");
                    continue;
                }
                if (!runPipeline(expanded, log)) {
                    return new Outcome(false, log.toString());
                }
            }
            return new Outcome(true, log.toString());
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static List<String> runLines(String text) {
        var commands = new ArrayList<String>();
        var current = new StringBuilder();
        for (var line : text.split("\\R")) {
            var m = RUN_LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            var body = m.group(1).stripTrailing();
            if (body.endsWith("\\")) {
                current.append(body, 0, body.length() - 1).append(' ');
                continue;
            }
            current.append(body);
            commands.add(current.toString().strip());
            current.setLength(0);
        }
        return commands;
    }

    private boolean runPipeline(String command, StringBuilder log) {
        var stdin = "";
        for (var stage : splitPipeline(command)) {
            var args = tokenize(stage);
            boolean invert = false;
            boolean mergeStderr = false;
            if (!args.isEmpty() && args.get(0).equals("not")) {
                invert = true;
                args.remove(0);
            }
            if (args.remove("2>&1")) {
                mergeStderr = true;
            }
            if (args.isEmpty()) {
                log.append("  error: empty pipeline stage in: ").append(command).append('\n');
                return false;
            }
            var tool = tools.get(args.get(0));
            if (tool == null) {
                log.append("  error: unknown tool '").append(args.get(0)).append("'; known: ").append(tools.keySet()).append('\n');
                return false;
            }
            ToolResult result;
            try {
                result = tool.run(List.copyOf(args.subList(1, args.size())), stdin);
            } catch (RuntimeException e) {
                result = new ToolResult(2, "", "tool '" + args.get(0) + "' crashed: " + e);
            }
            boolean failed = invert ? result.exitCode == 0 : result.exitCode != 0;
            if (failed) {
                log.append("  stage '").append(stage.strip()).append("' ").append(invert ? "succeeded but was expected to fail" : "failed with exit code " + result.exitCode).append('\n');
                appendIndented(log, "stdout", result.stdout);
                appendIndented(log, "stderr", result.stderr);
                return false;
            }
            stdin = mergeStderr ? result.stdout + result.stderr : result.stdout;
        }
        return true;
    }

    private static void appendIndented(StringBuilder log, String name, String text) {
        if (!text.isEmpty()) {
            log.append("  --- ").append(name).append(" ---\n");
            text.lines().forEach(l -> log.append("  ").append(l).append('\n'));
        }
    }

    private static List<String> splitPipeline(String command) {
        var stages = new ArrayList<String>();
        var current = new StringBuilder();
        char quote = 0;
        for (char c : command.toCharArray()) {
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                current.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                current.append(c);
            } else if (c == '|') {
                stages.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        stages.add(current.toString());
        return stages;
    }

    private static List<String> tokenize(String stage) {
        var tokens = new ArrayList<String>();
        var current = new StringBuilder();
        char quote = 0;
        boolean inToken = false;
        for (char c : stage.toCharArray()) {
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** {@code FileCheck <check-file> [--check-prefix=P | --check-prefixes=A,B] [--input-file=F]}. */
    private static ToolResult fileCheck(List<String> args, String stdin) {
        String checkFile = null;
        String input = stdin;
        var prefixes = new ArrayList<String>();
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (arg.startsWith("--check-prefix=")) {
                prefixes.add(arg.substring("--check-prefix=".length()));
            } else if (arg.equals("--check-prefix") && i + 1 < args.size()) {
                prefixes.add(args.get(++i));
            } else if (arg.startsWith("--check-prefixes=")) {
                prefixes.addAll(List.of(arg.substring("--check-prefixes=".length()).split(",")));
            } else if (arg.startsWith("--input-file=")) {
                try {
                    input = Files.readString(Path.of(arg.substring("--input-file=".length())));
                } catch (IOException e) {
                    return ToolResult.failure("", "FileCheck: cannot read input: " + e.getMessage());
                }
            } else if (arg.startsWith("-")) {
                return ToolResult.failure("", "FileCheck: unknown option " + arg);
            } else {
                checkFile = arg;
            }
        }
        if (checkFile == null) {
            return ToolResult.failure("", "FileCheck: no check file given");
        }
        if (prefixes.isEmpty()) {
            prefixes.add("CHECK");
        }
        String checkText;
        try {
            checkText = Files.readString(Path.of(checkFile));
        } catch (IOException e) {
            return ToolResult.failure("", "FileCheck: cannot read " + checkFile + ": " + e.getMessage());
        }
        var result = FileCheck.check(Path.of(checkFile).getFileName().toString(), checkText, input, prefixes);
        return result.passed() ? ToolResult.ok("") : ToolResult.failure("", result.message());
    }
}
