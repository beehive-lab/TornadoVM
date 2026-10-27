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
        for (var insn : code.insns()) {
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
