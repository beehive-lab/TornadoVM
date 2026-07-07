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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.runtime.jvmci.reflection.ClassfileParser.MethodCode;
import uk.ac.manchester.tornado.runtime.jvmci.reflection.ClassfileParser.RawHandler;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Reflection + ASM-backed {@link ResolvedJavaMethod}. Structural facts (name,
 * declaring class, signature, modifiers, flags) come from
 * {@link java.lang.reflect.Executable}. Bytecode-derived data (code, code size,
 * max locals/stack, exception handlers, constant pool, line/local tables) is
 * marked {@code REFLECTION-TODO} and will be sourced from ASM classfile parsing
 * (already a TornadoVM dependency) / Graal's {@code ClassfileBytecodeProvider}
 * in subsequent bring-up iterations.
 */
final class ReflectionResolvedJavaMethod implements ResolvedJavaMethod {

    private final ReflectionUniverse universe;
    private final Executable executable;
    private final boolean isConstructor;

    /**
     * For signature-polymorphic call sites ({@code VarHandle}/{@code MethodHandle}),
     * the call-site JVM descriptor from the constant pool; {@code null} otherwise.
     */
    private final String polymorphicDescriptor;

    // Lazily parsed Code attribute; sentinel below distinguishes "not parsed".
    private MethodCode code;
    private boolean codeParsed;
    private ConstantPool constantPool;

    ReflectionResolvedJavaMethod(ReflectionUniverse universe, Executable executable) {
        this(universe, executable, null);
    }

    ReflectionResolvedJavaMethod(ReflectionUniverse universe, Executable executable, String polymorphicDescriptor) {
        this.universe = universe;
        this.executable = executable;
        this.isConstructor = executable instanceof Constructor;
        this.polymorphicDescriptor = polymorphicDescriptor;
    }

