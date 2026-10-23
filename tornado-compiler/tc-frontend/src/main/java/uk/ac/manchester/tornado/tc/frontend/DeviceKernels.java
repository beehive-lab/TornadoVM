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
