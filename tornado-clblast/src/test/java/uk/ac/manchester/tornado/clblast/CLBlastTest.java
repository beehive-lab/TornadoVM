/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.clblast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.lang.reflect.Proxy;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.clblast.enums.CLBlastDiagonal;
import uk.ac.manchester.tornado.clblast.enums.CLBlastLayout;
import uk.ac.manchester.tornado.clblast.enums.CLBlastSide;
import uk.ac.manchester.tornado.clblast.enums.CLBlastTranspose;
import uk.ac.manchester.tornado.clblast.enums.CLBlastTriangle;
import uk.ac.manchester.tornado.clblast.provider.CLBlastLibraryProvider;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

class CLBlastTest {

    @Test
    void betaControlsOutputAccessWithoutLosingDoublePrecision() {
        assertEquals(Access.WRITE_ONLY, CLBlast.sgemm(101, 111, 111, 2, 2, 2, 1, null, 2, null, 2, 0, null, 2).getAccess()[12]);
        assertEquals(Access.READ_WRITE, CLBlast.sgemm(101, 111, 111, 2, 2, 2, 1, null, 2, null, 2, 1, null, 2).getAccess()[12]);
        assertEquals(Access.READ_WRITE, CLBlast.dgemm(101, 111, 111, 2, 2, 2, 1, null, 2, null, 2, Double.MIN_VALUE, null, 2).getAccess()[12]);
        assertEquals(Access.WRITE_ONLY, CLBlast.dgemm(101, 111, 111, 2, 2, 2, 1, null, 2, null, 2, -0.0, null, 2).getAccess()[12]);
        assertEquals(Access.WRITE_ONLY, CLBlast.sgemv(101, 111, 2, 2, 1, null, 2, null, 1, 0, null, 1).getAccess()[10]);
        assertEquals(Access.READ_WRITE, CLBlast.dgemv(101, 111, 2, 2, 1, null, 2, null, 1, Double.MIN_VALUE, null, 1).getAccess()[10]);
    }

    @Test
    void axpyReadsOutputAndDotWritesDeviceResult() {
        LibraryTaskDescriptor axpy = CLBlast.saxpy(3, 2, null, 1, null, 1);
        LibraryTaskDescriptor dot = CLBlast.ddot(3, null, 1, null, 1, null);
        assertEquals(CLBlast.LIBRARY_NAME, dot.getLibraryName());
        assertEquals("CLBlastDdot", dot.getFunctionName());
        assertEquals(6, dot.getParameters().length);
        assertEquals(Access.READ_WRITE, axpy.getAccess()[4]);
        assertEquals(Access.READ_ONLY, axpy.getAccess()[2]);
        assertEquals(Access.WRITE_ONLY, dot.getAccess()[5]);
        assertEquals(Access.READ_ONLY, dot.getAccess()[1]);
        assertEquals(Access.READ_ONLY, dot.getAccess()[3]);
    }

    @Test
    void strmmReadsAndOverwritesB() {
        LibraryTaskDescriptor task = CLBlast.strmm(CLBlastLayout.ROW_MAJOR.value(), CLBlastSide.LEFT.value(),
                CLBlastTriangle.UPPER.value(), CLBlastTranspose.NO.value(), CLBlastDiagonal.NON_UNIT.value(),
                3, 2, 1.5f, null, 3, null, 2);
        assertEquals(CLBlast.LIBRARY_NAME, task.getLibraryName());
        assertEquals("CLBlastStrmm", task.getFunctionName());
        assertEquals(12, task.getParameters().length);
        assertEquals(Access.READ_ONLY, task.getAccess()[8]);
        assertEquals(Access.READ_WRITE, task.getAccess()[10]);
        assertEquals(141, CLBlastSide.LEFT.value());
        assertEquals(142, CLBlastSide.RIGHT.value());
        assertEquals(121, CLBlastTriangle.UPPER.value());
        assertEquals(122, CLBlastTriangle.LOWER.value());
        assertEquals(131, CLBlastDiagonal.NON_UNIT.value());
        assertEquals(132, CLBlastDiagonal.UNIT.value());
    }

    @Test
    void providerDiscoveryAndBackendSelectionDoNotLoadNativeLibrary() {
        CLBlastLibraryProvider provider = new CLBlastLibraryProvider();
        assertTrue(ServiceLoader.load(TornadoLibraryProvider.class).stream().anyMatch(p -> p.type() == CLBlastLibraryProvider.class));
        assertTrue(provider.canHandle(device(TornadoVMBackendType.OPENCL, true)));
        assertFalse(provider.canHandle(device(TornadoVMBackendType.CUDA, true)));
        assertFalse(provider.canHandle(device(TornadoVMBackendType.METAL, true)));
        assertFalse(provider.canHandle(device(TornadoVMBackendType.OPENCL, false)));
    }

    private static TornadoXPUDevice device(TornadoVMBackendType backend, boolean nativeStream) {
        Class<?>[] interfaces = nativeStream ? new Class<?>[] { TornadoXPUDevice.class, TornadoNativeStreamSupport.class } : new Class<?>[] { TornadoXPUDevice.class };
        return (TornadoXPUDevice) Proxy.newProxyInstance(CLBlastTest.class.getClassLoader(), interfaces,
                (proxy, method, args) -> method.getName().equals("getTornadoVMBackend") ? backend : null);
    }

    @Test
    void missingLibraryReportsInstallationRequirement() {
        assumeFalse(CLBlastLibraryProvider.isAvailable(), "This test checks a host without CLBlast");
        TornadoRuntimeException error = assertThrows(TornadoRuntimeException.class,
                () -> new CLBlastLibraryProvider().createContext(device(TornadoVMBackendType.OPENCL, true), 0));
        assertTrue(error.getMessage().contains("Install the CLBlast"));
    }
}
