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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;

/** Unit tests of the TC-SSA Java API (the counterpart of LLVM's unittests). */
public class IrApiTest {

    /** Builds the VectorAdd kernel of the IR specification with {@link IrBuilder}. */
    private static Function vectorAdd() {
        var f = new Function("VectorAdd.add", Function.Kind.KERNEL, Type.VOID);
        var entry = f.newBlock("entry");
        var a = entry.addArg(new Type.ArrayType(Type.F32), "a");
        var b = entry.addArg(new Type.ArrayType(Type.F32), "b");
        var c = entry.addArg(new Type.ArrayType(Type.F32), "c");
        var head = f.newBlock("head");
        var i = head.addArg(Type.I32, "i");
        head.addAttribute(Attribute.of("parallel", "iv", 0));
        var body = f.newBlock("body");
        var exit = f.newBlock("exit");

        var builder = new IrBuilder(entry);
        var n = builder.at(new Loc("VectorAdd.java", 20, -1)).arrayLength(c);
        n.setName("n");
        builder.at(Loc.UNKNOWN);
        var i0 = builder.intrinsic("tornado.thread_id.global", Immediates.of("dim", 0), Type.I32, List.of());
        var stride = builder.intrinsic("tornado.global_size", Immediates.of("dim", 0), Type.I32, List.of());
        builder.br(head, List.of(i0));

        builder.atEnd(head);
        var more = builder.icmp(IntPredicate.SLT, i, n);
        builder.condBr(more, body, List.of(), exit, List.of());

        builder.atEnd(body);
        var sum = builder.fadd(builder.arrayLoad(a, i), builder.arrayLoad(b, i));
        builder.arrayStore(c, i, sum);
        builder.br(head, List.of(builder.add(i, stride)));

        builder.atEnd(exit).ret();
        return f;
    }

    @Test
    public void builtKernelVerifies() {
        var diagnostics = Verifier.verify(vectorAdd());
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    }

    @Test
    public void builtKernelPrintsAndRoundTrips() {
        var text = IrPrinter.print(vectorAdd());
        assertTrue(text.contains("^head(%i: i32) [parallel<iv = 0>]:"), text);
        assertTrue(text.contains("%n = array.length %c : i32  loc(\"VectorAdd.java\":20)"), text);
        assertEquals(text, IrPrinter.print(IrParser.parseFunction(text)));
    }

    @Test
    public void replaceAllUsesWithUpdatesOperandsAndBranchArguments() {
        var f = vectorAdd();
        var entry = f.entry();
        var i0 = entry.insts().get(1);
        var builder = new IrBuilder(entry).before(i0);
        var zero = builder.constInt(Type.I32, 0);
        i0.replaceAllUsesWith(zero);
        assertFalse(i0.hasUses());
        var branch = entry.terminator().orElseThrow();
        assertEquals(zero, branch.successors().get(0).args().get(0));
        assertEquals(1, zero.users().size());
        i0.erase();
        assertTrue(Verifier.verify(f).isEmpty());
    }

    @Test
    public void erasingAValueWithUsersIsRejected() {
        var f = vectorAdd();
        var n = f.entry().insts().get(0);
        assertThrows(IllegalStateException.class, n::erase);
    }

    @Test
    public void detachedOperandIsReported() {
        var f = vectorAdd();
        var other = new Function("other", Function.Kind.DEVICE, Type.VOID);
        var foreign = other.newBlock("entry").addArg(Type.I32, "x");
        var exit = f.blocks().get(3);
        var ret = exit.terminator().orElseThrow();
        new IrBuilder(exit).before(ret).add(foreign, foreign);
        var diagnostics = Verifier.verify(f);
        assertEquals(2, diagnostics.size());
        assertEquals("TC-E2110", diagnostics.get(0).code());
    }

    /** Cached CFG analyses are reused while the CFG is unchanged and recomputed after any edit to it. */
    @Test
    public void cfgAnalysesFollowEveryCfgEdit() {
        var f = IrParser.parseFunction("""
                device @f(%c: i1) -> i32 {
                ^entry:
                  cond_br %c, ^a, ^b
                ^a:
                  %one = const 1 : i32
                  ret %one
                ^b:
                  %two = const 2 : i32
                  ret %two
                }
                """);
        var first = uk.ac.manchester.tornado.tc.ir.analysis.Analyses.dominators(f);
        assertTrue(first == uk.ac.manchester.tornado.tc.ir.analysis.Analyses.dominators(f));
        // A new instruction is not a CFG edit.
        IrBuilder.insertingBefore(f.blocks().get(1).terminator().orElseThrow()).constInt(Type.I32, 3);
        assertTrue(first == uk.ac.manchester.tornado.tc.ir.analysis.Analyses.dominators(f));
        // Retargeting an edge, a new block and a removed terminator each are.
        var entryBranch = f.entry().terminator().orElseThrow();
        entryBranch.setSuccessorTarget(1, f.blocks().get(1));
        var second = uk.ac.manchester.tornado.tc.ir.analysis.Analyses.dominators(f);
        assertFalse(first == second);
        f.newBlock("extra");
        var third = uk.ac.manchester.tornado.tc.ir.analysis.Analyses.dominators(f);
        assertFalse(second == third);
        var ret = f.blocks().get(2).terminator().orElseThrow();
        ret.dropReferences();
        ret.erase();
        assertFalse(third == uk.ac.manchester.tornado.tc.ir.analysis.Analyses.dominators(f));
    }
}
