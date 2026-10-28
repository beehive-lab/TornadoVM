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

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.Optional;

/**
 * The constant pool of a class file (JVMS section 4.4), looked up by the indices that bytecode operands carry.
 * A thin view over the {@code java.lang.classfile} pool.
 */
public final class ConstantPool {

    /** A resolved member reference: owner class (internal name), member name and descriptor. */
    public record MemberRef(String owner, String name, String descriptor) {
    }

    /** An {@code invokedynamic} site: bootstrap-method index, name and descriptor. */
    public record DynamicSite(int bootstrap, String name, String descriptor) {
    }

    private final java.lang.classfile.constantpool.ConstantPool pool;

    ConstantPool(java.lang.classfile.constantpool.ConstantPool pool) {
        this.pool = pool;
    }

    private PoolEntry entry(int index) {
        try {
            return pool.entryByIndex(index);
        } catch (IllegalArgumentException e) {
            throw new ClassFormatException("bad constant-pool index " + index + ": " + e.getMessage());
        }
    }

    public String utf8(int index) {
        if (entry(index) instanceof Utf8Entry utf8) {
            return utf8.stringValue();
        }
        throw new ClassFormatException("constant " + index + " is not UTF-8");
    }

    public String className(int index) {
        if (entry(index) instanceof ClassEntry c) {
            return c.asInternalName();
        }
        throw new ClassFormatException("constant " + index + " is not a class");
    }

    public MemberRef member(int index) {
        if (entry(index) instanceof MemberRefEntry m) {
            return memberRef(m);
        }
        throw new ClassFormatException("constant " + index + " is not a field or method reference");
    }

    /** The method or field a {@code CONSTANT_MethodHandle} refers to, if {@code index} is one. */
    public Optional<MemberRef> methodHandle(int index) {
        return entry(index) instanceof MethodHandleEntry h ? Optional.of(memberRef(h.reference())) : Optional.empty();
    }

    public DynamicSite dynamicSite(int index) {
        if (entry(index) instanceof InvokeDynamicEntry d) {
            return new DynamicSite(d.bootstrapMethodIndex(), d.name().stringValue(), d.type().stringValue());
        }
        throw new ClassFormatException("constant " + index + " is not an invokedynamic site");
    }

    /** A loadable constant ({@code ldc}): Integer, Float, Long, Double or String. */
    public Object loadable(int index) {
        return switch (entry(index)) {
            case IntegerEntry e -> e.intValue();
            case FloatEntry e -> e.floatValue();
            case LongEntry e -> e.longValue();
            case DoubleEntry e -> e.doubleValue();
            case StringEntry e -> e.stringValue();
            default -> throw new ClassFormatException("constant " + index + " is not loadable by ldc in a kernel");
        };
    }

    private static MemberRef memberRef(MemberRefEntry m) {
        return new MemberRef(m.owner().asInternalName(), m.name().stringValue(), m.type().stringValue());
    }
}
