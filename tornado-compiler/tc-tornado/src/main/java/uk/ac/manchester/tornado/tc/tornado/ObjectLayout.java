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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.Map;

/**
 * Where the fields of an object argument are in its device copy. The TornadoVM runtime copies an
 * object field by field to the byte offsets the JVM uses for it; a reference field holds the offset of
 * the referenced object's device copy relative to the referencing object, divided by 8 (compressed
 * references). Primitive fields are read in place; a reference field {@code r} of object {@code o} is
 * {@code o + 8 * (u32 at o + offset(r))}.
 *
 * @param className the simple class name, as in {@code obj<Name>}
 * @param offsets byte offset of each field, by name (superclass fields included)
 * @param referenceClasses for fields that hold objects (not arrays), the simple class name of the object
 */
public record ObjectLayout(String className, Map<String, Integer> offsets, Map<String, String> referenceClasses) {

    /** Bytes a compressed reference is scaled by. */
    public static final int REFERENCE_SCALE = 8;
}
