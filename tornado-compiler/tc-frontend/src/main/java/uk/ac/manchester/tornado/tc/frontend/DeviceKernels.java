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
package uk.ac.manchester.tornado.tc.frontend;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Resolves a {@code static final DeviceKernel} field to the static method it wraps, through the host
 * JVM: the field holds {@code DeviceKernel.of(Owner::method)}, a serializable method reference whose
 * {@link SerializedLambda} names the implementation method. Reflection only, so the frontend needs no
 * compile-time dependency on the TornadoVM API.
 */
final class DeviceKernels {

    private DeviceKernels() {
    }

    static Optional<Frontend.MethodRef> resolve(ClassLoader loader, String ownerInternalName, String fieldName) {
        try {
            var owner = Class.forName(ownerInternalName.replace('/', '.'), true, loader);
            var field = owner.getDeclaredField(fieldName);
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())) {
                return Optional.empty();
            }
            field.setAccessible(true);
            var deviceKernel = field.get(null);
            if (deviceKernel == null) {
                return Optional.empty();
            }
            var kernel = deviceKernel.getClass().getMethod("getKernel").invoke(deviceKernel);
            var writeReplace = kernel.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            if (writeReplace.invoke(kernel) instanceof SerializedLambda lambda) {
                return Optional.of(new Frontend.MethodRef(lambda.getImplClass(), lambda.getImplMethodName(), lambda.getImplMethodSignature()));
            }
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }
}
