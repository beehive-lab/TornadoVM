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
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.Value;

/**
 * A LIR statement that computes one value from registers alone.
 *
 * <p>
 * Implementing this is an assertion about the emitted code, and a scheduler is entitled to
 * rely on it. All three parts must hold:
 *
 * <ol>
 * <li><b>It touches no memory.</b> The emitted C neither dereferences a pointer nor indexes
 * an array — in any address space. A statement that emits {@code *(} or {@code [} for a
 * memory access does not qualify, however pure it looks otherwise.</li>
 * <li><b>It has no cross-thread effect.</b> No barrier, no warp vote, no shuffle, no
 * {@code mma}/{@code ldmatrix}: nothing whose result depends on, or is observed by, another
 * lane.</li>
 * <li><b>It defines exactly one value</b>, returned by {@link #getDefinedValue()}, and
 * defines nothing else.</li>
 * </ol>
 *
 * <p>
 * Statements that deliberately do <em>not</em> qualify, with the reason, so the next person
 * does not have to re-derive it:
 *
 * <ul>
 * <li>{@code Dp4aStmt} — its inline PTX dereferences both operand pointers;</li>
 * <li>{@code LdmatrixStmt} — {@code ldmatrix.sync...shared.b16} reads shared memory;</li>
 * <li>{@code ShuffleSyncStmt}, {@code WarpVoteStmt} — cross-lane;</li>
 * <li>{@code MMAComputeStmt} — cross-lane, and its operands are fragment arrays;</li>
 * <li>{@code CastCompressedStmt}, {@code SwizzledLoadFP16Stride32Stmt} — both dereference.</li>
 * </ul>
 *
 * <p>
 * The point of an opt-in marker rather than a list held elsewhere is that the assertion sits
 * beside the {@code emitCode} that has to justify it, and a statement added later is excluded
 * until someone deliberately includes it.
 *
 * @see uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDAGlobalLoadBatching
 */
public interface PureRegisterComputation {

    /** The single value this statement defines. */
    Value getDefinedValue();
}
