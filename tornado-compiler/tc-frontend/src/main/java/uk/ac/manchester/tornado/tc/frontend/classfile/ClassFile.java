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
import java.util.function.Function;

/**
 * A parsed class file (chapter 4 of The Java Virtual Machine Specification). Only what the TC
 * frontend needs is kept: the constant pool, the methods with their {@code Code} attribute, line
 * numbers, local variable names and the type annotations on locals (where {@code @Parallel} lives).
 *
 * <p>Two readers produce it ({@link Reader}): TC's own, which accepts any class-file version and
 * skips unknown attributes, and on JDK 24 and newer the JDK's Class-File API (JEP 484). The
 * frontend sees the same model, with the class file's own constant-pool indices, from both.
 */
public record ClassFile(String name, Optional<String> superName, int majorVersion, ConstantPool constants, List<Method> methods, List<BootstrapMethod> bootstrapMethods) {

    /** One entry of the {@code BootstrapMethods} attribute: the method handle and its static arguments. */
    public record BootstrapMethod(int methodHandle, List<Integer> arguments) {
    }

    /** A method and, unless abstract or native, its code. */
    public record Method(String name, String descriptor, int access, Optional<Code> code) {
        public boolean isStatic() {
            return (access & 0x0008) != 0;
        }
    }

    /**
     * The instructions of a {@code Code} attribute and the type annotations on its locals, decoded on
     * first use: a class is parsed whole, but only the methods TC compiles are decoded.
     */
    public interface Body {
        List<Bytecode.Insn> insns();

        List<TypeAnnotations.LocalAnnotation> localAnnotations();
    }

    /** The {@code Code} attribute of a method. */
    public record Code(int maxStack, int maxLocals, Body body, List<Handler> handlers, List<LineNumber> lines, List<LocalVariable> locals) {

        /** The decoded instructions, in bci order. */
        public List<Bytecode.Insn> insns() {
            return body.insns();
        }

        /** Annotations on local variables ({@code @Parallel}, {@code @Reduce}). */
        public List<TypeAnnotations.LocalAnnotation> localAnnotations() {
            return body.localAnnotations();
        }

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

    /** The class-file readers. */
    public enum Reader {
        /** TC's own reader, written from the JVM specification. Every JDK, any class-file version. */
        TC,
        /** The JDK's Class-File API ({@code java.lang.classfile}, JEP 484): JDK 24 and newer. */
        JDK;

        /** Whether this reader can run here: {@link #JDK} needs JDK 24+ and a build that includes it. */
        public boolean isAvailable() {
            return this == TC || JdkReader.INSTANCE != null;
        }
    }

    /**
     * Parses {@code bytes}; throws {@link ClassFormatException} for malformed input. With
     * {@code -Dtornado.tc.classfile=auto} (the default) the JDK's reader is used where available, and a
     * class it refuses (a class-file version newer than the running JDK, for example) is read by TC's
     * own; {@code jdk} or {@code tc} selects one reader only.
     */
    public static ClassFile parse(byte[] bytes) {
        return switch (SELECTED) {
            case "tc" -> parse(bytes, Reader.TC);
            case "jdk" -> parse(bytes, Reader.JDK);
            default -> {
                if (JdkReader.INSTANCE == null) {
                    yield parse(bytes, Reader.TC);
                }
                try {
                    yield parse(bytes, Reader.JDK);
                } catch (ClassFormatException e) {
                    yield parse(bytes, Reader.TC);
                }
            }
        };
    }

    /** The reader {@link #parse(byte[])} uses first: {@code -Dtornado.tc.classfile}, or JDK where available. */
    public static Reader reader() {
        return switch (SELECTED) {
            case "tc" -> Reader.TC;
            case "jdk" -> Reader.JDK;
            default -> JdkReader.INSTANCE != null ? Reader.JDK : Reader.TC;
        };
    }

