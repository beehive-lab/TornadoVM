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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A pattern checker for compiler output, implementing the directive language described in LLVM's
 * public FileCheck documentation (no LLVM code is used): {@code CHECK}, {@code CHECK-NEXT},
 * {@code CHECK-SAME}, {@code CHECK-NOT}, {@code CHECK-DAG}, {@code CHECK-LABEL} and {@code CHECK-EMPTY},
 * with {@code {{regex}}} blocks, {@code [[VAR:regex]]} definitions and {@code [[VAR]]} uses, and custom
 * prefixes. Runs of horizontal whitespace in a pattern match any run of horizontal whitespace.
 */
public final class FileCheck {

    /** Outcome of a check; {@code message} explains the first failure in LLVM's style. */
    public record Result(boolean passed, String message) {
    }

    private enum Kind {
        PLAIN(""), NEXT("-NEXT"), SAME("-SAME"), NOT("-NOT"), DAG("-DAG"), LABEL("-LABEL"), EMPTY("-EMPTY");

        final String suffix;

        Kind(String suffix) {
            this.suffix = suffix;
        }
    }

    private record Directive(Kind kind, String prefix, String pattern, int checkLine) {
        String spelling() {
            return prefix + kind.suffix;
        }
    }

    private record Pos(int line, int column) implements Comparable<Pos> {
        @Override
        public int compareTo(Pos other) {
            return line != other.line ? Integer.compare(line, other.line) : Integer.compare(column, other.column);
        }
    }

    private record Match(int line, int start, int end) {
        Pos after() {
            return new Pos(line, end);
        }

        Pos before() {
            return new Pos(line, start);
        }
    }

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final String checkName;
    private final List<String> input;
    private final Map<String, String> variables = new HashMap<>();

    private FileCheck(String checkName, String input) {
        this.checkName = checkName;
        this.input = List.of(input.split("\n", -1));
    }

    /**
     * Checks {@code input} against the directives found in {@code checkText}.
     *
     * @param checkName name used in messages (usually the check file)
     */
    public static Result check(String checkName, String checkText, String input, List<String> prefixes) {
        List<Directive> directives;
        try {
            directives = parseDirectives(checkText, prefixes);
        } catch (IllegalArgumentException e) {
            return new Result(false, checkName + ": error: " + e.getMessage());
        }
        if (directives.isEmpty()) {
            return new Result(false, checkName + ": error: no check strings found with prefix " + String.join(", ", prefixes.stream().map(p -> p + ":").toList()));
        }
        var checker = new FileCheck(checkName, input);
        try {
            checker.run(directives);
            return new Result(true, "");
        } catch (CheckFailure failure) {
            return new Result(false, failure.getMessage());
        }
    }

    public static Result check(String checkName, String checkText, String input) {
        return check(checkName, checkText, input, List.of("CHECK"));
    }

    // ---------------------------------------------------------------- directive parsing

