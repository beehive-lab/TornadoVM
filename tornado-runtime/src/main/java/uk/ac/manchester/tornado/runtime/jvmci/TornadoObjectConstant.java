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
package uk.ac.manchester.tornado.runtime.jvmci;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * A JDK-neutral object {@link JavaConstant} backed by a plain Java reference,
 * so the reflection metadata layer can materialise object constants (e.g. a
 * {@code Class} folded from a {@code static final} field) without a HotSpot
 * {@code HotSpotObjectConstant}. These constants are used for compile-time type
 * information (element kinds in reduction snippets) and are canonicalised away
 * before accelerator code generation.
 */
public final class TornadoObjectConstant implements JavaConstant {

    private final Object object;

    public TornadoObjectConstant(Object object) {
        this.object = object;
    }

    public Object getObject() {
        return object;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return object == null;
    }

    @Override
    public boolean isDefaultForKind() {
        return object == null;
    }

    @Override
    public Object asBoxedPrimitive() {
        throw new IllegalArgumentException("Object constant is not a primitive");
    }

    @Override
    public boolean asBoolean() {
        throw new IllegalArgumentException("Object constant is not a boolean");
    }

    @Override
    public int asInt() {
        throw new IllegalArgumentException("Object constant is not an int");
    }

    @Override
    public long asLong() {
        throw new IllegalArgumentException("Object constant is not a long");
    }

    @Override
    public float asFloat() {
        throw new IllegalArgumentException("Object constant is not a float");
    }

    @Override
    public double asDouble() {
        throw new IllegalArgumentException("Object constant is not a double");
    }

    @Override
    public String toValueString() {
        if (object == null) {
            return "null";
        }
        // Match the HotSpot object-constant format that consumers parse, e.g.
        // TornadoOpenCLIntrinsicsReplacements expects "Class:int".
        if (object instanceof Class<?> clazz) {
            return "Class:" + clazz.getName();
        }
        return object.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TornadoObjectConstant other && object == other.object;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(object);
    }

    @Override
    public String toString() {
        return "TornadoObject[" + (object == null ? "null" : object.getClass().getName()) + "]";
    }
}
