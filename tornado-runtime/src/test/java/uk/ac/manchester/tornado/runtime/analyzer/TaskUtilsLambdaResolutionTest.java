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
package uk.ac.manchester.tornado.runtime.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

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
