/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the on-disk directory a backend uses for its code cache, generated sources or
 * compiler logs.
 *
 * <p>
 * Each backend used to do this itself, with the same line in all three:
 *
 * <pre>
 * Paths.get(System.getenv("TORNADOVM_HOME") + "/" + dir + "/" + deviceDir)
 * </pre>
 *
 * String concatenation, so a configured directory could only ever land underneath
 * {@code TORNADOVM_HOME}. Setting {@code -Dtornado.cuda.codecache.dir=/tmp/cache} produced
 * {@code $TORNADOVM_HOME/tmp/cache/device-0-0} -- the absolute path silently ignored and the
 * cache written into the SDK installation instead, which fails outright when the SDK is
 * read-only or shared between users. And with {@code TORNADOVM_HOME} unset the concatenation
 * yields the string {@code "null/..."}, so a directory literally named {@code null} appeared
 * in the working directory.
 *
 * <p>
 * Resolution here is the conventional one:
 *
 * <ul>
 * <li>an <b>absolute</b> configured directory is used as given;</li>
 * <li>a <b>relative</b> one is resolved against {@code TORNADOVM_HOME};</li>
 * <li>if {@code TORNADOVM_HOME} is unset or blank, a relative directory falls back to
 * {@code ${java.io.tmpdir}/tornadovm}, which keeps an embedded use working instead of
 * writing to {@code null/}.</li>
 * </ul>
 *
 * <p>
 * The per-backend defaults are relative ({@code var/cuda-codecache} and friends), so the
 * default location is unchanged: {@code $TORNADOVM_HOME/var/cuda-codecache/device-0-0}.
 */
public final class CodeCacheDirectory {

    private static final String TORNADOVM_HOME = "TORNADOVM_HOME";
    private static final String FALLBACK_DIRECTORY_NAME = "tornadovm";

    private CodeCacheDirectory() {
    }

    /**
     * Resolves {@code configuredDirectory}, then appends the per-device subdirectory.
     *
     * @param configuredDirectory
     *     value of the backend's {@code *.dir} property. Absolute is honoured as given;
     *     relative is resolved against {@code TORNADOVM_HOME}.
     * @param deviceDirectory
     *     per-device leaf, e.g. {@code device-0-0}.
     *
     * @return the directory to use. Not created -- the caller does that.
     */
    public static Path resolve(String configuredDirectory, String deviceDirectory) {
        return resolveBase(configuredDirectory).resolve(deviceDirectory);
    }

    /**
     * The configured directory without the per-device leaf. Exposed for tests and for callers
     * that do not partition by device.
     */
    public static Path resolveBase(String configuredDirectory) {
        final Path configured = Paths.get(configuredDirectory);
        if (configured.isAbsolute()) {
            return configured;
        }
        return root().resolve(configured);
    }

    private static Path root() {
        final String home = System.getenv(TORNADOVM_HOME);
        if (home == null || home.isBlank()) {
            // Not fatal: TornadoVM is usable as a library, where the launcher never runs and so
            // never exports TORNADOVM_HOME. Writing to "null/" was the previous behaviour and was
            // never intentional.
            return Paths.get(System.getProperty("java.io.tmpdir")).resolve(FALLBACK_DIRECTORY_NAME);
        }
        return Paths.get(home);
    }
}
