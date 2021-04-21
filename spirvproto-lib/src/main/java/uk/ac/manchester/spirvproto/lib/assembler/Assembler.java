package uk.ac.manchester.spirvproto.lib.assembler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.ac.manchester.spirvproto.lib.InvalidSPIRVModuleException;
import uk.ac.manchester.spirvproto.lib.SPIRVHeader;
import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.SPIRVModule;
import uk.ac.manchester.spirvproto.lib.SPIRVTool;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInstImport;

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
     * @return An instance of SPIRVModule representing the instructions from the
     *         input.
     */
    public SPIRVModule assemble() {
        SPIRVModule module = new SPIRVModule(new SPIRVHeader(1, 5, 29, 0, 0));

        final SPIRVInstScope[] currentScope = new SPIRVInstScope[] { module };
        reader.lines().forEach(line -> currentScope[0] = processLine(line, currentScope[0]));

        return module;
    }

    /**
     * Tokenize the current line, discard comments, map the instruction and add it
     * to the current scope
     * 
     * @param line
     *            The line to process
     * @param scope
     *            The current scope
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
            } else if (token.isOperand()) {
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
     * 
     * @param instruction
     *            The instruction to process
     */
    private void processInstruction(SPIRVInstruction instruction) {
        if (instruction instanceof SPIRVOpExtInstImport) {
            String name = ((SPIRVOpExtInstImport) instruction)._name.value;
            if (name.equals("OpenCL.std")) {
                SPIRVExtInstMapper.loadOpenCL();
            } else {
                throw new RuntimeException("Unsupported external import: " + name);
            }
        }
    }

    private SPIRVToken[] tokenize(String line) {
        String[] tokens = line.split("\\s+");
        return Arrays.stream(tokens).filter(s -> !s.isEmpty()).map(token -> new SPIRVToken(token, instRecognizer)).toArray(SPIRVToken[]::new);
    }

    private void writeBuffer(ByteBuffer buffer) throws IOException {
        buffer.flip();
        FileChannel channel = new FileOutputStream(out, false).getChannel();
        channel.write(buffer);
        channel.close();
    }

    /**
     * Transform the input SPIRV text form into a SPIR-V binary module and write it
     * out to the given file.
     *
     * @throws {@link
     *             InvalidSPIRVModuleException}
     * @throws {@link
     *             IOException}
     */
    @Override
    public void run() throws InvalidSPIRVModuleException, IOException {
        SPIRVModule module = assemble();
        ByteBuffer buffer = ByteBuffer.allocate(module.getByteCount()).order(ByteOrder.LITTLE_ENDIAN);
        module.validate().write(buffer);

        if (out == null)
            return;

        writeBuffer(buffer);
    }
}