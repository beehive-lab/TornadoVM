package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.options.OptionValues;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXControlFlow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.graal.TornadoLIRGenerator.trace;

public class PTXCompilationResultBuilder extends CompilationResultBuilder {
    private boolean isKernel;
    private boolean isParallel;
    private Set<ResolvedJavaMethod> nonInlinedMethods;
    private PTXAssembler asm;
    private PTXDeviceContext deviceContext;
    private boolean includePrintf;

    public PTXCompilationResultBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
            OptionValues options, CompilationResult compilationResult) {
        super(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult, Register.None);

        nonInlinedMethods = new HashSet<>();
        this.asm = (PTXAssembler) asm;
    }

    public PTXAssembler getAssembler() {
        return asm;
    }

    public void setKernel(boolean value) {
        isKernel = value;
    }

    public void setParallel(boolean value) {
        isParallel = value;
    }

    public void setIncludePrintf(boolean value) {
        this.includePrintf = value;
    }

    public boolean getIncludePrintf() {
        return includePrintf;
    }

    public boolean getParallel() {
        return isParallel;
    }

    public void addNonInlinedMethod(ResolvedJavaMethod method) {
        nonInlinedMethods.add(method);
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    public boolean isKernel() {
        return isKernel;
    }

    /**
     * Emits code for {@code lir} in its {@linkplain LIR#codeEmittingOrder() code
     * emitting order}.
     */
    @Override
    public void emit(LIR lir) {
        assert this.lir == null;
        assert currentBlockIndex == 0;
        this.lir = lir;
        this.currentBlockIndex = 0;
        frameContext.enter(this);

        final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();
        trace("Traversing CFG: ", cfg.graph.name);
        cfg.computePostdominators();
        traverseControlFlowGraph(cfg, new PTXBlockVisitor(this, asm));

        trace("Finished traversing CFG");
        this.lir = null;
        this.currentBlockIndex = 0;
    }

    @Override
    public void finish() {
        int position = asm.position();
        compilationResult.setTargetCode(asm.close(true), position);
    }

    void emitBlock(Block block) {
        if (block == null) {
            return;
        }

        trace("block: %d", block.getId());

        if (Options.PrintLIRWithAssembly.getValue(getOptions())) {
            blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }

        LIRInstruction breakInst = null;

        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (op != null) {
                if (Options.PrintLIRWithAssembly.getValue(getOptions())) {
                    blockComment(String.format("%d %s", op.id(), op));
                }

                if (op instanceof PTXControlFlow.LoopBreakOp) {
                    breakInst = op;
                    continue;
                }

                try {
                    emitOp(this, op);
                } catch (TornadoInternalError e) {
                    throw e.addContext("lir instruction", block + "@" + op.id() + " " + op + "\n");
                }
            }
        }

        /*
         * Because of the way Graal handles Phi nodes, we generate the break instruction
         * before any phi nodes are updated, therefore we need to ensure that the break
         * is emitted as the end of the block.
         */
        if (breakInst != null) {
            try {
                emitOp(this, breakInst);
            } catch (TornadoInternalError e) {
                throw e.addContext("lir instruction", block + "@" + breakInst.id() + " " + breakInst + "\n");
            }
        }
    }

    private static void emitOp(CompilationResultBuilder crb, LIRInstruction op) {
        try {
            trace("op: " + op);
            op.emitCode(crb);
        } catch (AssertionError | RuntimeException t) {
            throw new TornadoInternalError(t);
        }
    }

    private static void traverseControlFlowGraph(ControlFlowGraph cfg, PTXBlockVisitor visitor) {
        traverseControlFlowGraph(cfg.getStartBlock(), visitor, new HashSet<>());
    }

    private static void traverseControlFlowGraph(Block basicBlock, PTXBlockVisitor visitor, HashSet<Block> visited) {
        visitor.enter(basicBlock);
        visited.add(basicBlock);

        Block firstDominated = basicBlock.getFirstDominated();
        LinkedList<Block> queue = new LinkedList<>();
        queue.add(firstDominated);

        if (basicBlock.isLoopHeader()) {
            Block[] successors = basicBlock.getSuccessors();
            ArrayList<Block> last = new ArrayList<>();
            ArrayList<Block> pending = new ArrayList<>();
            FixedNode endNode = basicBlock.getEndNode();
            IfNode ifNode = null;
            if (endNode instanceof IfNode) {
                ifNode = (IfNode) endNode;
            }
            for (Block block : successors) {
                boolean isInnerLoop = isLoopBlock(block, basicBlock);
                if (!isInnerLoop) {
                    last.add(block);
                    assert ifNode != null;
                    if (ifNode.trueSuccessor() == block.getBeginNode() && block.getBeginNode() instanceof LoopExitNode && block.getEndNode() instanceof EndNode) {
                        pending.add(block);
                    } else {
                        last.add(block);
                    }
                } else {
                    queue.addLast(block);
                }
            }
            for (Block l : last) {
                queue.addLast(l);
            }
            for (Block p : pending) {
                queue.addLast(p);
            }
            queue.removeFirst();
        }

        for (Block block : queue) {
            firstDominated = block;
            while (firstDominated != null) {
                if (!visited.contains(firstDominated)) {
                    traverseControlFlowGraph(firstDominated, visitor, visited);
                }
                firstDominated = firstDominated.getDominatedSibling();
            }
        }
        visitor.exit(basicBlock, null);
    }

    private static boolean isLoopBlock(Block block, Block loopHeader) {

        Set<Block> visited = new HashSet<>();
        Stack<Block> stack = new Stack<>();
        stack.push(block);

        while (!stack.isEmpty()) {

            Block b = stack.pop();
            visited.add(b);

            if (b.getId() < loopHeader.getId()) {
                return false;
            } else if (b == loopHeader) {
                return true;
            } else {
                Block[] successors = b.getSuccessors();
                for (Block bl : successors) {
                    if (!visited.contains(bl)) {
                        stack.push(bl);
                    }
                }
            }
        }
        return false;
    }

    public void setDeviceContext(PTXDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    public PTXDeviceContext getDeviceContext() {
        return this.deviceContext;
    }
}
