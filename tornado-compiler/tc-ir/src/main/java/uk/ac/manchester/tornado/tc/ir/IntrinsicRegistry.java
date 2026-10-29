/*
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
package uk.ac.manchester.tornado.tc.ir;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The one description of every intrinsic TC can produce: its effects, and the immediates it
 * carries. Passes ask the registry what an intrinsic does (purity, memory effects, convergence)
 * instead of interpreting its name, the verifier rejects intrinsics it does not know, and every
 * dialect's spelling table covers these names.
 *
 * <p>An entry names one intrinsic, or a family by prefix ({@code math.}, {@code tile.binary.}) whose
 * members are generated from the TornadoVM and Java APIs; the prefix is interpreted here and only
 * here. Names identify operations; parameters that vary over open sets (a lane, a dimension, a
 * callee, a launch mode) are immediates.</p>
 */
public final class IntrinsicRegistry {

    /** What executing an intrinsic may do besides computing its result from its operands. */
    public enum Effect {
        /** Reads memory. */
        READS,
        /** Writes memory. */
        WRITES,
        /** Has an effect beyond memory or must happen exactly as written (atomics, printing, launches, allocation). */
        SIDE_EFFECT,
        /** Every thread of a group or warp must reach it together: it may not be moved across control flow. */
        CONVERGENT
    }

    /** A registered intrinsic or family; {@code effects} empty means pure. */
    public record Def(String name, boolean family, Set<Effect> effects, List<String> immediates, String description) {
        public boolean isPure() {
            return effects.isEmpty();
        }

        boolean covers(String intrinsic) {
            return family ? intrinsic.startsWith(name) : intrinsic.equals(name);
        }
    }

    private static final List<Def> DEFS = new ArrayList<>();

    private static void pure(String name, String description, String... immediates) {
        add(name, EnumSet.noneOf(Effect.class), description, immediates);
    }

    private static void add(String name, Set<Effect> effects, String description, String... immediates) {
        DEFS.add(new Def(name, name.endsWith("."), Set.copyOf(effects), List.of(immediates), description));
    }

