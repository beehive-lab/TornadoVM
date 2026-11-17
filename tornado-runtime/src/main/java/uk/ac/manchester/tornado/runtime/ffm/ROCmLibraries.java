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
package uk.ac.manchester.tornado.runtime.ffm;

import java.io.File;
import java.lang.foreign.SymbolLookup;
import java.util.ArrayList;
import java.util.List;


/**
 * Finds the ROCm libraries the HIP backend and the ROCm library tasks bind. Each library is tried by
 * absolute path under the ROCm install first ({@code ROCM_PATH}, {@code HIP_PATH}, {@code /opt/rocm}),
 * then by its bare name for the loader's search path. The install wins because a bare name can resolve
 * to a different, stale copy: on Windows a display driver may place its own {@code amdhip64_*.dll} in
 * System32, ahead of the SDK on every search.
 */
public final class ROCmLibraries {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("windows");

    private static final String[] LIB_DIRS = { "lib", "lib64", "bin" };

    private ROCmLibraries() {
    }

    /** The ROCm install roots, in the order they are searched. */
    public static List<String> roots() {
        List<String> roots = new ArrayList<>();
        for (String variable : new String[] { "ROCM_PATH", "HIP_PATH", "ROCM_HOME" }) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank() && !roots.contains(value)) {
                roots.add(value);
            }
        }
        if (!WINDOWS && !roots.contains("/opt/rocm")) {
            roots.add("/opt/rocm");
        }
        return roots;
    }

    /**
     * Opens the first library that loads: {@code linuxNames} (sonames, newest first) on Linux and
     * {@code windowsNames} (DLL names, newest first) on Windows, each under every ROCm root before the
     * bare name.
     *
     * @return the lookup, or {@code null} when no candidate loads.
     */
    public static SymbolLookup load(List<String> linuxNames, List<String> windowsNames) {
        List<String> names = WINDOWS ? windowsNames : linuxNames;
        List<String> candidates = new ArrayList<>();
        for (String root : roots()) {
            for (String directory : LIB_DIRS) {
                for (String name : names) {
                    File file = new File(root + File.separator + directory, name);
                    if (file.isFile()) {
                        candidates.add(file.getAbsolutePath());
                    }
                }
            }
        }
        candidates.addAll(names);
        for (String candidate : candidates) {
            SymbolLookup lookup = FFMSupport.loadLibrary(candidate);
            if (lookup != null) {
                return lookup;
            }
        }
        return null;
    }
}