    private static List<Directive> parseDirectives(String checkText, List<String> prefixes) {
        var alternatives = String.join("|", prefixes.stream().map(Pattern::quote).toList());
        var directive = Pattern.compile("(?<![A-Za-z0-9_-])(" + alternatives + ")(-NEXT|-SAME|-NOT|-DAG|-LABEL|-EMPTY)?:(.*)$");
        var result = new ArrayList<Directive>();
        var lines = checkText.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            var m = directive.matcher(lines[i]);
            if (!m.find()) {
                continue;
            }
            var kind = switch (m.group(2) == null ? "" : m.group(2)) {
                case "-NEXT" -> Kind.NEXT;
                case "-SAME" -> Kind.SAME;
                case "-NOT" -> Kind.NOT;
                case "-DAG" -> Kind.DAG;
                case "-LABEL" -> Kind.LABEL;
                case "-EMPTY" -> Kind.EMPTY;
                default -> Kind.PLAIN;
            };
            var pattern = m.group(3).strip();
            if (pattern.isEmpty() && kind != Kind.EMPTY) {
                throw new IllegalArgumentException("line " + (i + 1) + ": found empty check string with prefix '" + m.group(1) + kind.suffix + ":'");
            }
            if (kind == Kind.EMPTY && !pattern.isEmpty()) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + m.group(1) + "-EMPTY takes no pattern");
            }
            result.add(new Directive(kind, m.group(1), pattern, i + 1));
        }
        return result;
    }

    // ---------------------------------------------------------------- matching

    private void run(List<Directive> directives) {
        // Labels split the input into independent regions: find them first, in order.
        var labelIndices = new ArrayList<Integer>();
        var labelMatches = new ArrayList<Match>();
        var cursor = new Pos(0, 0);
        for (int i = 0; i < directives.size(); i++) {
            var d = directives.get(i);
            if (d.kind == Kind.LABEL) {
                var m = find(compile(d), cursor, end());
                if (m == null) {
                    throw failure(d, "expected string not found in input", cursor);
                }
                labelIndices.add(i);
                labelMatches.add(m);
                cursor = m.after();
            }
        }
        int from = 0;
        var regionStart = new Pos(0, 0);
        for (int k = 0; k <= labelIndices.size(); k++) {
            int to = k < labelIndices.size() ? labelIndices.get(k) : directives.size();
            var regionEnd = k < labelMatches.size() ? labelMatches.get(k).before() : end();
            Match previous = k == 0 ? null : labelMatches.get(k - 1);
            runRegion(directives.subList(from, to), regionStart, regionEnd, previous);
            if (k < labelMatches.size()) {
                regionStart = labelMatches.get(k).after();
                from = to + 1;
            }
        }
    }

    private void runRegion(List<Directive> directives, Pos start, Pos regionEnd, Match previousMatch) {
        var pos = start;
        var previous = previousMatch;
        var nots = new ArrayList<Directive>();
        int i = 0;
        while (i < directives.size()) {
            var d = directives.get(i);
            switch (d.kind) {
                case NOT -> {
                    nots.add(d);
                    i++;
                }
                case DAG -> {
                    var group = new ArrayList<Directive>();
                    while (i < directives.size() && directives.get(i).kind == Kind.DAG) {
                        group.add(directives.get(i++));
                    }
                    Pos earliest = null;
                    Pos furthest = pos;
                    Match last = previous;
                    for (var dag : group) {
                        var m = find(compile(dag), pos, regionEnd);
                        if (m == null) {
                            throw failure(dag, "expected string not found in input", pos);
                        }
                        if (earliest == null || m.before().compareTo(earliest) < 0) {
                            earliest = m.before();
                        }
                        if (m.after().compareTo(furthest) > 0) {
                            furthest = m.after();
                            last = m;
                        }
                    }
                    checkNots(nots, pos, earliest);
                    nots.clear();
                    pos = furthest;
                    previous = last;
                }
                case EMPTY -> {
                    var line = previous == null ? pos.line : previous.line + 1;
                    if (previous == null || line >= input.size() || !input.get(line).isBlank()) {
                        throw failure(d, "found non-empty line after previous match", new Pos(Math.min(line, input.size() - 1), 0));
                    }
                    checkNots(nots, pos, new Pos(line, 0));
                    nots.clear();
                    previous = new Match(line, 0, 0);
                    pos = new Pos(line, 0);
                    i++;
                }
                default -> {
                    var pattern = compile(d);
                    Match m;
                    if (d.kind == Kind.SAME) {
                        if (previous == null) {
                            throw failure(d, "found '" + d.spelling() + "' without previous 'CHECK' line", pos);
                        }
                        m = find(pattern, pos, new Pos(previous.line, input.get(previous.line).length()));
                        if (m == null) {
                            throw failure(d, "expected string not found on the same line as the previous match", pos);
                        }
                    } else {
                        m = find(pattern, pos, regionEnd);
                        if (m == null) {
                            throw failure(d, "expected string not found in input", pos);
                        }
                        if (d.kind == Kind.NEXT) {
                            if (previous == null) {
                                throw failure(d, "found '" + d.spelling() + "' without previous 'CHECK' line", pos);
                            }
                            if (m.line != previous.line + 1) {
                                throw failure(d, "is not on the line after the previous match (found on input line " + (m.line + 1) + ")", new Pos(m.line, 0));
                            }
                        }
                    }
                    checkNots(nots, pos, m.before());
                    nots.clear();
                    pos = m.after();
                    previous = m;
                    i++;
                }
            }
        }
        checkNots(nots, pos, regionEnd);
    }

    private void checkNots(List<Directive> nots, Pos from, Pos to) {
        for (var not : nots) {
            var m = find(compile(not), from, to);
            if (m != null) {
                throw failure(not, "excluded string found in input", new Pos(m.line, m.start));
            }
        }
    }

    private Match find(Pattern pattern, Pos from, Pos to) {
        for (int line = from.line; line <= to.line && line < input.size(); line++) {
            var text = input.get(line);
            int start = line == from.line ? Math.min(from.column, text.length()) : 0;
            int end = line == to.line ? Math.min(to.column, text.length()) : text.length();
            if (start > end) {
                continue;
            }
            Matcher m = pattern.matcher(text);
            m.region(start, end);
            m.useAnchoringBounds(false);
            if (m.find()) {
                recordVariables(pattern, m);
                return new Match(line, m.start(), m.end());
            }
        }
        return null;
    }

    private Pos end() {
        var last = input.size() - 1;
        return new Pos(last, input.get(last).length());
    }

    // ---------------------------------------------------------------- patterns and variables

    private final Map<Pattern, List<String>> captures = new HashMap<>();

    /** Translates a directive pattern into a Java regex, substituting variable uses. */
    private Pattern compile(Directive d) {
        var regex = new StringBuilder();
        var defined = new ArrayList<String>();
        var text = d.pattern;
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("{{", i)) {
                int close = text.indexOf("}}", i + 2);
                if (close < 0) {
                    throw failure(d, "unterminated '{{' regex", null);
                }
                regex.append("(?:").append(text, i + 2, close).append(')');
                i = close + 2;
            } else if (text.startsWith("[[", i)) {
                int close = text.indexOf("]]", i + 2);
                if (close < 0) {
                    throw failure(d, "unterminated '[[' variable", null);
                }
                var body = text.substring(i + 2, close);
                int colon = body.indexOf(':');
                if (colon >= 0) {
                    var name = body.substring(0, colon);
                    if (!VARIABLE_NAME.matcher(name).matches()) {
                        throw failure(d, "invalid variable name '" + name + "'", null);
                    }
                    defined.add(name);
                    regex.append("(?<v").append(defined.size()).append('>').append(body.substring(colon + 1)).append(')');
                } else {
                    var value = variables.get(body);
                    if (value == null) {
                        throw failure(d, "use of undefined variable '" + body + "'", null);
                    }
                    regex.append(Pattern.quote(value));
                }
                i = close + 2;
            } else {
                int next = nextSpecial(text, i);
                regex.append(literal(text.substring(i, next)));
                i = next;
            }
        }
        try {
            var pattern = Pattern.compile(regex.toString());
            captures.put(pattern, defined);
            return pattern;
        } catch (PatternSyntaxException e) {
            throw failure(d, "invalid regex: " + e.getDescription(), null);
        }
    }

    private void recordVariables(Pattern pattern, Matcher m) {
        var names = captures.getOrDefault(pattern, List.of());
        for (int k = 0; k < names.size(); k++) {
            variables.put(names.get(k), m.group("v" + (k + 1)));
        }
    }

    private static int nextSpecial(String text, int from) {
        int a = text.indexOf("{{", from);
        int b = text.indexOf("[[", from);
        int next = text.length();
        if (a >= 0) {
            next = Math.min(next, a);
        }
        if (b >= 0) {
            next = Math.min(next, b);
        }
        return next;
    }

    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[ \t]+");

    /** Quotes literal text, letting each run of spaces or tabs match any run of spaces or tabs. */
    private static String literal(String text) {
        var sb = new StringBuilder();
        var m = HORIZONTAL_SPACE.matcher(text);
        int last = 0;
        while (m.find()) {
            sb.append(Pattern.quote(text.substring(last, m.start()))).append("[ \\t]+");
            last = m.end();
        }
        return sb.append(Pattern.quote(text.substring(last))).toString();
    }

    // ---------------------------------------------------------------- failures

    private static final class CheckFailure extends RuntimeException {
        CheckFailure(String message) {
            super(message, null, false, false);
        }
    }

    private CheckFailure failure(Directive d, String what, Pos scanFrom) {
        var sb = new StringBuilder();
        sb.append(checkName).append(':').append(d.checkLine).append(": error: ").append(d.spelling()).append(": ").append(what).append('\n');
        sb.append(d.spelling()).append(": ").append(d.pattern).append('\n');
        if (scanFrom != null && !input.isEmpty()) {
            int line = Math.max(0, Math.min(scanFrom.line, input.size() - 1));
            sb.append("<input>:").append(line + 1).append(": note: scanning from here\n");
            for (int k = line; k < Math.min(input.size(), line + 6); k++) {
                sb.append(String.format("%5d | %s%n", k + 1, input.get(k)));
            }
            if (!variables.isEmpty()) {
                sb.append("variables: ").append(variables).append('\n');
            }
        }
        return new CheckFailure(sb.toString());
    }
}
