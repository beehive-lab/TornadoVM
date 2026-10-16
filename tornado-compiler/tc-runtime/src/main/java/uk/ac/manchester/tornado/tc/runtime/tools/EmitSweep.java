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
package uk.ac.manchester.tornado.tc.runtime.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipFile;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.frontend.TaskSites;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;

/**
 * Compiles every task kernel of a jar (found at its {@code .task(...)} call sites) for one target,
 * headlessly, and writes each emitted source to a directory: a whole-suite corpus for targets this
 * machine cannot run (Metal), to be checked offline ({@code utils/mslcheck}).
 * {@code tc-emit-sweep <classpath> <jar> --target=cuda|opencl|metal --out DIR}. Kernels are not
 * specialised (no argument facts), so the sources are the generic form of each kernel.
 */
public final class EmitSweep {

    private EmitSweep() {
    }

    public static void main(String[] args) throws IOException {
        var classpath = args[0];
        var jar = args[1];
        var target = TargetCaps.CUDA_SM89;
        var extension = ".cu";
        Path out = Path.of("tc-sweep");
        for (var a : List.of(args).subList(2, args.length)) {
            if (a.equals("--target=opencl")) {
                target = TargetCaps.OPENCL_GENERIC;
                extension = ".cl";
            } else if (a.equals("--target=metal")) {
                target = TargetCaps.METAL_APPLE;
                extension = ".metal";
            } else if (a.startsWith("--out=")) {
                out = Path.of(a.substring("--out=".length()));
            }
        }
        Files.createDirectories(out);
        var kernels = new TreeSet<String>();
        var refs = new TreeMap<String, MemberRef>();
        try (var zip = new ZipFile(jar)) {
            for (var entry : zip.stream().map(e -> e.getName()).filter(n -> n.endsWith(".class") && !n.contains("module-info")).sorted().toList()) {
                try (var in = zip.getInputStream(zip.getEntry(entry))) {
                    for (var k : TaskSites.kernels(ClassFile.parse(in.readAllBytes()))) {
                        var key = k.owner() + "." + k.name() + k.descriptor();
                        if (kernels.add(key)) {
                            refs.put(key, k);
                        }
                    }
                }
            }
        }
        var source = Frontend.BytecodeSource.classpath(classpath);
        var options = new TcOptions(java.util.Set.of(), out, false, java.util.Optional.empty(), false);
        Map<String, Integer> refused = new TreeMap<>();
        int emitted = 0;
        for (var k : refs.values()) {
            TcCompiler.Compilation c;
            try {
                c = TcCompiler.compileJava(source, k.owner().replace('/', '.'), k.name(), k.descriptor(), target, options, List.of());
            } catch (RuntimeException e) {
                refused.merge("exception " + e.getClass().getSimpleName(), 1, Integer::sum);
                continue;
            }
            if (c.succeeded()) {
                var name = k.owner().substring(k.owner().lastIndexOf('/') + 1).replace('$', '_') + "_" + k.name() + "_" + Integer.toHexString(k.descriptor().hashCode());
                Files.writeString(out.resolve(name + extension), c.source());
                emitted++;
            } else {
                var d = c.diagnostics().get(0);
                refused.merge(d.code() + " " + d.message().replaceAll("[0-9]+", "N").replaceAll("\\(.*", "").trim(), 1, Integer::sum);
            }
        }
        System.out.printf("%d kernels, %d emitted to %s%n", refs.size(), emitted, out);
        refused.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(e -> System.out.printf("  %4d  %s%n", e.getValue(), e.getKey()));
    }
}
