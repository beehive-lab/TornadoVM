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

/**
 * Named bundle of hipRTC options, selected with {@code -Dtornado.hip.compile.profile=<name>}: the HIP
 * counterpart of {@link CudaCompileProfile}. Flags set explicitly through
 * {@code tornado.hip.compiler.flags} or {@code withCompilerFlags(HIP, ...)} are added to the profile's,
 * an explicit option replacing the profile's option of the same name (see {@link CompilerFlags}).
 */
public enum HipCompileProfile {

    /** Whatever hipRTC does by default: no extra options. */
    DEFAULT(""),

    /** Trade floating-point strictness for speed. */
    FAST("-ffast-math"),

    /** Keep line-number information in the code object so profilers can attribute samples to the generated HIP C++. */
    DEBUG("-gline-tables-only"),

    /**
     * Reproducible arithmetic: no fast-math and no FMA contraction, so a kernel's result does not depend
     * on how the compiler fused its multiplies and adds.
     */
    REPRO("-ffp-contract=off");

    private final String flags;

    HipCompileProfile(String flags) {
        this.flags = flags;
    }

    public String getFlags() {
        return flags;
    }

    public static HipCompileProfile parse(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return switch (value.trim().toLowerCase()) {
            case "default", "none" -> DEFAULT;
            case "fast" -> FAST;
            case "debug", "lineinfo" -> DEBUG;
            case "repro", "deterministic" -> REPRO;
            default -> {
                System.err.println("[TornadoVM] unknown tornado.hip.compile.profile value '" + value + "', using 'default'. " //
                        + "Valid values: default, fast, debug, repro.");
                yield DEFAULT;
            }
        };
    }
}
