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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jdk.graal.compiler.bytecode.Bytecodes;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
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
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.PrebuiltTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;

public class TaskUtils {

    private static final String JDK_VM_CI_HOTSPOT_JDK_REFLECTION = "jdk.vm.ci.hotspot.HotSpotJDKReflection";
    private static final String JDK_VM_CI_HOTSPOT_RESOLVED_JAVA_METHOD_IMPL = "jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl";

    private static boolean useToJavaMethod = false;

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
     * JDK distributions before JDK 13 that to not backport JVMCI do not implement
     * {@link jdk.vm.ci.hotspot.HotSpotJDKReflection#getMethod}. Instead, we have to
     * rely on {@link jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl#toJava} that
     * uses pure reflection.
     */
    private static Method callToJava(JavaMethod javaMethod) {
        try {
            Class hotSpotResolvedJavaMethodImpl = Class.forName(JDK_VM_CI_HOTSPOT_RESOLVED_JAVA_METHOD_IMPL);
            Method toJava = hotSpotResolvedJavaMethodImpl.getDeclaredMethod("toJava");

            toJava.setAccessible(true);
            Method m = (Method) toJava.invoke(javaMethod);
            m.setAccessible(true);
            return m;
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            TornadoInternalError.shouldNotReachHere("Both HotSpotResolvedJavaMethodImpl::toJava and HotSpotJDKReflection::getMethod are missing from the JDK !");
        }
        return null;
    }

    /**
     * JDK distributions that backport JVMCI or after JDK 13 implement
     * {@link jdk.vm.ci.hotspot.HotSpotJDKReflection#getMethod} which relies on a
     * native call to the JVM. If this method is not available, then we call
     * {@link jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl#toJava} that uses pure
     * reflection.
     */
    private static Method callGetMethod(JavaMethod javaMethod) {
        try {
            Class hotSpotJDKReflection = Class.forName(JDK_VM_CI_HOTSPOT_JDK_REFLECTION);
            Method getMethod = null;
            for (Method method : hotSpotJDKReflection.getDeclaredMethods()) {
                if ("getMethod".equals(method.getName())) {
                    getMethod = method;
                    break;
                }
            }
            getMethod.setAccessible(true);
            Method m = (Method) getMethod.invoke(hotSpotJDKReflection, javaMethod);
            m.setAccessible(true);
            return m;
        } catch (SecurityException | IllegalArgumentException | ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
            new TornadoLogger().debug("HotSpotJDKReflection::getMethod is missing from the JDK distribution. Falling back to HotSpotResolvedJavaMethodImpl::toJava");
            useToJavaMethod = true;
            return callToJava(javaMethod);
        }
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
        final Class<?> type = task.getClass();

        /*
         * task should implement one of the TaskX interfaces... ...so we look for the
         * apply function. Note: apply will perform some type casting and then call the
         * function we really want to use, so we need to resolve the nested function.
         */
        Method entryPoint = null;
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals("apply")) {
                entryPoint = m;
            }
        }

        guarantee(entryPoint != null, "unable to find entry point");
        /*
         * Fortunately we can do a bit of JVMCI magic to resolve the function to a
         * Method.
         */
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getVMBackend().getMetaAccess().lookupJavaMethod(entryPoint);
        final ConstantPool cp = resolvedMethod.getConstantPool();
        final byte[] bc = resolvedMethod.getCode();

        for (int i = 0; i < bc.length; i++) {
            if (bc[i] == (byte) Bytecodes.INVOKESTATIC) {
                cp.loadReferencedType(bc[i + 2], Bytecodes.INVOKESTATIC);
                JavaMethod jm = cp.lookupMethod(bc[i + 2], Bytecodes.INVOKESTATIC);

                if (useToJavaMethod) {
                    return callToJava(jm);
                } else {
                    return callGetMethod(jm);
                }
            } else if (bc[i] == (byte) Bytecodes.INVOKEVIRTUAL) {
                cp.loadReferencedType(bc[i + 2], Bytecodes.INVOKEVIRTUAL);
                JavaMethod jm = cp.lookupMethod(bc[i + 2], Bytecodes.INVOKEVIRTUAL);
                switch (jm.getName()) {
                    case "booleanValue":
                    case "byteValue":
                    case "charValue":
                    case "shortValue":
                    case "intValue":
                    case "floatValue":
                    case "doubleValue":
                    case "longValue":
                        continue;
                }

                if (useToJavaMethod) {
                    return callToJava(jm);
                } else {
                    return callGetMethod(jm);
                }
            }
        }
        shouldNotReachHere();
        return null;
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
