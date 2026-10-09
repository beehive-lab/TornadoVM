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
package uk.ac.manchester.tornado.tc.ir;

/**
 * A compiler diagnostic with a stable code ({@code TC-E1xxx} frontend, {@code E2xxx} IR and verifier,
 * {@code E3xxx} TornadoVM lowering and structuring, {@code E4xxx} target, {@code E9xxx} internal).
 *
 * @param code     stable identifier, for example {@code TC-E2003}
 * @param message  what is wrong, in one sentence
 * @param where    the offending construct in context (an instruction's text, or a source position)
 * @param loc      Java source location when known
 * @param line     line in the IR text the construct was parsed from, or {@code -1}
 */
public record Diagnostic(String code, String message, String where, Loc loc, int line) {

    public Diagnostic(String code, String message, String where, Loc loc) {
        this(code, message, where, loc, -1);
    }

    public String render() {
        var sb = new StringBuilder(line >= 0 ? "line " + line + ": " : "").append("error ").append(code).append(": ").append(message);
        if (!where.isEmpty()) {
            sb.append("\n    ").append(where);
        }
        if (loc.isKnown()) {
            sb.append("\n    at ").append(loc.file()).append(':').append(loc.line());
            if (loc.bci() >= 0) {
                sb.append(" (bci ").append(loc.bci()).append(')');
            }
        }
        return sb.toString();
    }
}
