/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.fuzz.gen;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import uk.ac.manchester.tornado.api.KernelContext;

/**
 * Compiles a generated kernel class source in-process with {@code javax.tools}
 * and loads it through a classloader whose parent is the one that loaded
 * {@code tornado-api}, so the generated class resolves KernelContext / array
 * types and TornadoVM's Graal bytecode reader can read it back.
 */
public final class InProcessCompiler {

    private final Path workDir;
    private final String classpath;
    private final boolean useSystemLoader;

    public InProcessCompiler() throws IOException {
        // If a gen directory is supplied AND it is on the launch -cp, the generated
        // class can be loaded by the system classloader, which is the only loader
        // TornadoVM's ASMClassVisitor consults (via getSystemClassLoader().getResourceAsStream).
        // Otherwise fall back to an isolated URLClassLoader (Graal can read it, but the
        // annotation visitor cannot locate the .class resource).
        String genDir = System.getProperty("tornado.fuzz.genDir");
        if (genDir != null) {
            this.workDir = Files.createDirectories(Path.of(genDir));
            this.useSystemLoader = true;
        } else {
            this.workDir = Files.createTempDirectory("tornado-fuzz-gen");
            this.useSystemLoader = false;
        }
        this.classpath = deriveClasspath();
    }

    /**
     * Compile a single top-level public class and return the loaded {@link Class}.
     *
     * @param fqcn   fully-qualified class name (e.g. {@code uk.ac.manchester.tornado.fuzz.generated.G1})
     * @param source complete Java source for that class
     */
    public Class<?> compileAndLoad(String fqcn, String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler (run on a JDK, not a JRE)");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        // For system-loader mode, emit straight into the -cp directory so the class is
        // discoverable by name and as a resource; otherwise a private per-class dir.
        Path out = useSystemLoader ? workDir : Files.createDirectories(workDir.resolve(fqcn.replace('.', '_')));

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(out.toFile()));
            List<String> options = new ArrayList<>();
            options.add("-classpath");
            options.add(classpath);
            options.add("-source");
            options.add("21");
            options.add("-target");
            options.add("21");
            // TornadoVM's array types are compiled with JDK 21 preview features enabled.
            options.add("--enable-preview");
            options.add("-proc:none");
            // TornadoVM's code generator reads the LocalVariableTable, which javac only
            // emits with debug info. Without -g the sketch compilation NPEs.
            options.add("-g");

            JavaFileObject file = new StringSource(fqcn, source);
            boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, List.of(file)).call();
            if (!ok) {
                StringBuilder sb = new StringBuilder("In-process compilation failed:\n");
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    sb.append(d).append('\n');
                }
                throw new IllegalStateException(sb.toString());
            }
        }

        if (useSystemLoader) {
            // Resolvable by the system classloader because `out` is on the launch -cp.
            return Class.forName(fqcn, true, ClassLoader.getSystemClassLoader());
        }
        URLClassLoader loader = new URLClassLoader(new URL[] { out.toUri().toURL() }, KernelContext.class.getClassLoader());
        return Class.forName(fqcn, true, loader);
    }

    /** True when generated classes load via the system classloader (required for CUDA JIT). */
    public boolean usesSystemLoader() {
        return useSystemLoader;
    }

    /** Classpath the generated class compiles against: the packaged TornadoVM module jars. */
    private static String deriveClasspath() {
        String home = System.getenv("TORNADOVM_HOME");
        List<String> entries = new ArrayList<>();
        if (home != null) {
            Path jars = Path.of(home, "share", "java", "tornado");
            if (Files.isDirectory(jars)) {
                try (Stream<Path> s = Files.list(jars)) {
                    s.filter(p -> p.toString().endsWith(".jar")).forEach(p -> entries.add(p.toString()));
                } catch (IOException ignored) {
                }
            }
        }
        // Fall back to the code source of tornado-api if TORNADOVM_HOME is unset.
        if (entries.isEmpty()) {
            try {
                URI uri = KernelContext.class.getProtectionDomain().getCodeSource().getLocation().toURI();
                entries.add(Path.of(uri).toString());
            } catch (Exception ignored) {
            }
        }
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String code;

        StringSource(String fqcn, String code) {
            super(URI.create("string:///" + fqcn.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
