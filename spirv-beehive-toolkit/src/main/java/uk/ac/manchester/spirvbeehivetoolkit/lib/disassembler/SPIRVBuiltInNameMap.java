/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package uk.ac.manchester.spirvbeehivetoolkit.lib.disassembler;

import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeBool;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeFloat;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeVector;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeVoid;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.*;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralInteger;

import java.util.ArrayList;

class SPIRVBuiltInNameMap {
    private final ArrayList<KeyPair<SPIRVInstruction, String>> names;

    public SPIRVBuiltInNameMap() {
        names = new ArrayList<>();
        names.add(new KeyPair<>(new SPIRVOpTypeBool(new SPIRVId(-1)), "bool"));
        names.add(new KeyPair<>(new SPIRVOpTypeInt(new SPIRVId(-1), new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)), "uint"));
        names.add(new KeyPair<>(new SPIRVOpTypeInt(new SPIRVId(-1), new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(1)), "int"));
        names.add(new KeyPair<>(new SPIRVOpTypeInt(new SPIRVId(-1), new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)), "ulong"));
        names.add(new KeyPair<>(new SPIRVOpTypeInt(new SPIRVId(-1), new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(1)), "long"));
        names.add(new KeyPair<>(new SPIRVOpTypeFloat(new SPIRVId(-1), new SPIRVLiteralInteger(32)), "float"));
        names.add(new KeyPair<>(new SPIRVOpTypeFloat(new SPIRVId(-1), new SPIRVLiteralInteger(64)), "double"));
        names.add(new KeyPair<>(new SPIRVOpTypeVoid(new SPIRVId(-1)), "void"));
    }

    /**
     * Gives a human readable name to SPIRVIds where that can be deduced from the instruction or debug symbols.
     * @param instruction
     */
    public void process(SPIRVInstruction instruction) {
        int index = names.indexOf(new KeyPair<>(instruction, ""));
        if (index >= 0 && index < names.size()) {
            instruction.getResultId().setName(names.get(index).getValue());
        }
        else {
            if (instruction instanceof SPIRVOpName) {
                ((SPIRVOpName) instruction)._target.setName(((SPIRVOpName) instruction)._name.value);
            }
            else if (instruction instanceof SPIRVOpTypePointer) {
                instruction.getResultId().setName("ptr_" + ((SPIRVOpTypePointer) instruction)._type.getName());
            }
            else if (instruction instanceof SPIRVOpTypeVector) {
                instruction.getResultId().setName("v_" + ((SPIRVOpTypeVector) instruction)._componentType.getName() + "_" + ((SPIRVOpTypeVector) instruction)._componentCount.value);
            }
        }
    }
}
