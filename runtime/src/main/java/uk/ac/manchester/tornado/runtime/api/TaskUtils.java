/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.api;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.unimplemented;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.graalvm.compiler.bytecode.Bytecodes;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.GenericDevice;
import uk.ac.manchester.tornado.api.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.meta.domain.DomainTree;
import uk.ac.manchester.tornado.meta.domain.IntDomain;
import uk.ac.manchester.tornado.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.runtime.api.TornadoFunctions.Task9;

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

    private static Method resolveMethodHandle(Object task) {
        final Class<?> type = task.getClass();

        /*
         * task should implement one of the TaskX interfaces... ...so we look
         * for the apply function. Note: apply will perform some type casting
         * and then call the function we really want to use, so we need to
         * resolve the nested function.
         */
        Method entryPoint = null;
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals("apply")) {
                entryPoint = m;
            }
        }

        guarantee(entryPoint != null, "unable to find entry point");
        /*
         * Fortunately we can do a bit of JVMCI magic to resolve the function to
         * a Method.
         */
        final ResolvedJavaMethod resolvedMethod = TornadoRuntime.getVMBackend().getMetaAccess().lookupJavaMethod(entryPoint);
        final ConstantPool cp = resolvedMethod.getConstantPool();
        final byte[] bc = resolvedMethod.getCode();

        for (int i = 0; i < bc.length; i++) {
            if (bc[i] == (byte) Bytecodes.INVOKESTATIC) {
                cp.loadReferencedType(bc[i + 2], Bytecodes.INVOKESTATIC);
                JavaMethod jm = cp.lookupMethod(bc[i + 2], Bytecodes.INVOKESTATIC);
                try {
                    Method toJavaMethod = jm.getClass().getDeclaredMethod("toJava");
                    toJavaMethod.setAccessible(true);
                    Method m = (Method) toJavaMethod.invoke(jm);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {

                    e.printStackTrace();
                }
                break;
            } else if (bc[i] == (byte) Bytecodes.INVOKEVIRTUAL) {
                cp.loadReferencedType(bc[i + 2], Bytecodes.INVOKEVIRTUAL);
                JavaMethod jm = cp.lookupMethod(bc[i + 2], Bytecodes.INVOKEVIRTUAL);
                switch (jm.getName()) {
                    case "floatValue":
                    case "doubleValue":
                    case "intValue":
                        continue;
                }
                try {
                    Method toJavaMethod = jm.getClass().getDeclaredMethod("toJava");
                    toJavaMethod.setAccessible(true);
                    Method m = (Method) toJavaMethod.invoke(jm);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        shouldNotReachHere();
        return null;
    }

    public static <T1> CompilableTask createTask(ScheduleMetaData meta, String id, Task1<T1> code, T1 arg) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg);
    }

    public static <T1, T2> CompilableTask createTask(ScheduleMetaData meta, String id, Task2<T1, T2> code, T1 arg1, T2 arg2) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2);
    }

    public static <T1, T2, T3> CompilableTask createTask(ScheduleMetaData meta, String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2, arg3);
    }

    public static <T1, T2, T3, T4> CompilableTask createTask(ScheduleMetaData meta, String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4);
    }

    public static <T1, T2, T3, T4, T5> CompilableTask createTask(ScheduleMetaData meta, String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5);
    }

    public static <T1, T2, T3, T4, T5, T6> CompilableTask createTask(ScheduleMetaData meta, String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompilableTask createTask(ScheduleMetaData meta, String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6,
            T7 arg7) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompilableTask createTask(ScheduleMetaData meta, String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5,
            T6 arg6, T7 arg7, T8 arg8) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompilableTask createTask(ScheduleMetaData meta, String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompilableTask createTask(ScheduleMetaData meta, String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CompilableTask createTask(ScheduleMetaData meta, String id,
            Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11,
            T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        final Method method = resolveMethodHandle(code);
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
    }

    public static Object[] extractCapturedVariables(Object code) {
        final Class<?> type = code.getClass();
        int count = 0;
        for (Field field : type.getDeclaredFields()) {
            if (!field.getType().getName().contains("$$Lambda$")) {
                count++;
            }
        }

        final Object[] cvs = new Object[count];
        int index = 0;
        for (Field field : type.getDeclaredFields()) {
            if (!field.getType().getName().contains("$$Lambda$")) {
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

    public static PrebuiltTask createTask(ScheduleMetaData meta, String id, String entryPoint, String filename, Object[] args, Access[] accesses, GenericDevice device, int[] dims) {
        final DomainTree domain = new DomainTree(dims.length);
        for (int i = 0; i < dims.length; i++) {
            domain.set(i, new IntDomain(0, 1, dims[i]));
        }

        return new PrebuiltTask(meta, id, entryPoint, filename, args, accesses, device, domain);
    }

    public static CompilableTask createTask(ScheduleMetaData meta, String id, Runnable runnable) {
        final Method method = resolveRunnable(runnable);
        return createTask(meta, id, method, runnable, false);
    }

    private static CompilableTask createTask(ScheduleMetaData meta, String id, Method method, Object code, boolean extractCVs, Object... args) {
        final int numArgs;
        final Object[] cvs;

        if (extractCVs) {
            cvs = TaskUtils.extractCapturedVariables(code);
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

    private static Method resolveRunnable(Runnable runnable) {
        final Class<?> type = runnable.getClass();
        try {
            final Method method = type.getDeclaredMethod("run");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }
}
