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
package uk.ac.manchester.tornado.drivers.common.tc;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.runtime.common.KernelAccess;
import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.tornado.AccessAnalysis;

/** TC's parameter accesses for the runtime ({@link KernelAccess}), cached per method. */
public final class TcAccessProvider implements KernelAccess.Provider {

    private static final Map<Method, Optional<Access[]>> CACHE = new ConcurrentHashMap<>();

    @Override
    public Optional<Access[]> access(Method method) {
        return CACHE.computeIfAbsent(method, TcAccessProvider::compute).map(Access[]::clone);
    }

    @Override
    public Optional<Boolean> writesLoopIndex(Method method) {
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        try {
            return TcCompiler.writesLoopIndex(Frontend.BytecodeSource.classLoader(loader), owner.getName(), method.getName(), descriptor);
        } catch (RuntimeException | StackOverflowError e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<KernelAccess.Reductions> reductions(Method method, java.util.List<Integer> reduceIndices) {
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        try {
            return TcCompiler.reductions(Frontend.BytecodeSource.classLoader(loader), owner.getName(), method.getName(), descriptor, reduceIndices)
                    .map(i -> new KernelAccess.Reductions(i.operations(), i.boundParameter(), i.boundConstant()));
        } catch (RuntimeException | StackOverflowError e) {
            return Optional.empty();
        }
    }

    private static Optional<Access[]> compute(Method method) {
        var owner = method.getDeclaringClass();
        var loader = owner.getClassLoader() != null ? owner.getClassLoader() : ClassLoader.getSystemClassLoader();
        var descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
        try {
            return TcCompiler.access(Frontend.BytecodeSource.classLoader(loader), owner.getName(), method.getName(), descriptor).map(bits -> {
                var access = new Access[bits.length];
                for (int i = 0; i < bits.length; i++) {
                    access[i] = switch (bits[i]) {
                        case AccessAnalysis.READ -> Access.READ_ONLY;
                        case AccessAnalysis.WRITE -> Access.WRITE_ONLY;
                        case AccessAnalysis.READ | AccessAnalysis.WRITE -> Access.READ_WRITE;
                        default -> Access.NONE;
                    };
                }
                // A HalfFloat argument is a boxed value the runtime moves as the sketch declares it.
                var types = method.getParameterTypes();
                int receiver = access.length - types.length;
                for (int j = 0; j < types.length; j++) {
                    if (types[j].getName().equals("uk.ac.manchester.tornado.api.types.HalfFloat") && access[j + receiver] == Access.NONE) {
                        access[j + receiver] = Access.WRITE_ONLY;
                    }
                }
                return access;
            });
        } catch (RuntimeException | StackOverflowError e) {
            return Optional.empty();
        }
    }
}
