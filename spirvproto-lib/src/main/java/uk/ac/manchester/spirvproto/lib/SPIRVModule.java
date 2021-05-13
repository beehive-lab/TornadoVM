package uk.ac.manchester.spirvproto.lib;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import uk.ac.manchester.spirvproto.lib.disassembler.CLIHighlighter;
import uk.ac.manchester.spirvproto.lib.disassembler.SPIRVPrintingOptions;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVAnnotationInst;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVExecutionModeInst;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVNameInst;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpCapability;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpEntryPoint;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInstImport;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtension;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpMemoryModel;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpModuleProcessed;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVSourceInst;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVCapability;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVGlobalInst;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;

public class SPIRVModule implements SPIRVInstScope {
    private final SPIRVHeader header;

    private final List<SPIRVOpCapability> capabilities;
    private final List<SPIRVOpExtension> extensions;
    private final List<SPIRVOpExtInstImport> imports;
    private SPIRVOpMemoryModel memoryModel;
    private final List<SPIRVOpEntryPoint> entryPoints;
    private final List<SPIRVExecutionModeInst> executionModes;
    private final List<SPIRVSourceInst> srcInstructions;
    private final List<SPIRVNameInst> nameInstructions;
    private final List<SPIRVOpModuleProcessed> modules;
    private final List<SPIRVAnnotationInst> annotations;
    private final List<SPIRVGlobalInst> globals;
    private final List<SPIRVFunction> functions;

    private final SPIRVIdGenerator idGen;
    private final Map<SPIRVId, SPIRVInstruction> idToInstMap;

    public SPIRVModule(SPIRVHeader header) {
        this.header = header;
        capabilities = new ArrayList<>();
        extensions = new ArrayList<>();
        imports = new ArrayList<>();
        this.memoryModel = null;
        entryPoints = new ArrayList<>();
        executionModes = new ArrayList<>();
        srcInstructions = new ArrayList<>();
        nameInstructions = new ArrayList<>();
        modules = new ArrayList<>();
        annotations = new ArrayList<>();
        globals = new ArrayList<>();
        functions = new ArrayList<>();

        idGen = new SPIRVIdGenerator();
        idToInstMap = new HashMap<>();
    }

    public SPIRVInstScope add(SPIRVInstruction instruction) {
        ensureCapabilitiesPresent(instruction);

        if (instruction instanceof SPIRVOpCapability)
            capabilities.add((SPIRVOpCapability) instruction);
        else if (instruction instanceof SPIRVOpExtension)
            extensions.add((SPIRVOpExtension) instruction);
        else if (instruction instanceof SPIRVOpExtInstImport)
            imports.add((SPIRVOpExtInstImport) instruction);
        else if (instruction instanceof SPIRVOpEntryPoint)
            entryPoints.add((SPIRVOpEntryPoint) instruction);
        else if (instruction instanceof SPIRVExecutionModeInst)
            executionModes.add((SPIRVExecutionModeInst) instruction);
        else if (instruction instanceof SPIRVSourceInst)
            srcInstructions.add((SPIRVSourceInst) instruction);
        else if (instruction instanceof SPIRVNameInst)
            nameInstructions.add((SPIRVNameInst) instruction);
        else if (instruction instanceof SPIRVOpModuleProcessed)
            modules.add((SPIRVOpModuleProcessed) instruction);
        else if (instruction instanceof SPIRVAnnotationInst)
            annotations.add((SPIRVAnnotationInst) instruction);
        else if (instruction instanceof SPIRVGlobalInst)
            globals.add((SPIRVGlobalInst) instruction);
        else if (instruction instanceof SPIRVOpMemoryModel)
            memoryModel = (SPIRVOpMemoryModel) instruction;
        else if (instruction instanceof SPIRVOpFunction)
            return createFunction((SPIRVOpFunction) instruction);
        else
            throw new IllegalArgumentException("Instruction: " + instruction.getClass().getName() + " is not a valid global instruction");

        SPIRVId resultId = instruction.getResultId();
        if (resultId != null)
            idToInstMap.put(resultId, instruction);

        return this;
    }

    private SPIRVInstScope createFunction(SPIRVOpFunction instruction) {
        SPIRVFunction function = new SPIRVFunction(instruction, this);
        functions.add(function);
        return function;
    }