    /**
     * How class files are read, for reports: the JDK's reader (with TC's own for the classes it refuses,
     * under {@code auto}), or one reader only.
     */
    public static String readerDescription() {
        return switch (SELECTED) {
            case "tc" -> "TC reader";
            case "jdk" -> "JDK reader";
            default -> JdkReader.INSTANCE != null ? "JDK reader, TC reader for classes it refuses" : "TC reader (the JDK reader needs JDK 24+)";
        };
    }

    /** Parses {@code bytes} with {@code reader}. */
    public static ClassFile parse(byte[] bytes, Reader reader) {
        if (reader == Reader.JDK) {
            if (JdkReader.INSTANCE == null) {
                throw new UnsupportedOperationException("the Class-File API reader needs JDK 24 or newer and a TC build made with JDK 24 or newer");
            }
            return JdkReader.INSTANCE.apply(bytes);
        }
        try {
            return read(new DataInputStream(new ByteArrayInputStream(bytes)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final String SELECTED = System.getProperty("tornado.tc.classfile", "auto");

    /**
     * The Class-File API reader, compiled from {@code src/main/java24} with {@code --release 24} when
     * TC is built on JDK 24 or newer. It is looked up by name so that this class still loads on JDK 21.
     * A native image needs the constructor registered for reflection; tc-frontend's
     * {@code META-INF/native-image} metadata does that, and without it TC falls back to its own reader.
     */
    private static final class JdkReader {
        static final Function<byte[], ClassFile> INSTANCE = load();

        @SuppressWarnings("unchecked")
        private static Function<byte[], ClassFile> load() {
            if (Runtime.version().feature() < 24) {
                return null;
            }
            try {
                return (Function<byte[], ClassFile>) Class.forName("uk.ac.manchester.tornado.tc.frontend.classfile.JdkClassFileReader").getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            } catch (Error e) {
                // A native image without the reflection metadata throws an Error of its own here.
                if (e instanceof VirtualMachineError) {
                    throw e;
                }
                return null;
            }
        }
    }

    private static ClassFile read(DataInputStream in) throws IOException {
        if (in.readInt() != 0xCAFEBABE) {
            throw new ClassFormatException("not a class file (bad magic number)");
        }
        in.readUnsignedShort(); // minor version
        int major = in.readUnsignedShort();
        var pool = ClassFileConstantPool.read(in);
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
            } else {
                in.skipNBytes(length);
            }
        }
        return new ClassFile(name, superName, major, pool, List.copyOf(methods), List.copyOf(bootstraps));
    }

    private static Code readCode(DataInputStream in, ClassFileConstantPool pool) throws IOException {
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
        return new Code(maxStack, maxLocals, new RawBody(bytes, typeAnnotations, pool), List.copyOf(handlers), List.copyOf(lines), List.copyOf(locals));
    }

    /** TC's own decoding of the code bytes and the {@code RuntimeVisibleTypeAnnotations} attribute. */
    private static final class RawBody implements Body {
        private final byte[] bytes;
        private final byte[] typeAnnotations;
        private final ConstantPool pool;
        private volatile List<Bytecode.Insn> insns;
        private volatile List<TypeAnnotations.LocalAnnotation> localAnnotations;

        RawBody(byte[] bytes, byte[] typeAnnotations, ConstantPool pool) {
            this.bytes = bytes;
            this.typeAnnotations = typeAnnotations;
            this.pool = pool;
        }

        @Override
        public List<Bytecode.Insn> insns() {
            var result = insns;
            if (result == null) {
                result = List.copyOf(Bytecode.decode(bytes));
                insns = result;
            }
            return result;
        }

        @Override
        public List<TypeAnnotations.LocalAnnotation> localAnnotations() {
            var result = localAnnotations;
            if (result == null) {
                result = List.copyOf(TypeAnnotations.locals(typeAnnotations, pool));
                localAnnotations = result;
            }
            return result;
        }
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.readUnsignedShort();
            in.skipNBytes(in.readInt());
        }
    }
}
