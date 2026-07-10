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
package uk.ac.manchester.tornado.runtime.graal.phases.sketcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

/**
 * Locks in the WP2 correctness fix: surviving-intrinsic recognition is scoped to the
 * {@code KernelContext} declaring class + an exact method-name set, so a user method that merely
 * contains "atomicAdd"/"mmaStore" in its name is not a false positive.
 */
public class TornadoDataflowAnalysisMatchTest {

    private static final String KERNEL_CONTEXT = "uk.ac.manchester.tornado.api.KernelContext";
    private static final Set<String> ATOMIC_ADD = Set.of("atomicAdd");
    private static final Set<String> MMA_STORE = Set.of("mmaStore", "mmaStoreInt", "mmaStoreBSwizzled");

    @Test
    public void matchesRealKernelContextMethods() {
        assertTrue(TornadoDataflowAnalysis.matchesKernelContextMethod(KERNEL_CONTEXT, "atomicAdd", ATOMIC_ADD));
        assertTrue(TornadoDataflowAnalysis.matchesKernelContextMethod(KERNEL_CONTEXT, "mmaStore", MMA_STORE));
        assertTrue(TornadoDataflowAnalysis.matchesKernelContextMethod(KERNEL_CONTEXT, "mmaStoreBSwizzled", MMA_STORE));
    }

    @Test
    public void rejectsWrongDeclaringClass() {
        assertFalse(TornadoDataflowAnalysis.matchesKernelContextMethod("com.example.MyKernels", "atomicAdd", ATOMIC_ADD));
    }

    @Test
    public void rejectsSubstringImposters() {
        // The old substring match (name.contains("atomicAdd")) would wrongly accept these.
        assertFalse(TornadoDataflowAnalysis.matchesKernelContextMethod(KERNEL_CONTEXT, "myAtomicAddHelper", ATOMIC_ADD));
        assertFalse(TornadoDataflowAnalysis.matchesKernelContextMethod(KERNEL_CONTEXT, "atomicAddFast", ATOMIC_ADD));
        assertFalse(TornadoDataflowAnalysis.matchesKernelContextMethod(KERNEL_CONTEXT, "doMmaStoreThing", MMA_STORE));
    }
}
