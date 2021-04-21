package uk.ac.manchester.spirvproto.lib.disassembler;

import uk.ac.manchester.spirvproto.lib.instructions.*;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;

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
