/*
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal;

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.ROUND_NEAREST_EVEN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.ROUND_TOWARD_ZERO_INTEGER;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.ptx.PTXDevice;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

public class PTXCodeUtil {

    private static final String PACKAGE_PANAMA_TYPES = "uk_ac_manchester_tornado_api_types_";
    private static final String PACKAGE_PANAMA_COLLECTION = "uk_ac_manchester_tornado_api_types_collections_";
    private static final String PTX_HEADER_FORMAT = PTXAssemblerConstants.COMPUTE_VERSION + " %s \n" + PTXAssemblerConstants.TARGET_ARCH + " %s \n" + PTXAssemblerConstants.ADDRESS_HEADER + " %s \n";

    /**
     * Create a calling convention from a {@link ResolvedJavaMethod}.
     */
    public static CallingConvention getCallingConvention(CodeCacheProvider codeCache, ResolvedJavaMethod method) {
        Signature sig = method.getSignature();
        JavaType retType = sig.getReturnType(method.getDeclaringClass());
        int sigCount = sig.getParameterCount(false);
        JavaType[] argTypes;
        int argIndex = 0;
        if (!method.isStatic()) {
            argTypes = new JavaType[sigCount + 1];
            argTypes[argIndex++] = method.getDeclaringClass();
        } else {
            argTypes = new JavaType[sigCount];
        }
        for (int i = 0; i < sigCount; i++) {
            argTypes[argIndex++] = sig.getParameterType(i, null);
        }
        return getCallingConvention(retType, argTypes, codeCache.getTarget());
    }

    private static CallingConvention getCallingConvention(JavaType returnType, JavaType[] argTypes, TargetDescription target) {
        int variableIndex = 0;

        Variable[] inputParameters = new Variable[argTypes.length];
        for (int i = 0; i < argTypes.length; i++, variableIndex++) {
            inputParameters[i] = new Variable(LIRKind.value(target.arch.getPlatformKind(argTypes[i].getJavaKind())), variableIndex);

        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : returnType.getJavaKind();
        LIRKind lirKind = LIRKind.value(target.arch.getPlatformKind(returnKind));

        Variable returnParameter = new Variable(lirKind, variableIndex);

        return new CallingConvention(0, returnParameter, inputParameters);
    }

    /**
     * Only need to append value. If negative value, remove the minus sign in front
     *
     * @param sb
     * @param parameter
     */
    private static void emitSignatureForPrimitiveParameter(StringBuilder sb, Object parameter) {
        // Only need to append value. If negative value, remove the minus sign in front
        sb.append(parameter.toString().replace('.', '_').replaceAll("-", ""));
    }

    /**
     * Append type and length of the array
     *
     * @param sb
     * @param arg
     * @param task
     */
    private static void emitSignatureForArrayParameter(StringBuilder sb, Object arg, SchedulableTask task) {
        Class<?> argClass = arg.getClass();
        // Need to append type and length
        sb.append(argClass.getComponentType().getName().replace('[', '_'));
        if (task.getBatchThreads() != 0) {
            sb.append(task.getBatchThreads());
        } else {
            sb.append(Array.getLength(arg));
        }
    }

    /**
     * Append Type and batch size
     *
     * @param sb
     * @param arg
     * @param task
     */
    private static void emitSignatureForOffHeapSegments(StringBuilder sb, Object arg, SchedulableTask task) {
        Class<?> argClass = arg.getClass();
        if (task.getBatchThreads() != 0) {
            sb.append(task.getBatchThreads());
        } else {
            sb.append(argClass.getName().replace('.', '_'));
        }
    }

    /**
     * Since with objects there is no way to know what will be a
     * constant differentiate using the hashcode of the object
     *
     * @param sb
     * @param arg
     */
    private static void emitSignatureForGenericParameter(StringBuilder sb, Object arg) {
        Class<?> argClass = arg.getClass();
        sb.append(argClass.getName().replace('.', '_'));
        sb.append('_');
        sb.append(arg.hashCode());
    }

    private static StringBuilder sanitiseTaskID(String methodName, SchedulableTask task) {
        StringBuilder sb = new StringBuilder();
        String taskNameID = task.getId();
        for (int i = 0; i < taskNameID.length(); i++) {
            char c = taskNameID.charAt(i);
            if (i == 0 && Character.isDigit(c)) {
                sb.append('_');
                sb.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        sb.append('_').append(methodName);
        return sb;
    }

    public static String buildKernelName(String methodName, SchedulableTask task) {
        StringBuilder sb = sanitiseTaskID(methodName, task);
        for (Object arg : task.getArguments()) {
            sb.append('_');
            Class<?> argClass = arg.getClass();
            if (argClass == HalfFloat.class) {
                emitSignatureForGenericParameter(sb, arg);
            } else if (RuntimeUtilities.isBoxedPrimitiveClass(argClass)) {
                emitSignatureForPrimitiveParameter(sb, arg);
            } else if (argClass.isArray() && RuntimeUtilities.isPrimitiveArray(argClass)) {
                emitSignatureForArrayParameter(sb, arg, task);
            } else if (arg instanceof TornadoNativeArray) {
                emitSignatureForOffHeapSegments(sb, arg, task);
            } else {
                emitSignatureForGenericParameter(sb, arg);
            }
        }

        // If the batch number is greater than 0 append the name
        if (task.getBatchNumber() > 0) {
            sb.append("_").append(task.getBatchNumber());
        }
        return sb.toString().replaceAll(PACKAGE_PANAMA_TYPES, "") //
                .replaceAll(PACKAGE_PANAMA_COLLECTION, "") //
                .replaceAll("&", "") //
                .toLowerCase();
    }

    public static byte[] getCodeWithAttachedPTXHeader(byte[] targetCode, PTXBackend backend) {
        PTXDevice device = backend.getDeviceContext().getDevice();
        String header = String.format(PTX_HEADER_FORMAT, device.getTargetPTXVersion(), device.getTargetArchitecture(), backend.getTarget().getArch().getWordSize() * 8);

        return prependToTargetCode(targetCode, header.getBytes());
    }

    public static byte[] prependToTargetCode(byte[] targetCode, byte[] codeToPrepend) {
        final int size = targetCode.length + codeToPrepend.length + 1;

        final byte[] newCode = new byte[size];
        Arrays.fill(newCode, (byte) 0);

        final ByteBuffer buffer = ByteBuffer.wrap(newCode);
        buffer.put(codeToPrepend);
        buffer.put((byte) '\n');
        buffer.put(targetCode);
        return newCode;
    }

    public static String makeMethodName(ResolvedJavaMethod method) {
        if (method != null) {
            final String declaringClass = method.getDeclaringClass().toJavaName().replace(".", "_");
            return String.format("%s_%s", declaringClass, method.getName());
        } else {
            TornadoInternalError.unimplemented();
            return null;
        }
    }

    public static String getFPURoundingMode(PTXKind lhs, PTXKind rhs) {
        String roundingMode = ROUND_NEAREST_EVEN;

        if (lhs.isB16() && rhs.isF32()) {
            return roundingMode;
        }
        if (!lhs.isFloating() && rhs.isFloating()) {
            roundingMode = ROUND_TOWARD_ZERO_INTEGER;
        }
        if ((lhs.isF64() && rhs.isF32())) {
            return null;
        }
        return roundingMode;
    }
}