    /** JVM method descriptor, e.g. {@code (I[I)V}. */
    private String methodDescriptor() {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : executable.getParameterTypes()) {
            sb.append(ReflectionUniverse.descriptor(p));
        }
        sb.append(')');
        sb.append(isConstructor ? "V" : ReflectionUniverse.descriptor(((Method) executable).getReturnType()));
        return sb.toString();
    }

    /**
     * Lazily parse the declaring class's classfile (JDK-neutral, no HotSpot
     * JVMCI) to recover this method's {@code Code} attribute: max stack/locals,
     * raw bytecode and exception table.
     */
    private MethodCode code() {
        if (!codeParsed) {
            code = loadCode();
            codeParsed = true;
        }
        return code;
    }

    private MethodCode loadCode() {
        return ClassfileParser.parse(classfileBytes(), getName(), methodDescriptor());
    }

    private byte[] classfileBytes() {
        Class<?> declaring = executable.getDeclaringClass();
        String internalName = declaring.getName().replace('.', '/');
        ClassLoader loader = declaring.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                throw new IllegalStateException("classfile not found for " + declaring.getName());
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("failed reading classfile for " + declaring.getName(), e);
        }
    }

    // ---- structural facts from reflection ----

    @Override
    public String getName() {
        return isConstructor ? "<init>" : executable.getName();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return universe.lookupType(executable.getDeclaringClass());
    }

    @Override
    public Signature getSignature() {
        if (polymorphicDescriptor != null) {
            ClassLoader loader = executable.getDeclaringClass().getClassLoader();
            return new DescriptorSignature(universe, loader != null ? loader : ClassLoader.getSystemClassLoader(), polymorphicDescriptor);
        }
        return new ReflectionSignature(universe, executable);
    }

    @Override
    public int getModifiers() {
        return executable.getModifiers();
    }

    @Override
    public boolean isSynthetic() {
        return executable.isSynthetic();
    }

    @Override
    public boolean isVarArgs() {
        return executable.isVarArgs();
    }

    @Override
    public boolean isBridge() {
        return executable instanceof Method m && m.isBridge();
    }

    @Override
    public boolean isDefault() {
        return executable instanceof Method m && m.isDefault();
    }

    @Override
    public boolean isClassInitializer() {
        return false;
    }

    @Override
    public boolean isConstructor() {
        return isConstructor;
    }

    @Override
    public boolean canBeStaticallyBound() {
        int mods = executable.getModifiers();
        return isConstructor || Modifier.isStatic(mods) || Modifier.isPrivate(mods) || Modifier.isFinal(mods) || Modifier.isFinal(executable.getDeclaringClass().getModifiers());
    }

    @Override
    public boolean canBeInlined() {
        return true;
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return false;
    }

    @Override
    public boolean shouldBeInlined() {
        return false;
    }

    @Override
    public Type[] getGenericParameterTypes() {
        return executable.getGenericParameterTypes();
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        return executable.getParameterAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return executable.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return executable.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return executable.getDeclaredAnnotations();
    }

    @Override
    public Parameter[] getParameters() {
        java.lang.reflect.Parameter[] reflectionParameters = executable.getParameters();
        Parameter[] out = new Parameter[reflectionParameters.length];
        for (int i = 0; i < reflectionParameters.length; i++) {
            java.lang.reflect.Parameter p = reflectionParameters[i];
            out[i] = new Parameter(p.getName(), p.getModifiers(), this, i);
        }
        return out;
    }

    @Override
    public void reprofile() {
        // no-op: TornadoVM does not use runtime profiling for kernel compilation
    }

    // ---- bytecode-derived: REFLECTION-TODO (ASM classfile parsing) ----

    @Override
    public byte[] getCode() {
        MethodCode c = code();
        return c == null ? null : c.code();
    }

    @Override
    public int getCodeSize() {
        MethodCode c = code();
        return c == null ? 0 : c.code().length;
    }

    @Override
    public int getMaxLocals() {
        MethodCode c = code();
        return c == null ? 0 : c.maxLocals();
    }

    @Override
    public int getMaxStackSize() {
        MethodCode c = code();
        return c == null ? 0 : c.maxStack();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        MethodCode c = code();
        if (c == null || c.handlers().length == 0) {
            return new ExceptionHandler[0];
        }
        ExceptionHandler[] out = new ExceptionHandler[c.handlers().length];
        for (int i = 0; i < out.length; i++) {
            RawHandler h = c.handlers()[i];
            JavaType catchType = null;
            if (h.catchTypeName() != null) {
                try {
                    Class<?> catchClass = Class.forName(h.catchTypeName().replace('/', '.'), false, executable.getDeclaringClass().getClassLoader());
                    catchType = universe.lookupType(catchClass);
                } catch (ClassNotFoundException e) {
                    catchType = null;
                }
            }
            out[i] = new ExceptionHandler(h.startBCI(), h.endBCI(), h.handlerBCI(), h.catchTypeCPI(), catchType);
        }
        return out;
    }

    @Override
    public ConstantPool getConstantPool() {
        if (constantPool == null) {
            constantPool = new ReflectionConstantPool(universe, executable.getDeclaringClass(), classfileBytes());
        }
        return constantPool;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return null;
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        // The reflection classfile path does not parse the optional (debug-only) LocalVariableTable
        // attribute, but backends need one to name kernel parameters (locals live at BCI 0). Synthesise
        // a table from the method parameters: stable identifiers (this/arg0/arg1/...) with the correct
        // types and JVM local slots — semantically sufficient for code generation.
        int endBci = Math.max(getCodeSize(), 1);
        List<Local> locals = new ArrayList<>();
        int slot = 0;
        if (!Modifier.isStatic(executable.getModifiers())) {
            locals.add(new Local("this", getDeclaringClass(), 0, endBci, slot));
            slot += 1;
        }
        Class<?>[] parameterTypes = executable.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            JavaType type = universe.lookupType(parameterTypes[i]);
            locals.add(new Local("arg" + i, type, 0, endBci, slot));
            slot += JavaKind.fromJavaClass(parameterTypes[i]).getSlotCount();
        }
        return new LocalVariableTable(locals.toArray(new Local[0]));
    }

    @Override
    public Constant getEncoding() {
        throw ReflectionUniverse.todo("ResolvedJavaMethod.getEncoding");
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return new StackTraceElement(executable.getDeclaringClass().getName(), getName(), null, -1);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return new ReflectionProfilingInfo(getCodeSize());
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw ReflectionUniverse.todo("ResolvedJavaMethod.isInVirtualMethodTable");
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw ReflectionUniverse.todo("ResolvedJavaMethod.getSpeculationLog");
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ReflectionResolvedJavaMethod other && executable.equals(other.executable);
    }

    @Override
    public int hashCode() {
        return executable.hashCode();
    }

    @Override
    public String toString() {
        // Match the JVMCI-canonical "<holder>.<name>(<params>)" form (like HotSpotMethod's %H.%n(%p)):
        // TornadoVM's vector/half-float NodePlugins match on method.toString().contains("FloatArray.<init>(int)")
        // etc., so the "<init>" and parenthesised parameter list must be present or private-vector/HalfFloat
        // construction is inlined into the (bodiless) native allocator instead of being intrinsified.
        StringBuilder sb = new StringBuilder("ReflectionMethod<");
        sb.append(executable.getDeclaringClass().getName()).append('.').append(getName()).append('(');
        Class<?>[] parameterTypes = executable.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameterTypes[i].getSimpleName());
        }
        sb.append(")>");
        return sb.toString();
    }
}
