/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ASMClassVisitorProvider;
import uk.ac.manchester.tornado.runtime.common.ParallelAnnotationProvider;

public class ASMClassVisitor extends ClassVisitor implements ASMClassVisitorProvider {
    private List<ParallelAnnotationProvider> parallelAnnotations;
    private ResolvedJavaMethod resolvedJavaMethod;

    public ASMClassVisitor() {
        super(Opcodes.ASM9);
    }

    public ASMClassVisitor(int i, ClassVisitor classVisitor, ResolvedJavaMethod resolvedJavaMethod) {
        super(i, classVisitor);
        parallelAnnotations = new ArrayList<>();
        this.resolvedJavaMethod = resolvedJavaMethod;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals(resolvedJavaMethod.getName()) && descriptor.equals(resolvedJavaMethod.getSignature().toMethodDescriptor())) {
            return new ASMMethodVisitor(api, cv.visitMethod(access, name, descriptor, signature, exceptions), parallelAnnotations);
        }
        return null;
    }

    // ASM's ClassReader only knows class-file versions up to the ASM release it ships with; a newer JDK class
    // on the class path (e.g. java.lang.Float is v71 on JDK 27) makes it throw. @Parallel-annotation extraction
    // is class-file-version independent, so cap the major version for the read.
    //
    // NOTE: this is the ASM parser's ceiling, NOT the highest JDK TornadoVM supports -- raising it past what the
    // bundled ASM understands is exactly what makes ClassReader throw. It is deliberately expressed as the
    // Opcodes constant rather than the literal 67 so it stays tied to the asm version in tornado-annotation/pom.xml:
    // dropping below that asm release stops compiling here instead of failing at run time.
    private static final int MAX_SUPPORTED_MAJOR = Opcodes.V23;

    /**
     * Finds a kernel's class file, trying the context class loader before the system one.
     *
     * <p>The system class loader alone is not enough for a kernel that was generated and compiled
     * at run time: such a class is loaded by whatever loader the generator created, which the
     * system loader cannot see. The failure is silent and expensive -- no annotations are found,
     * so {@code @Parallel} is dropped and the kernel is emitted as a sequential loop that every
     * device thread executes in full. The results are correct and the performance is not.
     */
    private static InputStream openClassFile(ResolvedJavaMethod method, String methodClassFile) {
        ClassLoader[] candidates = {
                declaringClassLoader(method),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader(),
                ASMClassVisitor.class.getClassLoader()
        };
        for (ClassLoader loader : candidates) {
            if (loader == null) {
                continue;
            }
            InputStream stream = loader.getResourceAsStream(methodClassFile);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    /**
     * The loader that actually defined the kernel's class, which is the only one guaranteed to
     * serve its class file.
     *
     * <p>Obtained reflectively so this module keeps no dependency on the runtime's reflection
     * types. It mirrors what {@code ReflectionUniverse} already does when it reads a method's
     * bytecode -- the two must agree, or a kernel whose bytecode can be read will still lose its
     * annotations. The context class loader is not a substitute: annotation lookup runs on a
     * sketcher worker thread, which does not inherit the submitting thread's.
     */
    private static ClassLoader declaringClassLoader(ResolvedJavaMethod method) {
        try {
            Object declaringType = method.getDeclaringClass();
            java.lang.reflect.Method getMirror = declaringType.getClass().getMethod("getMirror");
            getMirror.setAccessible(true);
            Object mirror = getMirror.invoke(declaringType);
            return mirror instanceof Class ? ((Class<?>) mirror).getClassLoader() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public ParallelAnnotationProvider[] getParallelAnnotations(ResolvedJavaMethod method) {
        String methodClassFile = method.getDeclaringClass().getName().replaceFirst("L", "").replaceFirst(";", ".class");
        InputStream inputStream = openClassFile(method, methodClassFile);
        if (inputStream == null) {
            // e.g. a JDK class whose resource is not visible, or a runtime-generated hidden class
            // (e.g. java/lang/invoke/LambdaForm$MH.0x...) with no loadable .class resource; either
            // way it carries no @Parallel/@Reduce annotations.
            return new ParallelAnnotationProvider[0];
        }
        try {
            byte[] classFileBytes = inputStream.readAllBytes();
            int major = ((classFileBytes[6] & 0xFF) << 8) | (classFileBytes[7] & 0xFF);
            if (major > MAX_SUPPORTED_MAJOR) {
                classFileBytes[6] = (byte) ((MAX_SUPPORTED_MAJOR >> 8) & 0xFF);
                classFileBytes[7] = (byte) (MAX_SUPPORTED_MAJOR & 0xFF);
            }
            ClassReader classReader = new ClassReader(classFileBytes);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ASMClassVisitor visitor = new ASMClassVisitor(Opcodes.ASM9, cw, method);
            classReader.accept(visitor, 0);

            ParallelAnnotationProvider[] parallelAnnotation = new ParallelAnnotationProvider[visitor.parallelAnnotations.size()];
            return visitor.parallelAnnotations.toArray(parallelAnnotation);
        } catch (IOException e) {
            e.printStackTrace();
            throw new TornadoRuntimeException("[ERROR] Class reader could not be instantiated for class file: " + methodClassFile);
        }
    }
}
