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
package uk.ac.manchester.tornado.runtime.analyzer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.junit.Test;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

/**
 * Pure-JVM (no-GPU) tests for the JVMCI-absent kernel-entry resolution: a Serializable task lambda
 * resolves through {@code writeReplace() -> SerializedLambda} to its implementation {@link Method},
 * and a non-serializable lambda (no {@code writeReplace}) fails cleanly.
 */
public class TaskUtilsLambdaResolutionTest {

    @FunctionalInterface
    interface SerializableRunnable extends Serializable {
        void run();
    }

    static void kernelBody() {
    }

    @Test
    public void resolvesSerializableLambdaToImplMethod() {
        SerializableRunnable task = TaskUtilsLambdaResolutionTest::kernelBody;
        Method method = TaskUtils.resolveMethodHandle(task);
        assertEquals("kernelBody", method.getName());
        assertEquals(TaskUtilsLambdaResolutionTest.class, method.getDeclaringClass());
    }

    @Test
    public void nonSerializableLambdaFailsCleanly() {
        // A plain Runnable lambda has no compiler-generated writeReplace(), so resolution must raise a
        // TornadoInternalError rather than NPE or leak a ReflectiveOperationException.
        Runnable plain = () -> {
        };
        assertThrows(TornadoInternalError.class, () -> TaskUtils.resolveMethodHandle(plain));
    }
}
