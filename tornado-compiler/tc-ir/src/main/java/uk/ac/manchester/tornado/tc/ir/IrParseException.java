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

/** A syntax or name-resolution error in TC-SSA text, with its position. */
public final class IrParseException extends RuntimeException {

    private final transient Diagnostic diagnostic;
    private final int line;
    private final int column;

    IrParseException(String code, String message, int line, int column) {
        super(code + " at " + line + ":" + column + ": " + message);
        this.diagnostic = new Diagnostic(code, message, "column " + column, Loc.UNKNOWN, line);
        this.line = line;
        this.column = column;
    }

    public Diagnostic diagnostic() {
        return diagnostic;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}