    static {
        // Launch geometry: fixed for a launch.
        pure("tornado.thread_id.global", "global thread index in a dimension", "dim");
        pure("tornado.thread_id.local", "thread index within the work-group", "dim");
        pure("tornado.group_id", "work-group index", "dim");
        pure("tornado.group_size.local", "work-group size", "dim");
        pure("tornado.global_size", "global size (threads in the launch)", "dim");
        pure("tornado.group_count", "number of work-groups", "dim");
        // Math (Java semantics; the spelling tables give each dialect's functions).
        pure("math.", "math functions of TornadoMath and java.lang.Math");
        // Vector values.
        pure("tornado.vector.make", "a vector from its lanes");
        pure("tornado.vector.lane", "one lane of a vector", "lane");
        pure("tornado.vector.insert", "a vector with one lane replaced", "lane");
        pure("tornado.vector.lane_at", "a lane by an index operand, until unrolling makes it constant");
        pure("tornado.vector.insert_at", "a lane replaced by an index operand, until unrolling makes it constant");
        add("tornado.vector.load", EnumSet.of(Effect.READS), "a vector from consecutive elements of an array (operands: data, byte offset); one wide load where the address allows");
        add("tornado.vector.store", EnumSet.of(Effect.WRITES), "a vector to consecutive elements of an array (operands: data, byte offset, vector); one wide store where the address allows");
        add("tornado.vector.store_param", EnumSet.of(Effect.WRITES), "writes a changed vector parameter back to its lanes in memory");
        // Synchronisation, warp and tensor-core operations.
        add("tornado.barrier.", EnumSet.of(Effect.CONVERGENT, Effect.READS, Effect.WRITES), "work-group barrier");
        add("tornado.simd.", EnumSet.of(Effect.CONVERGENT), "warp (SIMD-group) operations");
        add("tornado.mma.store", EnumSet.of(Effect.CONVERGENT, Effect.WRITES), "stores a tensor-core result fragment");
        add("tornado.mma.", EnumSet.of(Effect.CONVERGENT, Effect.READS), "tensor-core fragments and multiply-accumulate (warp-collective)");
        add("tornado.simdgroup_matrix.store", EnumSet.of(Effect.CONVERGENT, Effect.WRITES), "stores an Apple simdgroup-matrix fragment");
        add("tornado.simdgroup_matrix.", EnumSet.of(Effect.CONVERGENT, Effect.READS), "Apple simdgroup-matrix fragments and multiply-accumulate (SIMD-group collective)");
        // Atomics and other effects.
        add("tornado.atomic.", EnumSet.of(Effect.SIDE_EFFECT, Effect.READS, Effect.WRITES), "atomic read-modify-write on memory");
        add("tornado.atomic_int.", EnumSet.of(Effect.SIDE_EFFECT, Effect.READS, Effect.WRITES), "AtomicInteger counter in the atomics region");
        add("tornado.async_copy", EnumSet.of(Effect.SIDE_EFFECT, Effect.READS, Effect.WRITES), "asynchronous global-to-shared copy of a 4- or 16-byte slot", "bytes", "elem_bytes");
        add("tornado.async_copy.", EnumSet.of(Effect.SIDE_EFFECT, Effect.READS, Effect.WRITES), "commits and waits for groups of asynchronous copies");
        add("tornado.printf", EnumSet.of(Effect.SIDE_EFFECT), "Debug.printf", "format");
        add("tornado.device_launch", EnumSet.of(Effect.SIDE_EFFECT, Effect.READS, Effect.WRITES), "a kernel launch from the device", "callee", "descriptor", "mode");
        add("tornado.local_array.length", EnumSet.noneOf(Effect.class), "the element count of a pending local array");
        add("tornado.local_array", EnumSet.of(Effect.SIDE_EFFECT), "a local array whose size is known after specialisation", "space", "elem");
        pure("tornado.local_collection", "a vector collection allocated in the kernel (removed after inlining)");
        // Frontend-only compile-time operands, read by Debug.printf and KernelContext.launch and removed
        // before the function leaves the frontend.
        pure("tornado.string", "a string constant (a Debug.printf format)", "value");
        pure("tornado.device_kernel", "the static method a DeviceKernel field names", "owner", "name", "descriptor");
        pure("tornado.varargs", "the Object[] of a varargs call", "length");
        add("tornado.varargs.set", EnumSet.of(Effect.WRITES), "an element of a varargs array", "index");
        add("tornado.dp4a.packed", EnumSet.noneOf(Effect.class), "dot product of packed int8 words");
        add("tornado.dp4a", EnumSet.of(Effect.READS), "dot product of int8 words read from memory");
        // CUDA Tile (TileContext): block geometry, views (element type an immediate or a DType operand) and extracts.
        pure("tile.bid", "tile block index", "dim");
        pure("tile.num_blocks", "number of tile blocks", "dim");
        pure("tile.view", "a tensor view of an array");
        pure("tile.view_strided", "a strided two-dimensional tensor view of an array");
        pure("tile.extract", "a sub-tile", "rank");
        // CUDA Tile (TileContext): values and views are pure; memory accesses are not.
        add("tile.load", EnumSet.of(Effect.READS), "tile load through a partition view");
        add("tile.load_masked", EnumSet.of(Effect.READS), "masked tile load");
        add("tile.atomic_load", EnumSet.of(Effect.READS, Effect.SIDE_EFFECT), "atomic tile load");
        add("tile.store", EnumSet.of(Effect.WRITES), "tile store through a partition view");
        add("tile.store_masked", EnumSet.of(Effect.WRITES), "masked tile store");
        add("tile.atomic_store", EnumSet.of(Effect.WRITES, Effect.SIDE_EFFECT), "atomic tile store");
        add("tile.atomic.", EnumSet.of(Effect.SIDE_EFFECT, Effect.READS, Effect.WRITES), "tile atomic read-modify-write");
        pure("tile.", "tile values, views and arithmetic (CUDA Tile)");
        // Frontend markers that never reach code generation.
        add("tc.vector_param_unchanged", EnumSet.of(Effect.SIDE_EFFECT), "frontend check: a device function must not change a vector argument");
        // Longest match first, so a specific entry wins over its family.
        DEFS.sort(Comparator.comparingInt((Def d) -> d.name().length()).reversed());
    }

    private IntrinsicRegistry() {
    }

    /** The entry describing {@code intrinsic} (an exact entry wins over the longest family). */
    public static Optional<Def> lookup(String intrinsic) {
        for (var def : DEFS) {
            if (def.covers(intrinsic)) {
                return Optional.of(def);
            }
        }
        return Optional.empty();
    }

    public static List<Def> all() {
        return List.copyOf(DEFS);
    }
}
