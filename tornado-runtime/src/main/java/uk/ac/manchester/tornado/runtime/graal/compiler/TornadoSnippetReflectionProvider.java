/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import tornado.graal.compiler.api.replacements.SnippetReflectionProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.runtime.jvmci.TornadoObjectConstant;
import uk.ac.manchester.tornado.runtime.jvmci.reflection.ReflectionResolvedJavaType;

public class TornadoSnippetReflectionProvider implements SnippetReflectionProvider {

    @Override
    public JavaConstant forBoxed(JavaKind kind, Object value) {
        if (kind == JavaKind.Object) {
            return forObject(value);
        }
        return JavaConstant.forBoxedPrimitive(value);
    }

    @Override
    public JavaConstant forObject(Object object) {
        return object == null ? JavaConstant.NULL_POINTER : new TornadoObjectConstant(object);
    }

    @Override
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant == null || constant.isNull()) {
            return null;
        }
        if (constant instanceof TornadoObjectConstant objectConstant) {
            return type.cast(objectConstant.getObject());
        }
        unimplemented();
        return null;
    }

    @Override
    public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
        unimplemented();
        return null;
    }

    @Override
    public Class<?> originalClass(ResolvedJavaType type) {
        if (type instanceof ReflectionResolvedJavaType reflectionType) {
            return reflectionType.getMirror();
        }
        unimplemented();
        return null;
    }

    @Override
    public Executable originalMethod(ResolvedJavaMethod method) {
        return null;
    }

    @Override
    public Field originalField(ResolvedJavaField field) {
        return null;
    }

}
