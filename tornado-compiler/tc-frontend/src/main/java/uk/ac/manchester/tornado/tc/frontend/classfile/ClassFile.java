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

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.PoolEntry;
import java.util.List;
import java.util.Optional;

/**
 * A parsed class file, read with the JDK's {@code java.lang.classfile} API. Only what the TC frontend
 * needs is kept: the constant pool, the methods with their {@code Code} attribute, line numbers, local
 * variable names and the annotations on local variables (where {@code @Parallel} lives). Any class-file
 * version the running JDK can load is accepted.
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

    /** The {@code Code} attribute of a method. */
    public record Code(int maxStack, int maxLocals, byte[] bytes, List<Handler> handlers, List<LineNumber> lines, List<LocalVariable> locals, List<TypeAnnotations.LocalAnnotation> typeAnnotations) {

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
            return read(java.lang.classfile.ClassFile.of().parse(bytes));
        } catch (IllegalArgumentException e) {
            throw new ClassFormatException(e.getMessage());
        }
    }

    private static ClassFile read(ClassModel model) {
        var methods = model.methods().stream().map(ClassFile::method).toList();
        var bootstraps = model.findAttribute(Attributes.bootstrapMethods()).stream() //
                .flatMap(a -> a.bootstrapMethods().stream()) //
                .map(b -> new BootstrapMethod(b.bootstrapMethod().index(), b.arguments().stream().map(PoolEntry::index).toList())) //
                .toList();
        var superName = model.superclass().map(c -> c.asInternalName());
        return new ClassFile(model.thisClass().asInternalName(), superName, model.majorVersion(), new ConstantPool(model.constantPool()), methods, bootstraps);
    }

    private static Method method(MethodModel m) {
        var code = m.findAttribute(Attributes.code()).map(ClassFile::code);
        return new Method(m.methodName().stringValue(), m.methodType().stringValue(), m.flags().flagsMask(), code);
    }

    private static Code code(CodeAttribute code) {
        var handlers = code.exceptionHandlers().stream() //
                .map(h -> new Handler(code.labelToBci(h.tryStart()), code.labelToBci(h.tryEnd()), code.labelToBci(h.handler()), h.catchType().map(PoolEntry::index).orElse(0))) //
                .toList();
        var lines = code.findAttributes(Attributes.lineNumberTable()).stream() //
                .flatMap(a -> a.lineNumbers().stream()) //
                .map(l -> new LineNumber(l.startPc(), l.lineNumber())) //
                .toList();
        var locals = code.findAttributes(Attributes.localVariableTable()).stream() //
                .flatMap(a -> a.localVariables().stream()) //
                .map(v -> new LocalVariable(v.startPc(), v.length(), v.name().stringValue(), v.type().stringValue(), v.slot())) //
                .toList();
        return new Code(code.maxStack(), code.maxLocals(), code.codeArray(), handlers, lines, locals, TypeAnnotations.locals(code));
    }
}
