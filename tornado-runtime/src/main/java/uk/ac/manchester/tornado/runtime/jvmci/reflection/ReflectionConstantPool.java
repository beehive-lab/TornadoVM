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
package uk.ac.manchester.tornado.runtime.jvmci.reflection;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Executable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import uk.ac.manchester.tornado.runtime.jvmci.TornadoObjectConstant;

/**
 * Reflection-backed {@link ConstantPool}. It parses the declaring class's raw
 * classfile constant pool and resolves method/field/type references via
 * reflection. This is the JDK-neutral replacement for HotSpot's constant pool.
 *
 * <p>
 * Because {@link ReflectionResolvedJavaMethod#getCode()} returns the raw
 * classfile bytecode (no HotSpot constant-pool rewriting), the constant-pool
 * indices in that bytecode index this raw classfile CP directly.
 */
final class ReflectionConstantPool implements ConstantPool {

    private static final int CP_UTF8 = 1;
    private static final int CP_INTEGER = 3;
    private static final int CP_FLOAT = 4;
    private static final int CP_LONG = 5;
    private static final int CP_DOUBLE = 6;
    private static final int CP_CLASS = 7;
    private static final int CP_STRING = 8;
    private static final int CP_FIELDREF = 9;
    private static final int CP_METHODREF = 10;
    private static final int CP_INTERFACE_METHODREF = 11;
    private static final int CP_NAME_AND_TYPE = 12;
    private static final int CP_METHOD_HANDLE = 15;
    private static final int CP_METHOD_TYPE = 16;
    private static final int CP_DYNAMIC = 17;
    private static final int CP_INVOKE_DYNAMIC = 18;
    private static final int CP_MODULE = 19;
    private static final int CP_PACKAGE = 20;

    private final ReflectionUniverse universe;
    private final Class<?> declaringClass;
    private final ClassLoader loader;

    private final int count;
    private final int[] tag;
    private final String[] utf8;
    private final int[] refA; // Class: name_index; Ref: class_index; NameAndType: name_index; String/MethodType: index
    private final int[] refB; // Ref: name_and_type_index; NameAndType: descriptor_index
    private final long[] primitive; // Integer/Float(as int bits)/Long/Double bits
    // Cache resolved Class per CP index: the same class ref is resolved repeatedly during a method's graph build.
    private final Map<Integer, Class<?>> resolvedClasses = new ConcurrentHashMap<>();

