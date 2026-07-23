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

/**
 * Renders a generated {@link Expr} into a compilable Java class. The class holds
 * three members built from the SAME expression text:
 * <ul>
 *   <li>{@code fuzzedKernel} — the KernelContext device kernel,</li>
 *   <li>{@code reference} — the JVM golden loop (same expression),</li>
 *   <li>{@code task()} — a factory returning a method reference so the lambda's
 *       defining classloader can resolve the kernel (see {@link Phase2Spike}).</li>
 * </ul>
 * Method name is {@code fuzzedKernel} because {@code kernel} is a reserved
 * CUDA/OpenCL token.
 */
public final class KernelSourceEmitter {

    public static final String PACKAGE = "uk.ac.manchester.tornado.fuzz.generated";

    private KernelSourceEmitter() {
    }

    public static String fqcn(String className) {
        return PACKAGE + "." + className;
    }

    public static String emit(String className, String exprText) {
        return "package " + PACKAGE + ";\n\n" //
                + "import uk.ac.manchester.tornado.api.KernelContext;\n" //
                + "import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;\n" //
                + "import uk.ac.manchester.tornado.api.types.arrays.IntArray;\n\n" //
                + "public final class " + className + " {\n" //
                + "    public static void fuzzedKernel(KernelContext c, IntArray a, IntArray b, IntArray out) {\n" //
                + "        int i = c.globalIdx;\n" //
                + "        out.set(i, " + exprText + ");\n" //
                + "    }\n\n" //
                + "    public static void reference(IntArray a, IntArray b, IntArray out) {\n" //
                + "        for (int i = 0; i < out.getSize(); i++) {\n" //
                + "            out.set(i, " + exprText + ");\n" //
                + "        }\n" //
                + "    }\n\n" //
                + "    public static Task4<KernelContext, IntArray, IntArray, IntArray> task() {\n" //
                + "        return " + className + "::fuzzedKernel;\n" //
                + "    }\n" //
                + "}\n";
    }

    /** The kernel method alone, for the debug bundle / JUnit reproducer. */
    public static String methodText(String exprText) {
        return "public static void fuzzedKernel(KernelContext context, IntArray a, IntArray b, IntArray out) {\n" //
                + "    int i = context.globalIdx;\n" //
                + "    out.set(i, " + exprText + ");\n" //
                + "}";
    }
}
