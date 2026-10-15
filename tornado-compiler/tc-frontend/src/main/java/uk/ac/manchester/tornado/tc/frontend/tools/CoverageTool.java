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
package uk.ac.manchester.tornado.tc.frontend.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.ir.Verifier;

/**
 * Frontend coverage: translates every candidate kernel method in a jar and reports which
 * translate to verified TC-SSA and, for the others, which diagnostic stopped them. A candidate is a
 * static method with a TornadoVM native array, a {@code KernelContext} or a primitive array among
 * its parameters.
 *
 * <pre>tc-coverage &lt;jar&gt; [package-prefix] [--tasks] [--list] [--calls]</pre>
 *
 * <p>{@code --tasks} restricts the run to the methods registered as tasks ({@link uk.ac.manchester.tornado.tc.frontend.TaskSites}).</p>
 */
public final class CoverageTool {

    private CoverageTool() {
    }

    public static void main(String[] args) throws IOException {
        var jar = args[0];
        var prefix = args.length > 1 && !args[1].startsWith("--") ? args[1].replace('.', '/') : "";
        var list = List.of(args).contains("--list");
        var tasksOnly = List.of(args).contains("--tasks");
        var kernels = new java.util.HashSet<String>();
        var source = Frontend.BytecodeSource.classpath(jar);
        int candidates = 0;
        int translated = 0;
        int invalid = 0;
        int needsInlining = 0;
        int needsParallel = 0;
        Map<String, Integer> byCode = new TreeMap<>();
        Map<String, List<String>> examples = new TreeMap<>();
        Map<String, Integer> calls = new TreeMap<>();
        try (var zip = new ZipFile(jar)) {
            var names = zip.stream().map(e -> e.getName()).filter(n -> n.endsWith(".class") && n.startsWith(prefix) && !n.contains("module-info")).sorted().toList();
            for (var entry : names) {
                var internal = entry.substring(0, entry.length() - ".class".length());
                ClassFile classFile;
                try (var in = zip.getInputStream(zip.getEntry(entry))) {
                    classFile = ClassFile.parse(in.readAllBytes());
                }
                for (var k : uk.ac.manchester.tornado.tc.frontend.TaskSites.kernels(classFile)) {
                    kernels.add(k.owner() + "." + k.name() + k.descriptor());
                }
            }
            for (var entry : names) {
                var internal = entry.substring(0, entry.length() - ".class".length());
                ClassFile classFile;
                try (var in = zip.getInputStream(zip.getEntry(entry))) {
                    classFile = ClassFile.parse(in.readAllBytes());
                }
                for (var method : classFile.methods()) {
                    var isTask = kernels.contains(internal + "." + method.name() + method.descriptor());
                    if (!method.isStatic() || method.code().isEmpty() || (tasksOnly ? !isTask : !isCandidate(method.descriptor()))) {
                        continue;
                    }
                    candidates++;
                    var result = Frontend.translate(source, internal.replace('/', '.'), method.name(), method.descriptor());
                    var where = internal.substring(internal.lastIndexOf('/') + 1) + "." + method.name();
                    if (result.function().isPresent()) {
                        var problems = Verifier.verify(result.function().get());
                        var callees = result.function().get().blocks().stream().flatMap(b -> b.insts().stream()) //
                                .filter(i -> i.opcode() instanceof uk.ac.manchester.tornado.tc.ir.Opcode.Call) //
                                .map(i -> ((uk.ac.manchester.tornado.tc.ir.Opcode.Call) i.opcode()).callee()).toList();
                        if (problems.isEmpty() && callees.isEmpty()) {
                            translated++;
                            if (method.code().get().typeAnnotations().length > 0) {
                                needsParallel++;
                            }
                        } else if (problems.isEmpty()) {
                            needsInlining++;
                            callees.forEach(c -> calls.merge("(static, to inline) " + c, 1, Integer::sum));
                        } else {
                            invalid++;
                            record(byCode, examples, "invalid IR " + problems.get(0).code(), where + ": " + problems.get(0).message());
                        }
                    } else {
                        var d = result.diagnostics().get(0);
                        record(byCode, examples, d.code() + " " + shorten(d.message()), where);
                        var m = java.util.regex.Pattern.compile("call to (\\S+?)\\(").matcher(d.message());
                        if (m.find()) {
                            calls.merge(m.group(1), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        System.out.printf("candidates %d, translated to verified IR without calls %d (%.1f%%), verified but calling helpers (needs inlining, O1) %d, invalid IR %d, rejected %d%n", candidates,
                translated, 100.0 * translated / Math.max(1, candidates), needsInlining, invalid, candidates - translated - needsInlining - invalid);
        System.out.printf("  of the translated kernels, %d carry type annotations (@Parallel): they need T2 parallel-loop lowering before they are semantically complete%n", needsParallel);
        if (List.of(args).contains("--calls")) {
            System.out.println("unsupported call targets:");
            calls.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40).forEach(e -> System.out.printf("%6d  %s%n", e.getValue(), e.getKey()));
        }
        byCode.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(e -> {
            System.out.printf("%6d  %s%n", e.getValue(), e.getKey());
            if (list) {
                examples.get(e.getKey()).stream().limit(5).forEach(x -> System.out.println("          e.g. " + x));
            }
        });
    }

    private static void record(Map<String, Integer> byCode, Map<String, List<String>> examples, String key, String example) {
        byCode.merge(key, 1, Integer::sum);
        examples.computeIfAbsent(key, k -> new ArrayList<>()).add(example);
    }

    /** Groups messages that differ only in the member they name. */
    private static String shorten(String message) {
        return message.replaceAll("call to \\S+", "call to <method>").replaceAll("field \\S+", "field <name>").replaceAll("bytecode '(\\w+)'", "bytecode '$1'");
    }

    private static boolean isCandidate(String descriptor) {
        var params = descriptor.substring(1, descriptor.indexOf(')'));
        return params.contains("Luk/ac/manchester/tornado/api/types/arrays/") || params.contains("Luk/ac/manchester/tornado/api/KernelContext;") || params.contains("[");
    }
}
