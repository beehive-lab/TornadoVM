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
    /**
     * The runtime asks before every launch of a kernel ({@code TornadoVMInterpreter}), so the answer
     * is computed once per method: uncached, each launch re-read the class file and re-ran the
     * frontend (jitLLM decode on Metal, Llama-3.2-1B: 18 to 5 tokens/s).
     */
    private static final Map<Method, Optional<Boolean>> WRITES_LOOP_INDEX = new ConcurrentHashMap<>();

    @Override
    public Optional<Access[]> access(Method method) {
        return CACHE.computeIfAbsent(method, TcAccessProvider::compute).map(Access[]::clone);
    }

    @Override
    public Optional<Boolean> writesLoopIndex(Method method) {
        return WRITES_LOOP_INDEX.computeIfAbsent(method, TcAccessProvider::computeWritesLoopIndex);
    }

    private static Optional<Boolean> computeWritesLoopIndex(Method method) {
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
                    .map(i -> new KernelAccess.Reductions(i.operations(), i.boundParameter(), i.boundConstant(), i.start()));
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
