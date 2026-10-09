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
package uk.ac.manchester.tornado.tc.frontend.classfile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the {@code RuntimeVisibleTypeAnnotations} attribute of a {@code Code} attribute (JVMS
 * §4.7.20) and returns the annotations on local variables, which is where {@code @Parallel} and
 * {@code @Reduce} on loop indices and accumulators live. Other targets are parsed and skipped.
 */
public final class TypeAnnotations {

    /** An annotation on local variable {@code slot}, live for {@code [startBci, startBci + length)}. */
    public record LocalAnnotation(String descriptor, int slot, int startBci, int length) {
        public boolean covers(int bci) {
            return bci >= startBci && bci < startBci + length;
        }
    }

    private TypeAnnotations() {
    }

    public static List<LocalAnnotation> locals(byte[] raw, ConstantPool pool) {
        var result = new ArrayList<LocalAnnotation>();
        if (raw.length == 0) {
            return result;
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(raw))) {
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                int target = in.readUnsignedByte();
                record Range(int start, int length, int slot) {
                }
                var ranges = new ArrayList<Range>();
                switch (target) {
                    case 0x40, 0x41 -> { // localvar_target, resource_variable
                        int n = in.readUnsignedShort();
                        for (int k = 0; k < n; k++) {
                            ranges.add(new Range(in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort()));
                        }
                    }
                    case 0x42, 0x43, 0x44, 0x45, 0x46 -> in.readUnsignedShort(); // catch, offset targets
                    case 0x47, 0x48, 0x49, 0x4A, 0x4B -> { // type_argument_target
                        in.readUnsignedShort();
                        in.readUnsignedByte();
                    }
                    default -> throw new ClassFormatException("unexpected type-annotation target 0x" + Integer.toHexString(target) + " in a Code attribute");
                }
                int pathLength = in.readUnsignedByte();
                in.skipNBytes(2L * pathLength);
                var descriptor = pool.utf8(in.readUnsignedShort());
                skipElementValuePairs(in);
                for (var r : ranges) {
                    result.add(new LocalAnnotation(descriptor, r.slot(), r.start(), r.length()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private static void skipElementValuePairs(DataInputStream in) throws IOException {
        int pairs = in.readUnsignedShort();
        for (int p = 0; p < pairs; p++) {
            in.readUnsignedShort();
            skipElementValue(in);
        }
    }

    private static void skipElementValue(DataInputStream in) throws IOException {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's', 'c' -> in.readUnsignedShort();
            case 'e' -> {
                in.readUnsignedShort();
                in.readUnsignedShort();
            }
            case '@' -> {
                in.readUnsignedShort();
                skipElementValuePairs(in);
            }
            case '[' -> {
                int n = in.readUnsignedShort();
                for (int i = 0; i < n; i++) {
                    skipElementValue(in);
                }
            }
            default -> throw new ClassFormatException("unknown element-value tag '" + (char) tag + "'");
        }
    }
}
