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
package uk.ac.manchester.tornado.tc.runtime.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/** {@code tc emit}: every kernel of a directory, CUDA target options, compiler-style diagnostics. */
public class TcTest {

    /** The frontend's test kernels, compiled against tornado-api, and tornado-api itself. */
    private static final Path KERNELS = Path.of("../tc-frontend/target/test-classes");
    private static final String PACKAGE = "uk/ac/manchester/tornado/tc/frontend/kernels/";
    private static final String API = "../../tornado-api/target/classes";

    private record Result(int status, String out, String err) {
    }

    private static Result run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int status = Tc.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /** A directory holding only the given test kernel classes, as javac would leave them. */
    private static Path classes(Path dir, String... simpleNames) throws IOException {
        for (var name : simpleNames) {
            var target = dir.resolve(PACKAGE + name + ".class");
            Files.createDirectories(target.getParent());
            Files.copy(KERNELS.resolve(PACKAGE + name + ".class"), target);
        }
        return dir;
    }

    private static List<String> kernelNames(String simpleName) throws IOException {
        return Tc.kernels(ClassFile.parse(Files.readAllBytes(KERNELS.resolve(PACKAGE + simpleName + ".class")))).stream().map(Tc.Kernel::name).toList();
    }

    @Test
    public void deviceLaunchedChildrenAreNotKernelsOfTheirOwn() throws IOException {
        var names = kernelNames("LaunchKernels");
        assertTrue(names.contains("parent"), names.toString());
        assertFalse(names.contains("ping") || names.contains("pong") || names.contains("scale"), names.toString());
    }

    @Test
    public void kernelsAreListedInSourceOrder() throws IOException {
        var names = kernelNames("BasicKernels");
        assertEquals(List.of("add", "compute", "blockSum"), names.subList(0, 3));
    }

    @Test
    public void kernelParametersArePrimitivesArraysOrApiTypes() {
        assertTrue(Tc.kernelParameters("(Luk/ac/manchester/tornado/api/KernelContext;[FI)V"));
        assertFalse(Tc.kernelParameters("()V"));
        assertFalse(Tc.kernelParameters("(Ljava/lang/String;)V"));
        assertFalse(Tc.kernelParameters("([Ljava/lang/Object;)V"));
    }

    @Test
    public void allCompilesEveryKernelOfTheDirectoryAndResolvesDeviceKernels(@TempDir Path dir) throws IOException {
        classes(dir, "LaunchKernels");
        var r = run("emit", "--all", "--cp", dir + File.pathSeparator + API);
        assertTrue(r.out().contains("// ===== uk.ac.manchester.tornado.tc.frontend.kernels.LaunchKernels.parent ====="), r.out());
        // The DeviceKernel field is resolved through the loaded class: the parent launches its child.
        assertTrue(r.out().contains("<<<"), r.out() + r.err());
    }

    @Test
    public void computeCapabilityAndPrecisionsReachTheTarget() {
        var caps = Tc.target(TargetCaps.Dialect.CUDA, 90, false, null);
        assertEquals(90, caps.computeCapability());
        assertFalse(caps.fp16());
        assertTrue(caps.fp64());
        assertTrue(caps.deviceLaunch());
        assertEquals(TargetCaps.METAL_APPLE, Tc.target(TargetCaps.Dialect.METAL, 89, null, null));
    }

    @Test
    public void refusalsNameTheSourceLineLikeACompiler(@TempDir Path dir) throws IOException {
        classes(dir, "TileKernels");
        var r = run("emit", "--target", "opencl", "--cp", dir + File.pathSeparator + API, "uk.ac.manchester.tornado.tc.frontend.kernels.TileKernels", "gemm");
        assertEquals(1, r.status());
        assertTrue(r.err().matches("(?s)TileKernels\\.java:\\d+: error: TC-E4010 .*"), r.err());
    }

    @Test
    public void usageErrors() {
        assertEquals(2, run("emit").status());
        assertEquals(2, run("emit", "--all", "Extra", "arg").status());
        assertEquals(2, run("emit", "--fp16", "maybe", "--all").status());
    }
}
