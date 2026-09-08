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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Tests for {@link CodeCacheDirectory}.
 *
 * <p>
 * The behaviour these pin down is what the three backends got wrong for years: a configured
 * absolute directory was concatenated onto {@code TORNADOVM_HOME} rather than used, so the
 * cache went into the SDK installation instead of where it was asked to go.
 *
 * <p>
 * {@code TORNADOVM_HOME} is an environment variable and cannot be set from a test without
 * reflecting into the JDK's environment map, so the tests are written to hold whether or not
 * it happens to be set in the running shell. Where the expected value depends on it, it is
 * read rather than assumed.
 */
public class CodeCacheDirectoryTest {

    private static final String DEVICE = "device-0-0";

    private static String home() {
        final String home = System.getenv("TORNADOVM_HOME");
        return home == null || home.isBlank() ? null : home;
    }

    /** The regression: an absolute directory is used as given, never appended to the SDK root. */
    @Test
    public void anAbsoluteDirectoryIsHonoured() {
        Path resolved = CodeCacheDirectory.resolve("/tmp/tornado-cache-test", DEVICE);
        assertEquals(Paths.get("/tmp/tornado-cache-test", DEVICE), resolved);
    }

    /** Specifically: it does not land under TORNADOVM_HOME, which is what used to happen. */
    @Test
    public void anAbsoluteDirectoryDoesNotLandUnderTornadoHome() {
        final String home = home();
        if (home == null) {
            return; // nothing to be under
        }
        Path resolved = CodeCacheDirectory.resolve("/tmp/tornado-cache-test", DEVICE);
        assertTrue("absolute path was resolved under TORNADOVM_HOME: " + resolved, !resolved.startsWith(Paths.get(home)));
    }

    /** A relative directory keeps the historical location, so defaults do not move. */
    @Test
    public void aRelativeDirectoryResolvesAgainstTornadoHomeWhenSet() {
        final String home = home();
        if (home == null) {
            return;
        }
        assertEquals(Paths.get(home, "var/cuda-codecache", DEVICE), CodeCacheDirectory.resolve("var/cuda-codecache", DEVICE));
    }

    /**
     * With no {@code TORNADOVM_HOME} the result must still be an absolute, usable path -- the
     * previous code produced the literal string {@code "null/var/..."}.
     */
    @Test
    public void aRelativeDirectoryNeverResolvesToTheStringNull() {
        Path resolved = CodeCacheDirectory.resolve("var/cuda-codecache", DEVICE);
        assertTrue("resolved path is absolute", resolved.isAbsolute());
        for (Path element : resolved) {
            assertEquals("a path element is the literal string \"null\": " + resolved, false, "null".equals(element.toString()));
        }
    }

    /** The per-device leaf is always the last element. */
    @Test
    public void theDeviceDirectoryIsTheLeaf() {
        assertEquals(Paths.get(DEVICE), CodeCacheDirectory.resolve("/tmp/tornado-cache-test", DEVICE).getFileName());
        assertEquals(Paths.get("device-1-2"), CodeCacheDirectory.resolve("var/x", "device-1-2").getFileName());
    }

    /** resolveBase is resolve without the leaf. */
    @Test
    public void resolveBaseOmitsTheDeviceDirectory() {
        assertEquals(CodeCacheDirectory.resolveBase("/tmp/tornado-cache-test").resolve(DEVICE), CodeCacheDirectory.resolve("/tmp/tornado-cache-test", DEVICE));
    }

    /** A trailing separator or a redundant one must not change the result. */
    @Test
    public void redundantSeparatorsAreNormalised() {
        assertEquals(CodeCacheDirectory.resolve("/tmp/tornado-cache-test", DEVICE), CodeCacheDirectory.resolve("/tmp//tornado-cache-test/", DEVICE));
    }
}
