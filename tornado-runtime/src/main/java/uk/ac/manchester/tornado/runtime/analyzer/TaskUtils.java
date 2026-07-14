/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.analyzer;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.api.common.PrebuiltTaskPackage;
import uk.ac.manchester.tornado.api.common.TornadoFunctions;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.PrebuiltTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;

public class TaskUtils {

    public static CompilableTask scalaTask(String id, Object object, Object... args) {
        Class<?> type = object.getClass();
        Method entryPoint = null;
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals("apply") && !m.isSynthetic() && !m.isBridge()) {
                entryPoint = m;
                break;
            }
        }
        unimplemented("scala task");
        return createTask(null, id, entryPoint, object, false, args);
    }

    /**
     * When obtaining the method to be compiled it returns a lambda expression that
     * contains the invocation to the actual code. The actual code is an INVOKE that
     * is inside the apply method of the lambda. This method searches for the nested
     * method with the actual code to be compiled.
     *
     * @param task
     *     Input Tornado task that corresponds to the user code.
     */
    public static Method resolveMethodHandle(Object task) {
        // The kernel entry is a lambda/method-reference whose proxy is a hidden class with no
        // classfile resource, so its bytecode cannot be read reflectively. The compute Task
        // interfaces are Serializable, so resolve the implementation method through the
        // compiler-generated writeReplace() -> SerializedLambda.
        return resolveViaSerializedLambda(task);
    }

    /**
     * JDK-neutral kernel-entry resolution for JVMCI-absent JDKs (27+). The compute Task
     * interfaces are {@link java.io.Serializable}, so the compiler emits a {@code writeReplace()}
     * on each task lambda that yields a {@link SerializedLambda} describing the implementation
     * method (its declaring class, name and JVM signature). We resolve that directly to a
     * {@link Method} via core reflection, avoiding both the hidden lambda-proxy bytecode and the
     * removed HotSpot JVMCI reflection helpers.
     */
    private static Method resolveViaSerializedLambda(Object task) {
        try {
            Method writeReplace = task.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replacement = writeReplace.invoke(task);
            if (!(replacement instanceof SerializedLambda serializedLambda)) {
                throw new TornadoInternalError("Task lambda did not serialize to a SerializedLambda: " + replacement);
            }
            ClassLoader loader = task.getClass().getClassLoader();
            Class<?> implClass = Class.forName(serializedLambda.getImplClass().replace('/', '.'), false, loader);
            Class<?>[] parameterTypes = parseParameterTypes(serializedLambda.getImplMethodSignature(), loader);
            Method implementation = findDeclaredMethod(implClass, serializedLambda.getImplMethodName(), parameterTypes);
            implementation.setAccessible(true);
            return implementation;
        } catch (ReflectiveOperationException e) {
            throw new TornadoInternalError("Unable to resolve kernel entry via SerializedLambda on the JVMCI-absent path: " + e);
        }
    }

    /** Walk the class hierarchy so implementation methods inherited from a supertype still resolve. */
    private static Method findDeclaredMethod(Class<?> start, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                // try the superclass
            }
        }
        throw new NoSuchMethodException(start.getName() + "." + name);
    }

    /** Parse the parameter portion of a JVM method descriptor (e.g. {@code (I[JLp/C;)V}) into classes. */
    private static Class<?>[] parseParameterTypes(String methodDescriptor, ClassLoader loader) throws ClassNotFoundException {
        List<Class<?>> types = new ArrayList<>();
        int i = methodDescriptor.indexOf('(') + 1;
        int end = methodDescriptor.indexOf(')');
        while (i < end) {
            int dims = 0;
            while (methodDescriptor.charAt(i) == '[') {
                dims++;
                i++;
            }
            char c = methodDescriptor.charAt(i);
            Class<?> type;
            if (c == 'L') {
                int semicolon = methodDescriptor.indexOf(';', i);
                String binaryName = methodDescriptor.substring(i + 1, semicolon).replace('/', '.');
                type = Class.forName(binaryName, false, loader);
                i = semicolon + 1;
            } else {
                type = primitiveType(c);
                i++;
            }
            if (dims > 0) {
                type = java.lang.reflect.Array.newInstance(type, new int[dims]).getClass();
            }
            types.add(type);
        }
        return types.toArray(new Class<?>[0]);
    }

    private static Class<?> primitiveType(char descriptor) {
        return switch (descriptor) {
            case 'Z' -> boolean.class;
            case 'B' -> byte.class;
            case 'C' -> char.class;
            case 'S' -> short.class;
            case 'I' -> int.class;
            case 'J' -> long.class;
            case 'F' -> float.class;
            case 'D' -> double.class;
            default -> throw new TornadoInternalError("Unknown primitive descriptor: " + descriptor);
        };
    }

    public static CompilableTask createTask(Method method, ScheduleContext meta, String id, Task code) {
        return createTask(meta, id, method, code, true);
    }

    public static <T1> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task1<T1> code, T1 arg1) {
        return createTask(meta, id, method, code, true, arg1);
    }

    public static <T1, T2> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task2<T1, T2> code, T1 arg1, T2 arg2) {
        return createTask(meta, id, method, code, true, arg1, arg2);
    }

    public static <T1, T2, T3> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3);
    }

    public static <T1, T2, T3, T4> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4);
    }

    public static <T1, T2, T3, T4, T5> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5);
    }

    public static <T1, T2, T3, T4, T5, T6> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5,
            T6 arg6) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompilableTask createTask(Method method, ScheduleContext meta, String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> CompilableTask createTask(Method method, ScheduleContext meta, String id,
            TornadoFunctions.Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> CompilableTask createTask(Method method, ScheduleContext meta, String id,
            TornadoFunctions.Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11,
            T12 arg12) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> CompilableTask createTask(Method method, ScheduleContext meta, String id,
            TornadoFunctions.Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11,
            T12 arg12, T13 arg13) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> CompilableTask createTask(Method method, ScheduleContext meta, String id,
            TornadoFunctions.Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10,
            T11 arg11, T12 arg12, T13 arg13, T14 arg14) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CompilableTask createTask(Method method, ScheduleContext meta, String id,
            Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11,
            T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
    }

    private static Object[] extractCapturedVariables(Object code) {
        final Class<?> type = code.getClass();
        int count = 0;
        for (Field field : type.getDeclaredFields()) {
            if (!field.getType().getName().contains("$$Lambda$") && !field.getName().contains("LAMBDA_INSTANCE$")) {
                count++;
            }
        }

        final Object[] cvs = new Object[count];
        int index = 0;
        for (Field field : type.getDeclaredFields()) {
            if (!field.getType().getName().contains("$$Lambda$") && !field.getName().contains("LAMBDA_INSTANCE$")) {
                field.setAccessible(true);
                try {
                    cvs[index] = field.get(code);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }
                index++;
            }
        }
        return cvs;
    }

    /**
     * Marshal object from {@link PrebuiltTaskPackage} to {@link PrebuiltTask}.
     * 
     * @param meta
     *     {@link ScheduleContext}
     * @param taskPackage
     *     {@link PrebuiltTaskPackage}
     * @return {@link PrebuiltTask}
     */
    public static PrebuiltTask createTask(ScheduleContext meta, PrebuiltTaskPackage taskPackage) {
        PrebuiltTask prebuiltTask = new PrebuiltTask(meta, //
                taskPackage.getId(), //
                taskPackage.getEntryPoint(), //
                taskPackage.getFilename(), //
                taskPackage.getArgs(),  //
                taskPackage.getAccesses());
        if (taskPackage.getAtomics() != null) {
            prebuiltTask.setAtomics(taskPackage.getAtomics());
        }
        return prebuiltTask;
    }

    private static CompilableTask createTask(ScheduleContext meta, String id, Method method, Object code, boolean extractCVs, Object... args) {
        final int numArgs;
        final Object[] cvs;

        if (extractCVs) {
            cvs = extractCapturedVariables(code);
            numArgs = cvs.length + args.length;
        } else {
            cvs = null;
            numArgs = args.length;
        }

        final Object[] parameters = new Object[numArgs];
        int index = 0;
        if (extractCVs) {
            for (Object cv : cvs) {
                parameters[index] = cv;
                index++;
            }
        }

        for (Object arg : args) {
            parameters[index] = arg;
            index++;
        }
        return new CompilableTask(meta, id, method, parameters);
    }

}
