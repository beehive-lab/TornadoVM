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
package uk.ac.manchester.tornado.clblast.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_DOUBLE;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_FLOAT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;

/** Exercises the real FFM downcalls against native upcall stubs, without a GPU or CLBlast installation. */
class CLBlastBindingsTest {

    private static final long QUEUE = 0x4560;

    private static final class Capture {
        private List<Object> arguments;
        private int status;

        int call(Object[] args) {
            arguments = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof MemorySegment segment) {
                    // The penultimate parameter is cl_command_queue*, rather than cl_command_queue.
                    arguments.add(i == args.length - 2 ? segment.reinterpret(Long.BYTES).get(C_POINTER, 0).address() : segment.address());
                } else {
                    arguments.add(args[i]);
                }
            }
            return status;
        }
    }

    private static LibraryInvocation invocation(Object[] args) {
        long[] buffers = new long[args.length];
        long[] offsets = new long[args.length];
        boolean[] references = new boolean[args.length];
        for (int i = 0; i < args.length; i++) {
            buffers[i] = 0x1000L + i * 0x100;
            // Deliberately different offsets, including a non-zero buffer subregion.
            offsets[i] = 32L + i * 8;
            references[i] = true;
        }
        // Raw addresses are deliberately absent: OpenCL must use nativeBuffers instead.
        return new LibraryInvocation(args, new long[args.length], buffers, offsets, references, null, 0, null, null, false);
    }

    private static CLBlastNativeLib library(String name, FunctionDescriptor descriptor, Capture capture, Arena arena) throws ReflectiveOperationException {
        MemorySegment stub = stub(descriptor, capture, arena);
        return new CLBlastNativeLib(symbol -> symbol.equals(name) ? Optional.of(stub) : Optional.empty());
    }

    private static MemorySegment stub(FunctionDescriptor descriptor, Capture capture, Arena arena) throws ReflectiveOperationException {
        MethodHandle target = MethodHandles.lookup().findVirtual(Capture.class, "call", MethodType.methodType(int.class, Object[].class)).bindTo(capture)
                .asCollector(Object[].class, descriptor.argumentLayouts().size()).asType(descriptor.toMethodType());
        return FFMSupport.upcallStub(target, descriptor, arena);
    }

    @Test
    void marshalsEveryRoutineThroughItsCAbi() throws ReflectiveOperationException {
        for (boolean fp64 : new boolean[] { false, true }) {
            ValueLayout scalar = fp64 ? C_DOUBLE : C_FLOAT;
            // Explicit branches preserve the boxed scalar type (a numeric ternary promotes float to double).
            Object alpha;
            Object beta;
            if (fp64) {
                alpha = 1.25d;
                beta = -0.5d;
            } else {
                alpha = 1.25f;
                beta = -0.5f;
            }
            String prefix = fp64 ? "CLBlastD" : "CLBlastS";
            int bytes = fp64 ? 8 : 4;
            check(prefix + "gemm", FunctionDescriptor.of(C_INT, C_INT, C_INT, C_INT, C_LONG, C_LONG, C_LONG, scalar,
                            C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, scalar, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER),
                    new Object[] { 101, 111, 112, 2, 3, 4, alpha, null, 4, null, 4, beta, null, 3 },
                    List.of(101, 111, 112, 2L, 3L, 4L, alpha, 0x1700L, 88L / bytes, 4L, 0x1900L, 104L / bytes, 4L, beta, 0x1c00L, 128L / bytes, 3L, QUEUE, 0L));
            check(prefix + "gemv", FunctionDescriptor.of(C_INT, C_INT, C_INT, C_LONG, C_LONG, scalar, C_POINTER, C_LONG, C_LONG,
                            C_POINTER, C_LONG, C_LONG, scalar, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER),
                    new Object[] { 102, 112, 2, 3, alpha, null, 2, null, 2, beta, null, 3 },
                    List.of(102, 112, 2L, 3L, alpha, 0x1500L, 72L / bytes, 2L, 0x1700L, 88L / bytes, 2L, beta, 0x1a00L, 112L / bytes, 3L, QUEUE, 0L));
            check(prefix + "axpy", FunctionDescriptor.of(C_INT, C_LONG, scalar, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER),
                    new Object[] { 3, alpha, null, 2, null, 3 },
                    List.of(3L, alpha, 0x1200L, 48L / bytes, 2L, 0x1400L, 64L / bytes, 3L, QUEUE, 0L));
            check(prefix + "dot", FunctionDescriptor.of(C_INT, C_LONG, C_POINTER, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER),
                    new Object[] { 3, null, 2, null, 3, null },
                    List.of(3L, 0x1500L, 72L / bytes, 0x1100L, 40L / bytes, 2L, 0x1300L, 56L / bytes, 3L, QUEUE, 0L));
        }
    }

    private static void check(String name, FunctionDescriptor descriptor, Object[] args, List<Object> expected) throws ReflectiveOperationException {
        try (Arena arena = Arena.ofConfined()) {
            Capture capture = new Capture();
            CLBlastNativeLib library = library(name, descriptor, capture, arena);
            library.dispatch(name, invocation(args), QUEUE);
            assertEquals(expected, capture.arguments, name);
            capture.status = -1017;
            TornadoRuntimeException error = assertThrows(TornadoRuntimeException.class, () -> library.dispatch(name, invocation(args), QUEUE));
            assertTrue(error.getMessage().contains(name));
            assertTrue(error.getMessage().contains("-1017"));
        }
    }

    @Test
    void marshalsStrmmThroughItsCAbi() throws ReflectiveOperationException {
        check("CLBlastStrmm", FunctionDescriptor.of(C_INT, C_INT, C_INT, C_INT, C_INT, C_INT, C_LONG, C_LONG, C_FLOAT,
                        C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER),
                new Object[] { 101, 142, 122, 112, 132, 2, 3, -0.75f, null, 5, null, 4 },
                List.of(101, 142, 122, 112, 132, 2L, 3L, -0.75f, 0x1800L, 24L, 5L, 0x1a00L, 28L, 4L, QUEUE, 0L));
    }

    @Test
    void optionalStrmmDoesNotDisableExistingRoutines() throws ReflectiveOperationException {
        try (Arena arena = Arena.ofConfined()) {
            // Only symbol discovery is tested here; these handles are never called.
            MemorySegment stub = stub(FunctionDescriptor.of(C_INT), new Capture(), arena);
            CLBlastNativeLib baseline = new CLBlastNativeLib(name -> name.equals("CLBlastStrmm") ? Optional.empty() : Optional.of(stub));
            assertTrue(baseline.isAvailable());
            assertThrows(TornadoRuntimeException.class, () -> baseline.dispatch("CLBlastStrmm", invocation(new Object[12]), QUEUE));
            CLBlastNativeLib extended = new CLBlastNativeLib(name -> Optional.of(stub));
            assertTrue(extended.isAvailable());
        }
    }

    @Test
    void rejectsMissingSymbols() {
        CLBlastNativeLib library = new CLBlastNativeLib(name -> Optional.empty());
        assertFalse(library.isAvailable());
        assertThrows(TornadoRuntimeException.class, () -> library.dispatch("CLBlastSgemm", invocation(new Object[14]), QUEUE));
    }

    @Test
    void rejectsNegativeDimensionsAndIncrementsBeforeNativeCall() throws ReflectiveOperationException {
        try (Arena arena = Arena.ofConfined()) {
            Capture capture = new Capture();
            String name = "CLBlastSaxpy";
            FunctionDescriptor descriptor = FunctionDescriptor.of(C_INT, C_LONG, C_FLOAT, C_POINTER, C_LONG, C_LONG, C_POINTER, C_LONG, C_LONG, C_POINTER, C_POINTER);
            CLBlastNativeLib library = library(name, descriptor, capture, arena);
            assertThrows(TornadoRuntimeException.class, () -> library.dispatch(name, invocation(new Object[] { 3, 1f, null, 1, null, 1 }), 0));
            assertThrows(IllegalArgumentException.class, () -> library.dispatch(name, invocation(new Object[] { -1, 1f, null, 1, null, 1 }), QUEUE));
            assertThrows(IllegalArgumentException.class, () -> library.dispatch(name, invocation(new Object[] { 3, 1f, null, -1, null, 1 }), QUEUE));
            assertThrows(IllegalArgumentException.class, () -> library.dispatch(name, invocation(new Object[] { 3, 1f, null, 0, null, 1 }), QUEUE));
        }
    }

    @Test
    void rejectsMisalignedOffsets() {
        LibraryInvocation invocation = new LibraryInvocation(new Object[1], new long[1], new long[] { 123 }, new long[] { 3 }, new boolean[] { true }, null, 0, null, null, false);
        assertThrows(IllegalArgumentException.class, () -> CLBlastNativeLib.elementOffset(invocation, 0, Float.BYTES));
    }
}
