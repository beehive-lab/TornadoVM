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
 * Memory address spaces. The numbers follow the NVPTX convention so that exported LLVM IR text needs
 * no remapping; other dialects map them in their printers.
 */
public enum AddressSpace {
    GLOBAL("global", 1),
    SHARED("shared", 3),
    CONSTANT("constant", 4),
    PRIVATE("private", 5);

    private final String text;
    private final int number;

    AddressSpace(String text, int number) {
        this.text = text;
        this.number = number;
    }

    public String text() {
        return text;
    }

    public int number() {
        return number;
    }

    public static AddressSpace fromText(String text) {
        for (var space : values()) {
            if (space.text.equals(text)) {
                return space;
            }
        }
        throw new IllegalArgumentException("unknown address space: " + text);
    }
}
