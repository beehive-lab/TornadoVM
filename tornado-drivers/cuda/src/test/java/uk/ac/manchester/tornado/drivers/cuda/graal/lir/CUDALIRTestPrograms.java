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

import java.util.ArrayList;
import java.util.List;

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.lir.LIRInstruction;
import tornado.graal.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture.CUDAMemoryBase;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.ExprStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.LoadStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.MoveStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.StoreStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDABarrierNode.CUDAMemFenceFlags;

/**
 * Builds small CUDA LIR sequences for the code-generator tests.
 *
 * <p>
 * It lives in {@code ...graal.lir} on purpose: {@link CUDAUnary.MemoryAccess} has a
 * package-private constructor, so an address operand cannot be built from outside this
 * package. Nothing here is used by production code.
 */
final class CUDALIRTestPrograms {

    private static final LIRKind FLOAT_KIND = LIRKind.value(CUDAKind.FLOAT);
    private static final LIRKind LONG_KIND = LIRKind.value(CUDAKind.LONG);

    private int nextIndex = 1;

    Variable variable() {
        return new Variable(FLOAT_KIND, nextIndex++);
    }

    /** A variable with a caller-chosen index, so a test can deliberately reuse one. */
    Variable variable(int index) {
        return new Variable(FLOAT_KIND, index);
    }

    /** {@code v = *(global float *) address} — the load the pass is allowed to hoist. */
    LoadStmt globalLoad(Variable result, Value address) {
        return load(CUDAArchitecture.globalSpace, result, address);
    }

    /** {@code v = shared[address]} — a reader of staged data; must terminate a run. */
    LoadStmt sharedLoad(Variable result, Value address) {
        return load(CUDAArchitecture.localSpace, result, address);
    }

    LoadStmt privateLoad(Variable result, Value address) {
        return load(CUDAArchitecture.privateSpace, result, address);
    }

    /** {@code shared[address] = value} — the store the pass is allowed to sink. */
    StoreStmt sharedStore(Value address, Value value) {
        return store(CUDAArchitecture.localSpace, address, value);
    }

    StoreStmt privateStore(Value address, Value value) {
        return store(CUDAArchitecture.privateSpace, address, value);
    }

    /** {@code *(global float *) address = value} — never sunk. */
    StoreStmt globalStore(Value address, Value value) {
        return store(CUDAArchitecture.globalSpace, address, value);
    }

    /** Pure register arithmetic: the address computation between two loads. */
    AssignStmt assign(Variable result, Value operand) {
        return new AssignStmt(result, operand);
    }

    /** An {@link AssignStmt} whose right-hand side is a nested LIR operator. */
    AssignStmt assignExpression(Variable result, Value operand) {
        return new AssignStmt(result, new CUDAUnary.Expr(CUDAUnaryIntrinsic.ABS, LONG_KIND, operand));
    }

    /** {@code __syncthreads()}. Not eligible, so it ends a run. */
    ExprStmt barrier() {
        return new ExprStmt(new CUDAUnary.Barrier(CUDAUnaryIntrinsic.BARRIER, CUDAMemFenceFlags.LOCAL));
    }

    /** Any instruction outside the pass's whitelist. */
    MoveStmt opaque(Variable result, Value operand) {
        return new MoveStmt(result, operand);
    }

    private LoadStmt load(CUDAMemoryBase base, Variable result, Value address) {
        return new LoadStmt(result, new CUDAUnary.CUDAAddressCast(base, FLOAT_KIND), new CUDAUnary.MemoryAccess(base, address));
    }

    private StoreStmt store(CUDAMemoryBase base, Value address, Value value) {
        return new StoreStmt(new CUDAUnary.CUDAAddressCast(base, FLOAT_KIND), new CUDAUnary.MemoryAccess(base, address), value);
    }

    static List<LIRInstruction> list(LIRInstruction... ops) {
        List<LIRInstruction> l = new ArrayList<>();
        for (LIRInstruction op : ops) {
            l.add(op);
        }
        return l;
    }
}
