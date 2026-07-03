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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Reflection-backed {@link Signature}: parameter and return types derived from
 * a {@link java.lang.reflect.Executable} rather than from a parsed method
 * descriptor obtained through HotSpot JVMCI.
 */
final class ReflectionSignature implements Signature {

    private final ReflectionUniverse universe;
    private final Class<?>[] parameterTypes;
    private final Class<?> returnType;

    ReflectionSignature(ReflectionUniverse universe, Executable executable) {
        this.universe = universe;
        this.parameterTypes = executable.getParameterTypes();
        this.returnType = (executable instanceof Method m) ? m.getReturnType() : void.class;
        assert executable instanceof Method || executable instanceof Constructor;
    }

    @Override
    public int getParameterCount(boolean withReceiver) {
        return parameterTypes.length + (withReceiver ? 1 : 0);
    }

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        return universe.lookupType(parameterTypes[index]);
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        return universe.lookupType(returnType);
    }
}
