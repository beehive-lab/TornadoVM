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

    private record MethodHandleRef(int kind, int reference) implements Entry {
    }

    private record InvokeDynamicRef(int bootstrap, int nameAndType) implements Entry {
    }

    private record Other(int tag) implements Entry {
    }

    /** An {@code invokedynamic} site: bootstrap-method index, name and descriptor. */
    public record DynamicSite(int bootstrap, String name, String descriptor) {
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
                case 15 -> new MethodHandleRef(in.readUnsignedByte(), in.readUnsignedShort());
                case 16, 19, 20 -> {
                    in.readUnsignedShort();
                    yield new Other(tag);
                }
                case 18 -> new InvokeDynamicRef(in.readUnsignedShort(), in.readUnsignedShort());
                case 17 -> {
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

    /** The method or field a {@code CONSTANT_MethodHandle} refers to, if {@code index} is one. */
    public java.util.Optional<MemberRef> methodHandle(int index) {
        return entries[index] instanceof MethodHandleRef(var kind, var reference) ? java.util.Optional.of(member(reference)) : java.util.Optional.empty();
    }

    public DynamicSite dynamicSite(int index) {
        if (entries[index] instanceof InvokeDynamicRef(var bootstrap, var nameAndType) && entries[nameAndType] instanceof NameAndType(var name, var descriptor)) {
            return new DynamicSite(bootstrap, utf8(name), utf8(descriptor));
        }
        throw new ClassFormatException("constant " + index + " is not an invokedynamic site");
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
