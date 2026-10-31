/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.runtime.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.options.CudaFlags;
import uk.ac.manchester.tornado.api.options.TcFlags;

/**
 * Combines two compiler option strings so that the second overrides the first: how a plan's
 * {@code withCompilerFlags} is added to a backend's default flags, on every backend.
 *
 * <p>
 * Plain concatenation is not enough. NVRTC rejects an option given twice ({@code --use_fast_math
 * (-use_fast_math) defined more than once}), even when both occurrences agree, so
 * {@code -Dtornado.cuda.compile.profile=fast} with {@code --use_fast_math} given again failed to
 * compile every kernel; and two values for one option ({@code -cl-std=CL2.0} and
 * {@code -cl-std=CL3.0}) must not both reach the OpenCL compiler. Instead, an option in the
 * overriding string replaces any option of the same name in the base, and everything else from
 * both is kept, base first.
 * </p>
 *
 * <p>
 * Names are compared without leading dashes and without an {@code =value}, with NVRTC's short
 * aliases mapped to their long form, so {@code -lineinfo} replaces {@code --generate-line-info}
 * and {@code --fmad=true} replaces {@code --fmad=false}. Options compilers accept more than once
 * (include paths, macro definitions, pre-includes) are never treated as duplicates.
 * </p>
 */
public final class CompilerFlags {

    /** NVRTC short spellings and the long option each one is an alias of. */
    private static final Map<String, String> ALIASES = Map.of("lineinfo", "generate-line-info", //
            "G", "device-debug", //
            "arch", "gpu-architecture", //
            "rdc", "relocatable-device-code", //
            "ewp", "extensible-whole-program", //
            "dlto", "dlink-time-opt", //
            "w", "disable-warnings");

    /** Options that may legitimately appear several times; repeating them is not an override. */
    private static final List<String> REPEATABLE = List.of("I", "include-path", "D", "define-macro", "U", "undefine-macro", "include", "pre-include");

    private CompilerFlags() {
    }

    /**
     * The NVRTC options every CUDA kernel is compiled with: those of {@code tornado.cuda.compile.profile}, merged with
     * {@code tornado.cuda.compiler.flags}, plus what {@code tornado.enable.fma=false} needs.
     *
     * <p>
     * The CUDA backend fuses a multiply and an add at two independent stages, so turning off one leaves the other.
     * {@code enable.fma=false} stops {@code CUDAFMAPhase} emitting an {@code fma()} call; {@code --fmad=false} stops
     * NVRTC re-contracting the separated multiply and add on the way to PTX. Both are needed to get the host's two
     * roundings. {@code --fmad=false} is only added when nothing has already specified {@code --fmad}: NVRTC rejects
     * it when given twice, and the {@code repro} profile sets it. An explicit setting wins.
     * </p>
     */
    public static String cudaDefaults() {
        return CudaDefaults.VALUE;
    }

    /**
     * Returns {@code base} with every option that {@code overrides} also sets replaced by the
     * overriding one, followed by the overriding options base did not have.
     *
     * @param base
     *     whitespace-separated NVRTC options, e.g. the profile's; may be null or blank
     * @param overrides
     *     whitespace-separated NVRTC options that win; may be null or blank
     * @return the merged options, single-space separated, never null
     */
    public static String merge(String base, String overrides) {
        List<String> overriding = options(overrides);
        List<String> overriddenNames = new ArrayList<>();
        for (String option : overriding) {
            String name = name(option);
            if (!REPEATABLE.contains(name)) {
                overriddenNames.add(name);
            }
        }
        List<String> merged = new ArrayList<>();
        for (String option : options(base)) {
            if (!overriddenNames.contains(name(option))) {
                merged.add(option);
            }
        }
        merged.addAll(overriding);
        return String.join(" ", merged);
    }

    /**
     * The flags a plan compiles with after {@code withCompilerFlags(backend, explicit)}: the
     * backend's defaults with {@code explicit} merged in, plus what a strict-floating-point plan
     * requires of that backend's compiler. Starts again from the defaults, so a second call replaces
     * the first.
     *
     * @param backend
     *     the backend the flags are for
     * @param defaults
     *     that backend's JVM-wide default flags
     * @param explicit
     *     the flags given to the plan; may be null or blank
     * @param strictFloatingPoint
     *     whether the plan asked for {@code withStrictFloatingPoint()}
     * @return the merged options, never null
     */
    public static String forPlan(TornadoVMBackendType backend, String defaults, String explicit, boolean strictFloatingPoint) {
        String merged = merge(defaults, explicit);
        if (strictFloatingPoint && backend == TornadoVMBackendType.CUDA) {
            // NVRTC must not contract the multiply and add the compiler kept apart. An explicit
            // --fmad wins, as it does in withStrictFloatingPoint().
            merged = merge("--fmad=false", merged);
        }
        return merged;
    }

    /**
     * Splits an option string into options, keeping a value given as a separate word ({@code -I
     * /usr/include}) with the option it belongs to.
     */
    private static List<String> options(String flags) {
        List<String> result = new ArrayList<>();
        if (flags == null || flags.isBlank()) {
            return result;
        }
        for (String word : flags.trim().split("\\s+")) {
            if (!word.startsWith("-") && !result.isEmpty()) {
                result.set(result.size() - 1, result.getLast() + " " + word);
            } else {
                result.add(word);
            }
        }
        return result;
    }

    private static String name(String option) {
        String name = option.split("[=\\s]", 2)[0].replaceFirst("^-+", "");
        // -I/usr/include and -DNAME=1 carry their value without a separator.
        for (String attached : List.of("I", "D", "U")) {
            if (name.length() > 1 && name.startsWith(attached) && !option.startsWith("--")) {
                return attached;
            }
        }
        return ALIASES.getOrDefault(name, name);
    }

    /** Computed once, the first time it is needed, as the options it reads are fixed for the run. */
    private static final class CudaDefaults {

        private static final String VALUE = compute();

        private static String compute() {
            String stated = merge(CudaCompileProfile.parse(CudaFlags.COMPILE_PROFILE.get()).getFlags(), CudaFlags.COMPILER_FLAGS.get());
            String fmaContraction = TcFlags.ENABLE_FMA.get() || stated.contains("-fmad") ? "" : "--fmad=false";
            return (stated + " " + fmaContraction).trim().replaceAll("\\s+", " ");
        }
    }
}
