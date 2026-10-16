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
package uk.ac.manchester.tornado.tc.frontend;

import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;

/**
 * A readable listing of a method's bytecode (bci, mnemonic, operands, resolved members, source line),
 * the first stage shown in TC dumps and reports.
 */
public final class Disassembler {

    private Disassembler() {
    }

    public static String print(ClassFile classFile, ClassFile.Method method) {
        var code = method.code().orElseThrow();
        var sb = new StringBuilder();
        sb.append("// ").append(classFile.name().replace('/', '.')).append('.').append(method.name()).append(method.descriptor()).append('\n');
        sb.append("// max_stack=").append(code.maxStack()).append(" max_locals=").append(code.maxLocals()).append('\n');
        int lastLine = -2;
        for (var insn : Bytecode.decode(code.bytes())) {
            int line = code.lineAt(insn.bci());
            if (line != lastLine && line >= 0) {
                sb.append("  // line ").append(line).append('\n');
                lastLine = line;
            }
            sb.append(String.format("  %4d: %-16s", insn.bci(), insn.mnemonic())).append(operands(classFile, insn)).append('\n');
        }
        return sb.toString();
    }

    private static String operands(ClassFile classFile, Bytecode.Insn insn) {
        int op = insn.opcode();
        var pool = classFile.constants();
        if (op >= Bytecode.GETSTATIC && op <= Bytecode.INVOKEINTERFACE) {
            var ref = pool.member(insn.index());
            return ref.owner().substring(ref.owner().lastIndexOf('/') + 1) + "." + ref.name() + " " + ref.descriptor();
        }
        if (op == Bytecode.LDC || op == Bytecode.LDC_W || op == Bytecode.LDC2_W) {
            return String.valueOf(pool.loadable(insn.index()));
        }
        if (insn.targets().length > 0) {
            var sb = new StringBuilder();
            for (int i = 0; i < insn.targets().length; i++) {
                if (i < insn.keys().length) {
                    sb.append(insn.keys()[i]).append(": ");
                } else if (insn.keys().length > 0) {
                    sb.append("default: ");
                }
                sb.append(insn.targets()[i]).append(i + 1 < insn.targets().length ? ", " : "");
            }
            return sb.toString();
        }
        if (op == Bytecode.IINC) {
            return insn.index() + " " + insn.value();
        }
        if ((op >= Bytecode.ILOAD && op <= Bytecode.ALOAD) || (op >= Bytecode.ISTORE && op <= Bytecode.ASTORE) || op == Bytecode.BIPUSH || op == Bytecode.SIPUSH) {
            return String.valueOf(insn.index());
        }
        if (op == Bytecode.NEWARRAY) {
            return "type " + insn.value();
        }
        return "";
    }
}
