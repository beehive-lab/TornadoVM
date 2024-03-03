/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.common.code;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import uk.ac.manchester.tornado.api.types.HalfFloat;

public class CodeUtil {

    /**
     * Create a calling convention from a {@link ResolvedJavaMethod}.
     */
    public static CallingConvention getCallingConvention(CodeCacheProvider codeCache, CallingConvention.Type type, ResolvedJavaMethod method) {
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

        final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);
        return getCallingConvention(type, retType, argTypes, codeCache.getTarget(), locals);
    }

    private static CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] argTypes, TargetDescription target, Local[] locals) {
        int variableIndex = 0;

        Variable[] inputParameters = new Variable[argTypes.length];
        for (int i = 0; i < argTypes.length; i++, variableIndex++) {
            if (isHalfFloat(argTypes[i])) {
                // Treat HalfFloat as short during code generation
                inputParameters[i] = new Variable(LIRKind.value(target.arch.getPlatformKind(JavaKind.Short)), variableIndex);
                continue;
            }
            inputParameters[i] = new Variable(LIRKind.value(target.arch.getPlatformKind(argTypes[i].getJavaKind())), variableIndex);

        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : returnType.getJavaKind();
        LIRKind lirKind = LIRKind.value(target.arch.getPlatformKind(returnKind));

        Variable returnParameter = new Variable(lirKind, variableIndex);
        variableIndex++;

        return new CallingConvention(0, returnParameter, inputParameters);
    }

    public static boolean isHalfFloat(JavaType type) {
        return type.toJavaName().equals(HalfFloat.class.getName());
    }

}