    ReflectionConstantPool(ReflectionUniverse universe, Class<?> declaringClass, byte[] classfile) {
        this.universe = universe;
        this.declaringClass = declaringClass;
        ClassLoader cl = declaringClass.getClassLoader();
        this.loader = cl != null ? cl : ClassLoader.getSystemClassLoader();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classfile))) {
            in.readInt(); // magic
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            this.count = in.readUnsignedShort();
            this.tag = new int[count];
            this.utf8 = new String[count];
            this.refA = new int[count];
            this.refB = new int[count];
            this.primitive = new long[count];
            for (int i = 1; i < count; i++) {
                int t = in.readUnsignedByte();
                tag[i] = t;
                switch (t) {
                    case CP_UTF8 -> utf8[i] = in.readUTF();
                    case CP_INTEGER, CP_FLOAT -> primitive[i] = in.readInt();
                    case CP_LONG, CP_DOUBLE -> {
                        primitive[i] = in.readLong();
                        i++; // occupies two slots
                    }
                    case CP_CLASS, CP_STRING, CP_METHOD_TYPE, CP_MODULE, CP_PACKAGE -> refA[i] = in.readUnsignedShort();
                    case CP_FIELDREF, CP_METHODREF, CP_INTERFACE_METHODREF, CP_NAME_AND_TYPE, CP_DYNAMIC, CP_INVOKE_DYNAMIC -> {
                        refA[i] = in.readUnsignedShort();
                        refB[i] = in.readUnsignedShort();
                    }
                    case CP_METHOD_HANDLE -> {
                        in.readUnsignedByte();
                        refA[i] = in.readUnsignedShort();
                    }
                    default -> throw new IllegalStateException("Unknown CP tag " + t + " at " + i);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed parsing constant pool of " + declaringClass.getName(), e);
        }
    }

    @Override
    public int length() {
        return count;
    }

    @Override
    public String lookupUtf8(int cpi) {
        return utf8[cpi];
    }

    private String classNameAt(int classCpi) {
        return utf8[refA[classCpi]];
    }

    private Class<?> resolveClass(int classCpi) {
        return resolvedClasses.computeIfAbsent(classCpi, this::doResolveClass);
    }

    private Class<?> doResolveClass(int classCpi) {
        String internal = classNameAt(classCpi);
        try {
            // Both plain class names and array names ("[L...;") map to the binary name by replacing '/' with '.'.
            String name = internal.replace('/', '.');
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to resolve class " + internal, e);
        }
    }

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        int t = tag[cpi];
        if (t == CP_METHODREF || t == CP_INTERFACE_METHODREF || t == CP_FIELDREF) {
            resolveClass(refA[cpi]);
        } else if (t == CP_CLASS) {
            resolveClass(cpi);
        }
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        return universe.lookupType(resolveClass(cpi));
    }

    @Override
    public JavaType lookupReferencedType(int cpi, int opcode) {
        int t = tag[cpi];
        if (t == CP_CLASS) {
            return universe.lookupType(resolveClass(cpi));
        }
        return universe.lookupType(resolveClass(refA[cpi]));
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        Class<?> holder = resolveClass(refA[cpi]);
        int nt = refB[cpi];
        String name = utf8[refA[nt]];
        String descriptor = utf8[refB[nt]];
        Executable executable = ReflectionMembers.findMethod(holder, name, descriptor, loader);
        if (executable != null) {
            return universe.lookupMethod(executable);
        }
        // Signature-polymorphic call site (VarHandle/MethodHandle): the declared
        // method takes Object..., but the invoke uses the call-site descriptor.
        Executable polymorphic = ReflectionMembers.findPolymorphic(holder, name);
        if (polymorphic != null) {
            return universe.lookupPolymorphicMethod(polymorphic, descriptor);
        }
        throw new IllegalStateException("Unable to resolve method " + holder.getName() + "." + name + descriptor);
    }

    @Override
    public JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode) {
        Class<?> holder = resolveClass(refA[cpi]);
        int nt = refB[cpi];
        String name = utf8[refA[nt]];
        java.lang.reflect.Field field = ReflectionMembers.findField(holder, name);
        if (field != null) {
            return universe.lookupField(field);
        }
        // The JDK reflection-filters some sensitive internal fields (e.g.
        // System.security); read their metadata straight from the classfile.
        ClassfileParser.FieldInfo info = ClassfileParser.findFieldInfo(universe.classfileBytes(holder), name);
        if (info != null) {
            return new ClassfileResolvedJavaField(universe, holder, info, loader);
        }
        throw new IllegalStateException("Unable to resolve field " + holder.getName() + "." + name);
    }

    @Override
    public Object lookupConstant(int cpi) {
        return switch (tag[cpi]) {
            case CP_INTEGER -> JavaConstant.forInt((int) primitive[cpi]);
            case CP_FLOAT -> JavaConstant.forFloat(Float.intBitsToFloat((int) primitive[cpi]));
            case CP_LONG -> JavaConstant.forLong(primitive[cpi]);
            case CP_DOUBLE -> JavaConstant.forDouble(Double.longBitsToDouble(primitive[cpi]));
            case CP_STRING -> new TornadoObjectConstant(utf8[refA[cpi]]);
            case CP_CLASS -> universe.lookupType(resolveClass(cpi));
            default -> throw ReflectionUniverse.todo("ConstantPool.lookupConstant(tag=" + tag[cpi] + ")");
        };
    }

    @Override
    public Signature lookupSignature(int cpi) {
        throw ReflectionUniverse.todo("ConstantPool.lookupSignature");
    }

    @Override
    public JavaConstant lookupAppendix(int cpi, int opcode) {
        // Only meaningful for invokedynamic/method-handle call sites, unused in kernels.
        return null;
    }
}
