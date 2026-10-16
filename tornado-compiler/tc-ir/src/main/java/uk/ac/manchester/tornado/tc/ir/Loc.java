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
 * Source location of an instruction: Java source file, line, and bytecode index ({@code -1} when
 * unknown).
 */
public record Loc(String file, int line, int bci) {

    public static final Loc UNKNOWN = new Loc("", -1, -1);

    public boolean isKnown() {
        return line >= 0 || bci >= 0;
    }

    public String text() {
        var sb = new StringBuilder("loc(\"").append(file).append("\":").append(line);
        if (bci >= 0) {
            sb.append(", bci=").append(bci);
        }
        return sb.append(')').toString();
    }
}
