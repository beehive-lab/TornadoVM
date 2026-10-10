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
package uk.ac.manchester.tornado.tc.ir;

import java.util.Map;
import java.util.Optional;

/**
 * Every diagnostic code TC reports, with what it means for the program and how to fix it. The
 * runtime turns the category into a TornadoVM exception (see the TC hook); hints are shown with the
 * error message. A test checks that every code in the sources is listed here.
 */
public final class ErrorCodes {

    /** What a diagnostic says about the kernel. */
    public enum Category {
        /** The kernel cannot run on a GPU as written (any compiler rejects it); the user must change it. */
        INVALID_KERNEL,
        /** A construct the GPU can run that TC does not compile yet; the Graal path may. */
        UNSUPPORTED,
        /** A TC error that a pattern of the kernel provokes but that is not the user's fault to fix: TC gives up on the kernel. */
        BAILOUT,
        /** A bug in TC (an invariant broken); please report it with the kernel. */
        INTERNAL
    }

    public record Entry(Category category, String hint) {
    }

    private static final Map<String, Entry> CODES = Map.ofEntries(
            // Frontend: Java bytecode the kernel model cannot express.
            Map.entry("TC-E1001", new Entry(Category.INVALID_KERNEL, "kernels cannot catch exceptions; check inputs on the host before the launch")),
            Map.entry("TC-E1002", new Entry(Category.INVALID_KERNEL, "kernels cannot allocate objects, use null, strings or arrays of references; pass data in arrays or TornadoVM types")),
            Map.entry("TC-E1003", new Entry(Category.UNSUPPORTED, "this call or field is not compiled by TC yet; -Dtornado.compiler.fallback=true lets the Graal path compile the kernel")),
            Map.entry("TC-E1004", new Entry(Category.INVALID_KERNEL, "the size of a kernel-local array must be a constant or a task argument")),
            Map.entry("TC-E1006", new Entry(Category.INTERNAL, "the frontend lost track of a local variable")),
            Map.entry("TC-E1007", new Entry(Category.INVALID_KERNEL, "the class of a called method is not on the class path of the task")),
            Map.entry("TC-E1008", new Entry(Category.INTERNAL, "a class file could not be read")),
            Map.entry("TC-E1009", new Entry(Category.INVALID_KERNEL, "kernels cannot recurse; rewrite the recursion as a loop")),
            Map.entry("TC-E1011", new Entry(Category.BAILOUT, "kernels cannot allocate objects; create them on the host and pass them as task arguments")),
            Map.entry("TC-E1012", new Entry(Category.UNSUPPORTED, "a helper method that changes a vector argument (setX, set) is not compiled by TC yet; return the changed vector, or the Graal path compiles it")),
            Map.entry("TC-E1010", new Entry(Category.INVALID_KERNEL, "vector lanes and tensor-core accumulator elements are registers: index them with a constant")),
            // IR text and verifier: broken invariants inside TC.
            Map.entry("TC-E2001", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2002", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2003", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2004", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2005", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2006", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2007", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2101", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2102", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2103", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2104", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2105", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2106", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2107", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2108", new Entry(Category.INVALID_KERNEL, "a kernel-local array needs at least one element")),
            Map.entry("TC-E2109", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2110", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2111", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2112", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2113", new Entry(Category.INVALID_KERNEL, "shared (local) memory can only be allocated in the kernel itself, not in a called method")),
            Map.entry("TC-E2114", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2116", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E2117", new Entry(Category.INTERNAL, "")), Map.entry("TC-E2118", new Entry(Category.INTERNAL, "")),
            // Structuring and TornadoVM lowering.
            Map.entry("TC-E2115", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E3001", new Entry(Category.BAILOUT, "the loops of this kernel jump into each other (irreducible control flow); restructure the loops")),
            Map.entry("TC-E3002", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E3101", new Entry(Category.UNSUPPORTED, "this object argument cannot be described to TC (its class is not the declared one, or it overrides methods); the Graal path may compile it")),
            Map.entry("TC-E3102", new Entry(Category.UNSUPPORTED, "this field of an object argument cannot be accessed on the device yet")),
            Map.entry("TC-E3103", new Entry(Category.BAILOUT, "the bounds of @Parallel loops must be known when the task is compiled: constants, task arguments or array sizes")),
            Map.entry("TC-E3201", new Entry(Category.BAILOUT, "a @Parallel loop must count i = start; i < end; i += step with a step that does not change in the loop")),
            Map.entry("TC-E3202", new Entry(Category.BAILOUT, "at most three @Parallel loops can be nested (x, y, z)")),
            Map.entry("TC-E3204", new Entry(Category.BAILOUT, "a @Reduce reduction must be out[k] = out[k] op value (op: + * min max), one per @Parallel loop, on every iteration")),
            Map.entry("TC-E3205", new Entry(Category.INVALID_KERNEL, "an AtomicInteger must be created in the kernel with a constant initial value, or passed as an argument")),
            Map.entry("TC-E3206", new Entry(Category.UNSUPPORTED, "batched execution (withBatch) is not compiled by TC yet; the Graal path supports it")),
            Map.entry("TC-E3207", new Entry(Category.UNSUPPORTED, "atomic counters in a kernel launched from the device (KernelContext.launch) are not compiled by TC yet; the Graal path compiles them")),
            Map.entry("TC-E3301", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E3401", new Entry(Category.INVALID_KERNEL, "a tile shape, axis or element type must be a compile-time constant (a literal or a static final); shapes must be powers of two")),
            Map.entry("TC-E3402", new Entry(Category.INVALID_KERNEL, "the tile operands do not fit together (rank, broadcast or element type); see the TileContext documentation")),
            Map.entry("TC-E3403", new Entry(Category.UNSUPPORTED, "this TileContext operation is not compiled by TC yet; the Graal path supports it")),
            Map.entry("TC-E4010", new Entry(Category.UNSUPPORTED, "CUDA Tile needs compute capability 8.0 or higher (Ampere and newer); rewrite the task with KernelContext for the SIMT path")),
            Map.entry("TC-E4011", new Entry(Category.UNSUPPORTED, "fp8 tiles need compute capability 9.0 (Hopper) or newer; use F16 or BF16 operands on this device")),
            // Code generation.
            Map.entry("TC-E4001", new Entry(Category.UNSUPPORTED, "this type or operation has no CUDA spelling in TC yet")),
            Map.entry("TC-E4002", new Entry(Category.UNSUPPORTED, "this intrinsic has no CUDA spelling in TC yet")),
            // Internal.
            Map.entry("TC-E9001", new Entry(Category.INTERNAL, "a pass produced invalid IR")),
            Map.entry("TC-E9002", new Entry(Category.INTERNAL, "")),
            Map.entry("TC-E9003", new Entry(Category.INTERNAL, "")));

    private ErrorCodes() {
    }

    public static Optional<Entry> lookup(String code) {
        return Optional.ofNullable(CODES.get(code));
    }

    /** The category of {@code code}; an unknown code is treated as an internal error. */
    public static Category category(String code) {
        return lookup(code).map(Entry::category).orElse(Category.INTERNAL);
    }

    public static String hint(String code) {
        return lookup(code).map(Entry::hint).orElse("");
    }

    public static java.util.Set<String> codes() {
        return CODES.keySet();
    }
}
