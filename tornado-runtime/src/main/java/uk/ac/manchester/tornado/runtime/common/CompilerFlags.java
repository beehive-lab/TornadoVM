/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.runtime.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;

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
}
