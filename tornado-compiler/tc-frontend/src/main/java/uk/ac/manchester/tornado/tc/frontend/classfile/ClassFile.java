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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A parsed class file, read directly from the format defined in chapter 4 of The Java Virtual
 * Machine Specification. Only what the TC frontend needs is kept: the constant pool, the methods
 * with their {@code Code} attribute, line numbers, local variable names and raw type annotations
 * (where {@code @Parallel} lives), and the types of the class's own runtime-visible annotations
 * (kotlinc marks its classes with {@code kotlin.Metadata}). Any class-file version is accepted:
 * unknown attributes are skipped.
 */
public record ClassFile(String name, Optional<String> superName, int majorVersion, ConstantPool constants, List<Method> methods, List<BootstrapMethod> bootstrapMethods,
        List<String> annotations) {

    /** One entry of the {@code BootstrapMethods} attribute: the method handle and its static arguments. */
    public record BootstrapMethod(int methodHandle, List<Integer> arguments) {
    }

    /** A method and, unless abstract or native, its code. */
    public record Method(String name, String descriptor, int access, Optional<Code> code) {
        public boolean isStatic() {
            return (access & 0x0008) != 0;
        }
    }

    /** The {@code Code} attribute of a method. */
    public record Code(int maxStack, int maxLocals, byte[] bytes, List<Handler> handlers, List<LineNumber> lines, List<LocalVariable> locals, byte[] typeAnnotations) {

        /** Source line of the instruction at {@code bci}, or {@code -1}. */
        public int lineAt(int bci) {
            int line = -1;
            int best = -1;
            for (var entry : lines) {
                if (entry.startBci() <= bci && entry.startBci() > best) {
                    best = entry.startBci();
                    line = entry.line();
                }
            }
            return line;
        }

        /** The local variable in {@code slot} that is live at {@code bci}, if the class was compiled with -g. */
        public Optional<LocalVariable> localAt(int slot, int bci) {
            return locals.stream().filter(v -> v.slot() == slot && bci >= v.startBci() && bci < v.startBci() + v.length()).findFirst();
        }
    }

    public record Handler(int startBci, int endBci, int handlerBci, int catchType) {
    }

    public record LineNumber(int startBci, int line) {
    }

    public record LocalVariable(int startBci, int length, String name, String descriptor, int slot) {
    }

    public Optional<Method> method(String name, String descriptor) {
        return methods.stream().filter(m -> m.name().equals(name) && (descriptor == null || m.descriptor().equals(descriptor))).findFirst();
    }

    /** Parses {@code bytes}; throws {@link ClassFormatException} for malformed input. */
    public static ClassFile parse(byte[] bytes) {
        try {
            return read(new DataInputStream(new ByteArrayInputStream(bytes)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ClassFile read(DataInputStream in) throws IOException {
        if (in.readInt() != 0xCAFEBABE) {
            throw new ClassFormatException("not a class file (bad magic number)");
        }
        in.readUnsignedShort(); // minor version
        int major = in.readUnsignedShort();
        var pool = ConstantPool.read(in);
        in.readUnsignedShort(); // access flags
        var name = pool.className(in.readUnsignedShort());
        int superIndex = in.readUnsignedShort();
        Optional<String> superName = superIndex == 0 ? Optional.empty() : Optional.of(pool.className(superIndex));
        int interfaces = in.readUnsignedShort();
        for (int i = 0; i < interfaces; i++) {
            in.readUnsignedShort();
        }
        int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) {
            in.readUnsignedShort();
            in.readUnsignedShort();
            in.readUnsignedShort();
            skipAttributes(in);
        }
        int count = in.readUnsignedShort();
        var methods = new ArrayList<Method>(count);
        for (int i = 0; i < count; i++) {
            int access = in.readUnsignedShort();
            var methodName = pool.utf8(in.readUnsignedShort());
            var descriptor = pool.utf8(in.readUnsignedShort());
            Optional<Code> code = Optional.empty();
            int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                var attributeName = pool.utf8(in.readUnsignedShort());
                int length = in.readInt();
                if (attributeName.equals("Code")) {
                    code = Optional.of(readCode(in, pool));
                } else {
                    in.skipNBytes(length);
                }
            }
            methods.add(new Method(methodName, descriptor, access, code));
        }
        var bootstraps = new ArrayList<BootstrapMethod>();
        var annotations = new ArrayList<String>();
        int classAttributes = in.readUnsignedShort();
        for (int a = 0; a < classAttributes; a++) {
            var attributeName = pool.utf8(in.readUnsignedShort());
            int length = in.readInt();
            if (attributeName.equals("BootstrapMethods")) {
                int n = in.readUnsignedShort();
                for (int i = 0; i < n; i++) {
                    int handle = in.readUnsignedShort();
                    int argc = in.readUnsignedShort();
                    var arguments = new ArrayList<Integer>();
                    for (int k = 0; k < argc; k++) {
                        arguments.add(in.readUnsignedShort());
                    }
                    bootstraps.add(new BootstrapMethod(handle, List.copyOf(arguments)));
                }
            } else if (attributeName.equals("RuntimeVisibleAnnotations")) {
                int n = in.readUnsignedShort();
                for (int i = 0; i < n; i++) {
                    annotations.add(readAnnotation(in, pool));
                }
            } else {
                in.skipNBytes(length);
            }
        }
        return new ClassFile(name, superName, major, pool, List.copyOf(methods), List.copyOf(bootstraps), List.copyOf(annotations));
    }

    /** Reads one annotation and returns its type descriptor; its element values are skipped. */
    private static String readAnnotation(DataInputStream in, ConstantPool pool) throws IOException {
        var type = pool.utf8(in.readUnsignedShort());
        int pairs = in.readUnsignedShort();
        for (int i = 0; i < pairs; i++) {
            in.readUnsignedShort(); // element name
            skipElementValue(in, pool);
        }
        return type;
    }

    private static void skipElementValue(DataInputStream in, ConstantPool pool) throws IOException {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case 'e' -> in.skipNBytes(4);
            case '@' -> readAnnotation(in, pool);
            case '[' -> {
                int n = in.readUnsignedShort();
                for (int i = 0; i < n; i++) {
                    skipElementValue(in, pool);
                }
            }
            default -> in.skipNBytes(2); // a constant or a class: one constant-pool index
        }
    }

    private static Code readCode(DataInputStream in, ConstantPool pool) throws IOException {
        int maxStack = in.readUnsignedShort();
        int maxLocals = in.readUnsignedShort();
        var bytes = in.readNBytes(in.readInt());
        int handlerCount = in.readUnsignedShort();
        var handlers = new ArrayList<Handler>();
        for (int i = 0; i < handlerCount; i++) {
            handlers.add(new Handler(in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort()));
        }
        var lines = new ArrayList<LineNumber>();
        var locals = new ArrayList<LocalVariable>();
        byte[] typeAnnotations = new byte[0];
        int attributes = in.readUnsignedShort();
        for (int a = 0; a < attributes; a++) {
            var name = pool.utf8(in.readUnsignedShort());
            int length = in.readInt();
            switch (name) {
                case "LineNumberTable" -> {
                    int n = in.readUnsignedShort();
                    for (int i = 0; i < n; i++) {
                        lines.add(new LineNumber(in.readUnsignedShort(), in.readUnsignedShort()));
                    }
                }
                case "LocalVariableTable" -> {
                    int n = in.readUnsignedShort();
                    for (int i = 0; i < n; i++) {
                        locals.add(new LocalVariable(in.readUnsignedShort(), in.readUnsignedShort(), pool.utf8(in.readUnsignedShort()), pool.utf8(in.readUnsignedShort()), in.readUnsignedShort()));
                    }
                }
                case "RuntimeVisibleTypeAnnotations" -> typeAnnotations = in.readNBytes(length);
                default -> in.skipNBytes(length);
            }
        }
        return new Code(maxStack, maxLocals, bytes, List.copyOf(handlers), List.copyOf(lines), List.copyOf(locals), typeAnnotations);
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.readUnsignedShort();
            in.skipNBytes(in.readInt());
        }
    }
}
