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
package uk.ac.manchester.tornado.tc.lower;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes the jumps that structured control flow makes redundant, so the emitted source reads like
 * hand-written code: a break at the end of the labelled block it leaves, a continue at the end of
 * the loop it restarts, and labelled blocks that no break targets. Runs to a fixed point.
 */
final class Simplifier {

    private Simplifier() {
    }

    static List<S.Stmt> simplify(List<S.Stmt> body) {
        var current = body;
        while (true) {
            var next = pass(current);
            if (next.equals(current)) {
                return next;
            }
            current = next;
        }
    }

    private static List<S.Stmt> pass(List<S.Stmt> body) {
        var withoutTails = rewrite(body);
        var targets = new HashSet<String>();
        collectBreakTargets(withoutTails, targets);
        return splice(withoutTails, targets);
    }

    /** Simplifies children first, then drops jumps in tail position that only fall through. */
    private static List<S.Stmt> rewrite(List<S.Stmt> body) {
        var out = new ArrayList<S.Stmt>();
        for (var stmt : body) {
            out.add(switch (stmt) {
                case S.If(var c, var t, var e) -> new S.If(c, rewrite(t), rewrite(e));
                case S.Loop(var label, var b) -> new S.Loop(label, dropTail(rewrite(b), new S.Continue(label)));
                case S.Labelled(var label, var b) -> new S.Labelled(label, dropTail(rewrite(b), new S.Break(label)));
                case S.Switch(var sel, var cases, var d) -> new S.Switch(sel, cases.stream().map(k -> new S.Case(k.key(), rewrite(k.body()))).toList(), rewrite(d));
                default -> stmt;
            });
        }
        return out;
    }

    /** Removes {@code jump} wherever it is the last statement executed before falling off {@code body}. */
    private static List<S.Stmt> dropTail(List<S.Stmt> body, S.Stmt jump) {
        if (body.isEmpty()) {
            return body;
        }
        var out = new ArrayList<>(body);
        var last = out.get(out.size() - 1);
        if (last.equals(jump)) {
            out.remove(out.size() - 1);
        } else if (last instanceof S.If(var c, var t, var e)) {
            out.set(out.size() - 1, new S.If(c, dropTail(t, jump), dropTail(e, jump)));
        } else if (last instanceof S.Labelled(var label, var b)) {
            // Falling off the end of a trailing labelled block also falls off the end of `body`.
            out.set(out.size() - 1, new S.Labelled(label, dropTail(b, jump)));
        }
        return out;
    }

    private static void collectBreakTargets(List<S.Stmt> body, Set<String> targets) {
        for (var stmt : body) {
            switch (stmt) {
                case S.Break(var label) -> targets.add(label);
                case S.If(var c, var t, var e) -> {
                    collectBreakTargets(t, targets);
                    collectBreakTargets(e, targets);
                }
                case S.Loop(var label, var b) -> collectBreakTargets(b, targets);
                case S.Labelled(var label, var b) -> collectBreakTargets(b, targets);
                case S.Switch(var sel, var cases, var d) -> {
                    cases.forEach(k -> collectBreakTargets(k.body(), targets));
                    collectBreakTargets(d, targets);
                }
                default -> {
                }
            }
        }
    }

    /** Inlines the body of every labelled block that no break targets. */
    private static List<S.Stmt> splice(List<S.Stmt> body, Set<String> targets) {
        var out = new ArrayList<S.Stmt>();
        for (var stmt : body) {
            switch (stmt) {
                case S.Labelled(var label, var b) when !targets.contains(label) -> out.addAll(splice(b, targets));
                case S.Labelled(var label, var b) -> out.add(new S.Labelled(label, splice(b, targets)));
                case S.If(var c, var t, var e) -> out.add(new S.If(c, splice(t, targets), splice(e, targets)));
                case S.Loop(var label, var b) -> out.add(new S.Loop(label, splice(b, targets)));
                case S.Switch(var sel, var cases, var d) -> out.add(new S.Switch(sel, cases.stream().map(k -> new S.Case(k.key(), splice(k.body(), targets))).toList(), splice(d, targets)));
                default -> out.add(stmt);
            }
        }
        return out;
    }
}
