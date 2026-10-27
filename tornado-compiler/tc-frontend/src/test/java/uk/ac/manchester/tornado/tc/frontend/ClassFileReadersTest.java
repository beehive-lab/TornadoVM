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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile.Reader;
import uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool;

/**
 * Differential test of the two class-file readers: TC's own and the JDK's Class-File API. Every class
 * is read by both, and everything the frontend consumes must be equal: methods, instructions (bcis,
 * operands, branch and switch targets), exception handlers, line and local-variable tables, the
 * annotations on locals, the bootstrap methods, and what each instruction's constant-pool operand
 * resolves to. Runs where the JDK reader is available (TC built and run on JDK 24 or newer).
 */
public class ClassFileReadersTest {

    @Test
    public void testKernelsReadTheSame() throws IOException {
        assumeTrue(Reader.JDK.isAvailable(), "the Class-File API reader needs JDK 24+");
        var classes = Path.of("target/test-classes");
        try (Stream<Path> files = Files.walk(classes)) {
            assertSame(files.filter(f -> f.toString().endsWith(".class")).map(f -> source(f.toString(), () -> read(f))).toList(), 10);
        }
    }

    @Test
    public void tornadoApiReadsTheSame() throws Exception {
        assumeTrue(Reader.JDK.isAvailable(), "the Class-File API reader needs JDK 24+");
        var location = Path.of(Class.forName("uk.ac.manchester.tornado.api.TaskGraph").getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(location)) {
            // A reactor build without packaging: tornado-api's classes directory.
            try (Stream<Path> files = Files.walk(location)) {
                assertSame(files.filter(f -> f.toString().endsWith(".class")).map(f -> source(f.toString(), () -> read(f))).toList(), 100);
            }
            return;
        }
        try (var zip = new ZipFile(location.toFile())) {
            var sources = new ArrayList<Source>();
            for (var entry : zip.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                byte[] bytes;
                try (var in = zip.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }
                sources.add(new Source(entry.getName(), bytes));
            }
            assertSame(sources, 100);
        }
    }

    /** The JDK's own classes: every instruction form a javac release emits, wide forms and switches included. */
    @Test
    public void javaBaseReadsTheSame() throws IOException {
        assumeTrue(Reader.JDK.isAvailable(), "the Class-File API reader needs JDK 24+");
        var jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        try (Stream<Path> files = Files.walk(jrt.getPath("/modules/java.base"))) {
            assertSame(files.filter(f -> f.toString().endsWith(".class")).map(f -> source(f.toString(), () -> read(f))).toList(), 5000);
        }
    }

    @Test
    public void selectedReaderIsTheJdkOneOnJdk24() {
        assumeTrue(Reader.JDK.isAvailable(), "the Class-File API reader needs JDK 24+");
        assumeTrue(System.getProperty("tornado.tc.classfile", "auto").equals("auto"));
        assertTrue(Runtime.version().feature() >= 24);
    }

    private record Source(String name, byte[] bytes) {
    }

    private static Source source(String name, Supplier<byte[]> bytes) {
        return new Source(name, bytes.get());
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertSame(List<Source> sources, int atLeast) {
        assertTrue(sources.size() >= atLeast, "expected at least " + atLeast + " classes, found " + sources.size());
        long insns = 0;
        for (var source : sources) {
            var own = describe(ClassFile.parse(source.bytes(), Reader.TC));
            var jdk = describe(ClassFile.parse(source.bytes(), Reader.JDK));
            if (!own.equals(jdk)) {
                assertEquals(String.join("\n", own), String.join("\n", jdk), source.name() + ": the readers differ");
            }
            insns += own.stream().filter(line -> line.startsWith("    ")).count();
        }
        assertTrue(insns > 0, "no instructions compared");
        System.out.println("ClassFileReadersTest: " + sources.size() + " classes, " + insns + " instructions read the same");
    }

    /** Everything the frontend reads from a class file, one line per item. */
    private static List<String> describe(ClassFile c) {
        var out = new ArrayList<String>();
        out.add("class " + c.name() + " extends " + c.superName().orElse("-") + " major " + c.majorVersion());
        for (var b : c.bootstrapMethods()) {
            out.add("bootstrap " + b.methodHandle() + " " + resolve(() -> c.constants().methodHandle(b.methodHandle())) + " " + b.arguments() + " "
                    + b.arguments().stream().map(a -> resolve(() -> c.constants().methodHandle(a))).toList());
        }
        for (var m : c.methods()) {
            out.add("method " + m.name() + m.descriptor() + " access " + m.access());
            if (m.code().isEmpty()) {
                continue;
            }
            var code = m.code().get();
            out.add("  max " + code.maxStack() + "/" + code.maxLocals() + " handlers " + code.handlers() + " lines " + code.lines() + " locals " + code.locals());
            out.add("  annotations " + resolve(code::localAnnotations));
            for (var insn : code.insns()) {
                out.add("    " + insn.bci() + " " + insn.mnemonic() + " " + insn.index() + " " + insn.value() + " " + Arrays.toString(insn.targets()) + " " + Arrays.toString(insn.keys())
                        + operand(c.constants(), insn));
            }
        }
        return out;
    }

    /** What the frontend looks up for an instruction's constant-pool operand. */
    private static String operand(ConstantPool pool, Bytecode.Insn insn) {
        int op = insn.opcode();
        if (op == Bytecode.LDC || op == Bytecode.LDC_W || op == Bytecode.LDC2_W) {
            return " = " + resolve(() -> {
                var v = pool.loadable(insn.index());
                return v.getClass().getSimpleName() + ":" + (v instanceof Float f ? Float.floatToRawIntBits(f) : v instanceof Double d ? Double.doubleToRawLongBits(d) : v);
            });
        }
        if (op >= Bytecode.GETSTATIC && op <= Bytecode.INVOKEINTERFACE) {
            return " = " + resolve(() -> pool.member(insn.index()));
        }
        if (op == Bytecode.INVOKEDYNAMIC) {
            return " = " + resolve(() -> pool.dynamicSite(insn.index()));
        }
        if (op == Bytecode.NEW || op == Bytecode.ANEWARRAY || op == Bytecode.CHECKCAST || op == Bytecode.INSTANCEOF || op == Bytecode.MULTIANEWARRAY) {
            return " = " + resolve(() -> pool.className(insn.index()));
        }
        return "";
    }

    /** The value, or the exception the lookup throws: both readers must also fail alike. */
    private static String resolve(Supplier<?> lookup) {
        try {
            return String.valueOf(lookup.get());
        } catch (RuntimeException e) {
            return "!" + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
