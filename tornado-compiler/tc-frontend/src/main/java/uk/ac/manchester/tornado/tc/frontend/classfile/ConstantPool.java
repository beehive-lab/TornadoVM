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

import java.io.DataInputStream;
import java.io.IOException;

/**
 * The constant pool of a class file (JVMS §4.4). Entries are kept as small records; long and double
 * entries occupy two slots as the specification requires.
 */
public final class ConstantPool {

    /** A resolved member reference: owner class (internal name), member name and descriptor. */
    public record MemberRef(String owner, String name, String descriptor) {
    }

    private sealed interface Entry {
    }

    private record Utf8(String value) implements Entry {
    }

    private record IntValue(int value) implements Entry {
    }

    private record FloatValue(float value) implements Entry {
    }

    private record LongValue(long value) implements Entry {
    }

    private record DoubleValue(double value) implements Entry {
    }

    private record ClassRef(int name) implements Entry {
    }

    private record StringRef(int value) implements Entry {
    }

    private record Member(int owner, int nameAndType) implements Entry {
    }

    private record NameAndType(int name, int descriptor) implements Entry {
    }

    private record Other(int tag) implements Entry {
    }

    private final Entry[] entries;

    private ConstantPool(Entry[] entries) {
        this.entries = entries;
    }

    static ConstantPool read(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        var entries = new Entry[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            entries[i] = switch (tag) {
                case 1 -> new Utf8(in.readUTF());
                case 3 -> new IntValue(in.readInt());
                case 4 -> new FloatValue(in.readFloat());
                case 5 -> new LongValue(in.readLong());
                case 6 -> new DoubleValue(in.readDouble());
                case 7 -> new ClassRef(in.readUnsignedShort());
                case 8 -> new StringRef(in.readUnsignedShort());
                case 9, 10, 11 -> new Member(in.readUnsignedShort(), in.readUnsignedShort());
                case 12 -> new NameAndType(in.readUnsignedShort(), in.readUnsignedShort());
                case 15 -> {
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                    yield new Other(tag);
                }
                case 16, 19, 20 -> {
                    in.readUnsignedShort();
                    yield new Other(tag);
                }
                case 17, 18 -> {
                    in.readUnsignedShort();
                    in.readUnsignedShort();
                    yield new Other(tag);
                }
                default -> throw new ClassFormatException("unknown constant-pool tag " + tag + " at index " + i);
            };
            if (tag == 5 || tag == 6) {
                i++; // eight-byte constants take two entries
            }
        }
        return new ConstantPool(entries);
    }

    public String utf8(int index) {
        if (entries[index] instanceof Utf8(var value)) {
            return value;
        }
        throw new ClassFormatException("constant " + index + " is not UTF-8");
    }

    public String className(int index) {
        if (entries[index] instanceof ClassRef(var name)) {
            return utf8(name);
        }
        throw new ClassFormatException("constant " + index + " is not a class");
    }

    public MemberRef member(int index) {
        if (entries[index] instanceof Member(var owner, var nameAndType) && entries[nameAndType] instanceof NameAndType(var name, var descriptor)) {
            return new MemberRef(className(owner), utf8(name), utf8(descriptor));
        }
        throw new ClassFormatException("constant " + index + " is not a field or method reference");
    }

    /** A loadable constant ({@code ldc}): Integer, Float, Long, Double or String. */
    public Object loadable(int index) {
        return switch (entries[index]) {
            case IntValue(var v) -> v;
            case FloatValue(var v) -> v;
            case LongValue(var v) -> v;
            case DoubleValue(var v) -> v;
            case StringRef(var v) -> utf8(v);
            default -> throw new ClassFormatException("constant " + index + " is not loadable by ldc in a kernel");
        };
    }
}
