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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;

/**
 * {@code tc}, the headless compiler: compiles one kernel method from class files to CUDA C++,
 * OpenCL C or MSL and prints it. No GPU and no TornadoVM runtime are needed. Class bytes are read
 * from the class path given on the command line, never loaded, so the same code runs as a GraalVM
 * native image.
 *
 * <pre>
 *   tc emit [--target cuda|opencl|metal] [--cp PATH] [--stats] Class method
 * </pre>
 *
 * The default class path is {@code .} plus {@code $TORNADOVM_HOME/share/java/tornado/tornado-api*.jar}
 * when that is set. Exit status: 0 on success, 1 when TC refuses the kernel (its diagnostics are
 * printed), 2 on a usage error.
 */
public final class Tc {

    private Tc() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length < 1 || !args[0].equals("emit")) {
            return usage();
        }
        var target = TargetCaps.CUDA_SM89;
        String classPath = defaultClassPath();
        boolean stats = false;
        var positional = new ArrayList<String>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--target" -> {
                    if (++i == args.length) {
                        return usage();
                    }
                    switch (args[i]) {
                        case "cuda" -> target = TargetCaps.CUDA_SM89;
                        case "opencl" -> target = TargetCaps.OPENCL_GENERIC;
                        case "metal" -> target = TargetCaps.METAL_APPLE;
                        default -> {
                            return usage();
                        }
                    }
                }
                case "--cp", "-cp", "--classpath" -> {
                    if (++i == args.length) {
                        return usage();
                    }
                    classPath = args[i];
                }
                case "--stats" -> stats = true;
                default -> positional.add(args[i]);
            }
        }
        if (positional.size() != 2) {
            return usage();
        }
        var source = Frontend.BytecodeSource.classpath(classPath);
        var c = TcCompiler.compileJava(source, positional.get(0), positional.get(1), target, TcOptions.DEFAULTS, List.of());
        if (!c.succeeded()) {
            c.diagnostics().forEach(d -> System.err.println("[TC] " + d.code() + " " + d.message() + (d.where().isBlank() ? "" : " in " + d.where())));
            return 1;
        }
        System.out.print(c.source());
        if (stats) {
            System.err.printf("[TC] %s: %.2f ms (class files: %s)%n", c.entryName(), c.nanos() / 1e6, ClassFile.readerDescription());
        }
        return 0;
    }

    private static String defaultClassPath() {
        var entries = new ArrayList<String>();
        entries.add(".");
        var home = System.getenv("TORNADOVM_HOME");
        var jars = home == null ? null : new File(home, "share/java/tornado").listFiles((dir, name) -> name.startsWith("tornado-api") && name.endsWith(".jar"));
        if (jars != null) {
            for (var jar : jars) {
                entries.add(jar.getPath());
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    private static int usage() {
        System.err.println("usage: tc emit [--target cuda|opencl|metal] [--cp PATH] [--stats] Class method");
        return 2;
    }
}
