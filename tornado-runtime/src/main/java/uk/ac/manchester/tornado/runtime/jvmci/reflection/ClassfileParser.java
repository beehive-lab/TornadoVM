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

/**
 * Minimal JVM classfile parser (JVMS §4) used to recover the {@code Code}
 * attribute of a single method: max stack, max locals, raw bytecode and the
 * exception table. This is the JDK-neutral source of bytecode-derived metadata
 * that HotSpot JVMCI normally provides, so TornadoVM can read what it needs
 * from the classfile bytes directly rather than from {@code jdk.vm.ci.hotspot}.
 */
final class ClassfileParser {

    /** Constant-pool tags (JVMS Table 4.4-B). */
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

    record RawHandler(int startBCI, int endBCI, int handlerBCI, int catchTypeCPI, String catchTypeName) {
    }

    record MethodCode(int maxStack, int maxLocals, byte[] code, RawHandler[] handlers) {
    }

    /** Field metadata read straight from the classfile (bypasses reflection filtering of sensitive fields). */
    record FieldInfo(String name, String descriptor, int accessFlags) {
    }

    private final String[] utf8;
    private final int[] classNameIndex;

    private ClassfileParser(String[] utf8, int[] classNameIndex) {
        this.utf8 = utf8;
        this.classNameIndex = classNameIndex;
    }

    /** Internal class name (e.g. {@code java/lang/Object}) for a CONSTANT_Class index, or null. */
    private String className(int cpIndex) {
        if (cpIndex == 0) {
            return null;
        }
        return utf8[classNameIndex[cpIndex]];
    }

    static MethodCode parse(byte[] bytes, String methodName, String methodDescriptor) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            in.readInt(); // magic 0xCAFEBABE
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major

            int cpCount = in.readUnsignedShort();
            String[] utf8 = new String[cpCount];
            int[] classNameIndex = new int[cpCount];
            for (int i = 1; i < cpCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case CP_UTF8 -> utf8[i] = in.readUTF();
                    case CP_CLASS -> classNameIndex[i] = in.readUnsignedShort();
                    case CP_STRING, CP_METHOD_TYPE, CP_MODULE, CP_PACKAGE -> in.readUnsignedShort();
                    case CP_INTEGER, CP_FLOAT -> in.readInt();
                    case CP_LONG, CP_DOUBLE -> {
                        in.readLong();
                        i++; // long/double occupy two constant-pool slots
                    }
                    case CP_FIELDREF, CP_METHODREF, CP_INTERFACE_METHODREF, CP_NAME_AND_TYPE, CP_DYNAMIC, CP_INVOKE_DYNAMIC -> {
                        in.readUnsignedShort();
                        in.readUnsignedShort();
                    }
                    case CP_METHOD_HANDLE -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    default -> throw new IllegalStateException("Unknown constant pool tag " + tag + " at " + i);
                }
            }
            ClassfileParser parser = new ClassfileParser(utf8, classNameIndex);

            in.readUnsignedShort(); // access_flags
            in.readUnsignedShort(); // this_class
            in.readUnsignedShort(); // super_class
            int interfaces = in.readUnsignedShort();
            in.skipBytes(interfaces * 2);

            skipMembers(in); // fields

            int methodCount = in.readUnsignedShort();
            for (int m = 0; m < methodCount; m++) {
                in.readUnsignedShort(); // access_flags
                String name = utf8[in.readUnsignedShort()];
                String descriptor = utf8[in.readUnsignedShort()];
                int attrCount = in.readUnsignedShort();
                boolean match = name.equals(methodName) && descriptor.equals(methodDescriptor);
                for (int a = 0; a < attrCount; a++) {
                    int attrName = in.readUnsignedShort();
                    int attrLen = in.readInt();
                    if (match && "Code".equals(utf8[attrName])) {
                        return parser.readCode(in);
                    }
                    in.skipBytes(attrLen);
                }
                if (match) {
                    return null; // abstract/native: no Code attribute
                }
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Failed parsing classfile", e);
        }
    }

    /**
     * Read a field's metadata directly from {@code holder}'s classfile, bypassing
     * reflection filtering (which hides sensitive JDK-internal fields such as
     * {@code System.security}).
     */
    static FieldInfo findFieldInfo(byte[] bytes, String fieldName) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            in.readInt();
            in.readUnsignedShort();
            in.readUnsignedShort();
            int cpCount = in.readUnsignedShort();
            String[] utf8 = new String[cpCount];
            for (int i = 1; i < cpCount; i++) {
                int t = in.readUnsignedByte();
                switch (t) {
                    case CP_UTF8 -> utf8[i] = in.readUTF();
                    case CP_CLASS, CP_STRING, CP_METHOD_TYPE, CP_MODULE, CP_PACKAGE -> in.readUnsignedShort();
                    case CP_INTEGER, CP_FLOAT -> in.readInt();
                    case CP_LONG, CP_DOUBLE -> {
                        in.readLong();
                        i++;
                    }
                    case CP_FIELDREF, CP_METHODREF, CP_INTERFACE_METHODREF, CP_NAME_AND_TYPE, CP_DYNAMIC, CP_INVOKE_DYNAMIC -> {
                        in.readUnsignedShort();
                        in.readUnsignedShort();
                    }
                    case CP_METHOD_HANDLE -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    default -> throw new IllegalStateException("Unknown constant pool tag " + t);
                }
            }
            in.readUnsignedShort(); // access_flags
            in.readUnsignedShort(); // this_class
            in.readUnsignedShort(); // super_class
            int interfaces = in.readUnsignedShort();
            in.skipBytes(interfaces * 2);
            int fieldCount = in.readUnsignedShort();
            for (int f = 0; f < fieldCount; f++) {
                int access = in.readUnsignedShort();
                String name = utf8[in.readUnsignedShort()];
                String descriptor = utf8[in.readUnsignedShort()];
                int attrCount = in.readUnsignedShort();
                for (int a = 0; a < attrCount; a++) {
                    in.readUnsignedShort();
                    in.skipBytes(in.readInt());
                }
                if (name.equals(fieldName)) {
                    return new FieldInfo(name, descriptor, access);
                }
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading field info", e);
        }
    }

    private MethodCode readCode(DataInputStream in) throws IOException {
        int maxStack = in.readUnsignedShort();
        int maxLocals = in.readUnsignedShort();
        int codeLength = in.readInt();
        byte[] code = new byte[codeLength];
        in.readFully(code);
        int handlerCount = in.readUnsignedShort();
        RawHandler[] handlers = new RawHandler[handlerCount];
        for (int i = 0; i < handlerCount; i++) {
            int start = in.readUnsignedShort();
            int end = in.readUnsignedShort();
            int handler = in.readUnsignedShort();
            int catchType = in.readUnsignedShort();
            handlers[i] = new RawHandler(start, end, handler, catchType, className(catchType));
        }
        // remaining Code sub-attributes are not needed
        return new MethodCode(maxStack, maxLocals, code, handlers);
    }

    private static void skipMembers(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.readUnsignedShort(); // access_flags
            in.readUnsignedShort(); // name_index
            in.readUnsignedShort(); // descriptor_index
            int attrCount = in.readUnsignedShort();
            for (int a = 0; a < attrCount; a++) {
                in.readUnsignedShort(); // attribute_name_index
                int len = in.readInt();
                in.skipBytes(len);
            }
        }
    }
}