    /**
     * Validate the module in it's current state and get a reference to the writer
     * 
     * @return The class that can write this module
     * @throws InvalidSPIRVModuleException
     */
    public SPIRVModuleWriter validate() throws InvalidSPIRVModuleException {
        if (capabilities.size() < 1) {
            throw new InvalidSPIRVModuleException("There were no capabilities declared");
        }
        if (memoryModel == null) {
            throw new InvalidSPIRVModuleException("There was no memory model defined");
        }
        if (functions.size() < 1) {
            throw new InvalidSPIRVModuleException("There are no functions declared or defined");
        }
        if (entryPoints.size() < 1) {
            // TODO: Look for Linkage Capability
            throw new InvalidSPIRVModuleException("There were no entry points added");
        }

        header.setBound(idGen.getCurrentBound());
        return new SPIRVModuleWriter();
    }

    public SPIRVModuleWriter close() {
        header.setBound(idGen.getCurrentBound());
        return new SPIRVModuleWriter();
    }

    /**
     * Get the length of this module if written in binary format
     * 
     * @return The length of the binary
     */
    public int getByteCount() {
        final int[] wordCount = { 0 };
        try {
            this.forEachInstruction(i -> wordCount[0] += i.getWordCount());
        } catch (NullPointerException e) {
            wordCount[0] += 2;
        }
        wordCount[0] += 5; // for the header

        return wordCount[0] * 4;
    }

    public SPIRVId getNextId() {
        return idGen.getNextId();
    }

    public SPIRVId getOrCreateId(String name) {
        return idGen.getOrCreateId(name);
    }

    @Override
    public SPIRVIdGenerator getIdGen() {
        return idGen;
    }

    @Override
    public void forEachInstruction(Consumer<SPIRVInstruction> instructionConsumer) {
        capabilities.forEach(instructionConsumer);
        extensions.forEach(instructionConsumer);
        imports.forEach(instructionConsumer);
        instructionConsumer.accept(memoryModel);
        entryPoints.forEach(instructionConsumer);
        executionModes.forEach(instructionConsumer);
        srcInstructions.forEach(instructionConsumer);
        nameInstructions.forEach(instructionConsumer);
        modules.forEach(instructionConsumer);
        annotations.forEach(instructionConsumer);
        globals.forEach(instructionConsumer);

        Map<Boolean, List<SPIRVFunction>> functionGroups = functions.stream().collect(Collectors.partitioningBy(SPIRVFunction::hasBlocks));
        functionGroups.get(false).forEach(f -> f.forEachInstruction(instructionConsumer));
        functionGroups.get(true).forEach(f -> f.forEachInstruction(instructionConsumer));
    }

    /**
     * Print the disassembly of this module.
     * 
     * @param output
     *            The PrintStream to print the output to
     * @param options
     *            PrintingOptions that determine how the output should be formatted
     */
    public void print(PrintStream output, SPIRVPrintingOptions options) {
        this.forEachInstruction(i -> i.print(output, options));
    }

    @Override
    public SPIRVInstruction getInstruction(SPIRVId id) {
        return idToInstMap.get(id);
    }

    @Override
    public SPIRVId getOrAddId(int id) {
        return idGen.getOrAddId(id);
    }

    @Override
    public void ensureCapabilitiesPresent(SPIRVInstruction instruction) {
        for (SPIRVCapability capability : instruction.getAllCapabilities()) {
            SPIRVOpCapability opCapability = new SPIRVOpCapability(capability);
            if (!capabilities.contains(opCapability)) {
                if (this.header.genMagicNumber == SPIRVGeneratorConstants.SPIRVGenMagicNumber) {
                    this.add(opCapability);
                } else {
                    String inst;
                    try {
                        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        final String utf8 = StandardCharsets.UTF_8.name();
                        PrintStream ps = new PrintStream(baos, true, utf8);
                        instruction.print(ps, new SPIRVPrintingOptions(new CLIHighlighter(false), 0, false, true));
                        inst = baos.toString(utf8);
                    } catch (UnsupportedEncodingException e) {
                        inst = instruction.name;
                    }
                    throw new RuntimeException("Instruction: " + inst + "requires capability: " + capability.name);
                }
            }
        }
    }

    public class SPIRVModuleWriter {
        protected SPIRVModuleWriter() {
        }

        /**
         * Write the module in binary format.
         * 
         * @param output
         *            The ByteBuffer where the module should be written
         */
        public void write(ByteBuffer output) {
            header.write(output);
            try {
                forEachInstruction(i -> i.write(output));
            } catch (NullPointerException e) {

            }
        }
    }
}
