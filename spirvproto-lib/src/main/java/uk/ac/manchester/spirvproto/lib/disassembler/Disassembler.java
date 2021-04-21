package uk.ac.manchester.spirvproto.lib.disassembler;

import uk.ac.manchester.spirvproto.lib.SPIRVHeader;
import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.SPIRVModule;
import uk.ac.manchester.spirvproto.lib.SPIRVTool;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInstImport;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.NoSuchElementException;

public class Disassembler implements SPIRVTool {
    private final int littleEndianMagicNumber = 0x07230203;
    private final int bigEndianMagicNumber = 0x03022307;

    private final BinaryWordStream wordStream;
    private final PrintStream output;
    private final SPIRVDisassemblerOptions options;

    private SPIRVHeader header;
    private final SPIRVSyntaxHighlighter highlighter;
    private final SPIRVBuiltInNameMap nameMap;

    public Disassembler(BinaryWordStream wordStream, PrintStream output, SPIRVDisassemblerOptions options) {
        this.wordStream = wordStream;
        this.output = output;
        this.options = options;

        highlighter = new CLIHighlighter(options.shouldHighlight);
        nameMap = new SPIRVBuiltInNameMap();
    }

    /**
     * Disassemble the SPIR-V module given and print the result to the given stream.
     * @throws Exception
     */
    @Override
    public void run() throws Exception {
        int magicNumber = wordStream.getNextWord();
        if (magicNumber == littleEndianMagicNumber) wordStream.setEndianness(ByteOrder.LITTLE_ENDIAN);
        else if (magicNumber == bigEndianMagicNumber) wordStream.setEndianness(ByteOrder.BIG_ENDIAN);
        else throw new InvalidBinarySPIRVInputException(magicNumber);

        header = new SPIRVHeader(
                wordStream.getNextWord(),
                wordStream.getNextWord(),
                wordStream.getNextWord(),
                wordStream.getNextWord()
        );

        SPIRVModule module = new SPIRVModule(header);
        SPIRVInstScope currentScope = module;

        int currentWord;
        while ((currentWord = wordStream.getNextWord()) != -1) {
            currentScope = processLine(currentWord, currentScope);
        }

        print(module);
    }

    /**
     * Read all the words related to this instruction, map the instruction and add it to the current scope
     * @param firstWord The first word of the instruction encoding the operation and the length
     * @param scope The current scope
     * @return The new current scope
     * @throws IOException
     * @throws InvalidSPIRVOpcodeException
     * @throws InvalidSPIRVWordCountException
     */
    private SPIRVInstScope processLine(int firstWord, SPIRVInstScope scope) throws IOException, InvalidSPIRVOpcodeException, InvalidSPIRVWordCountException {
        int opCode = firstWord & 0xFFFF;
        int wordcount = firstWord >> 16;

        int[] line = new int[wordcount];
        line[0] = opCode;

        for (int i = 1; i < wordcount; i++) {
            line[i] = wordStream.getNextWord();
        }

        try {
            SPIRVInstruction instruction = SPIRVInstMapper.createInst(
                    new SPIRVLine(Arrays.stream(line).iterator(), wordStream.getEndianness()), scope);

            nameMap.process(instruction);
            processInstruction(instruction);
            return scope.add(instruction);
        }
        catch (NoSuchElementException e) {
            throw new InvalidSPIRVWordCountException(opCode, wordcount);
        }
    }

    /**
     * Custom extra processing that only happens in the disaasembler
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

    private void print(SPIRVModule module) {
        if (!options.noHeader) output.println(highlighter.highlightComment(header.toString()));

        final int[] indent = {0};
        if (!options.turnOffIndent) module.forEachInstruction((SPIRVInstruction i) -> {
            int assignSize = i.getResultAssigmentSize(options.shouldInlineNames);
            if (assignSize > indent[0]) indent[0] = assignSize;
        });

        SPIRVPrintingOptions printingOptions = new SPIRVPrintingOptions(highlighter, indent[0], options.shouldInlineNames, options.shouldGroup);

        module.print(output, printingOptions);
    }
}
