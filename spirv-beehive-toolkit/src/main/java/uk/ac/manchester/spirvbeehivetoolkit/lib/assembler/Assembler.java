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

package uk.ac.manchester.spirvbeehivetoolkit.lib.assembler;

import uk.ac.manchester.spirvbeehivetoolkit.lib.InvalidSPIRVModuleException;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVHeader;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVModule;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVTool;
import uk.ac.manchester.spirvbeehivetoolkit.lib.*;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpExtInstImport;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Assembler implements SPIRVTool {
    private final BufferedReader reader;
    private final File out;
    private final SPIRVInstRecognizer instRecognizer;

    public Assembler(Reader reader) {
        this(reader, null);
    }

    public Assembler(Reader reader, File out) {
        this.reader = new BufferedReader(reader);
        this.out = out;
        instRecognizer = new SPIRVInstRecognizer();
    }

    /**
     * Read all lines from the input and construct a SPIRVModule.
     *
     * @return An instance of SPIRVModule representing the instructions from the input.
     */
    public SPIRVModule assemble() {
//        SPIRVModule module = new SPIRVModule(new SPIRVHeader(
//                SPIRVGeneratorConstants.SPIRVMajorVersion,
//                SPIRVGeneratorConstants.SPIRVMinorVersion,
//                SPIRVGeneratorConstants.SPIRVGenMagicNumber,
//                0,
//                0
//        ));
        SPIRVModule module = new SPIRVModule(new SPIRVHeader(
                1,
                5,
                29,
                0,
                0
        ));

        final SPIRVInstScope[] currentScope = new SPIRVInstScope[]{ module };
        reader.lines().forEach(line -> currentScope[0] = processLine(line, currentScope[0]));

        return module;
    }

    /**
     * Tokenize the current line, discard comments, map the instruction and add
     * it to the current scope
     * @param line The line to process
     * @param scope The current scope
     * @return The new current scope resulting from adding a new instruction
     */
    private SPIRVInstScope processLine(String line, SPIRVInstScope scope) {
        SPIRVToken[] tokens = tokenize(line);
        if (tokens.length <= 0) {
            return scope;
        }

        // Discard everything after comment token
        // Get the instruction token
        // Construct the list of operands
        SPIRVToken instruction = null;
        List<SPIRVToken> operands = new ArrayList<>();
        for (SPIRVToken token : tokens) {
            if (token.type == SPIRVTokenType.COMMENT) {
                break;
            } else if (token.type == SPIRVTokenType.INSTRUCTION) {
                instruction = token;
            }
            else if (token.isOperand()) {
                operands.add(token);
            }
        }

        if (instruction == null) {
            return scope;
        }

        SPIRVInstruction instructionNode = SPIRVInstMapper.createInst(instruction, operands.toArray(new SPIRVToken[0]), scope);

        processInstruction(instructionNode);
        return scope.add(instructionNode);
    }

    /**
     * Do custom processing that is only needed in the assembler
     * @param instruction The instruction to process
     */
    private void processInstruction(SPIRVInstruction instruction) {
        if (instruction instanceof SPIRVOpExtInstImport) {
            String name = ((SPIRVOpExtInstImport) instruction)._name.value;
            if (name.equals("OpenCL.std")) {
                SPIRVExtInstMapper.loadOpenCL();
            }
            else {
                throw new RuntimeException("Unsupported external import: " + name);
            }
        }
    }

    private SPIRVToken[] tokenize(String line) {
        String[] tokens = line.split("\\s+");
        return Arrays.stream(tokens)
                .filter(s -> !s.isEmpty())
                .map(token -> new SPIRVToken(token, instRecognizer))
                .toArray(SPIRVToken[]::new);
    }

    private void writeBuffer(ByteBuffer buffer) throws IOException {
        buffer.flip();
        FileChannel channel = new FileOutputStream(out, false).getChannel();
        channel.write(buffer);
        channel.close();
    }

    /**
     * Transform the input SPIRV text form into a SPIR-V binary module and write it out to the given file.
     *
     * @throws {@link InvalidSPIRVModuleException}
     * @throws {@link IOException}
     */
    @Override
    public void run() throws InvalidSPIRVModuleException, IOException {
        SPIRVModule module = assemble();
        ByteBuffer buffer = ByteBuffer.allocate(module.getByteCount()).order(ByteOrder.LITTLE_ENDIAN);
        module.validate().write(buffer);

        if (out == null) return;

        writeBuffer(buffer);
    }
}