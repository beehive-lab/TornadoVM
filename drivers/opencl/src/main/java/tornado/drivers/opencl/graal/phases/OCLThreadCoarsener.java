package tornado.drivers.opencl.graal.phases;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeBitMap;
import com.oracle.graal.loop.LoopEx;
import com.oracle.graal.loop.LoopFragmentInside;
import com.oracle.graal.loop.LoopsData;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.phases.BasePhase;
import java.util.List;
import jdk.vm.ci.meta.JavaKind;
import tornado.common.Tornado;
import tornado.drivers.opencl.enums.OCLDeviceType;
import tornado.drivers.opencl.graal.meta.Coarseness;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;
import tornado.graal.phases.TornadoHighTierContext;
import tornado.meta.Meta;
import tornado.meta.domain.DomainTree;
import tornado.meta.domain.IntDomain;

import static tornado.common.Tornado.debug;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoUnsupportedError.unsupported;

public class OCLThreadCoarsener extends BasePhase<TornadoHighTierContext> {

    private ParallelRangeNode findParallelRange(LoopBeginNode loopBegin) {

        IfNode ifNode = null;
        FixedWithNextNode current = loopBegin;
        while (current != null) {

            FixedNode next = current.next();
            if (next instanceof IfNode) {
                ifNode = (IfNode) next;
                break;
            } else if (next instanceof FixedWithNextNode) {
                current = (FixedWithNextNode) next;
            } else {
                shouldNotReachHere();
            }
        }

        guarantee(ifNode != null, "invalid loop header");
        return ifNode.condition().inputs().filter(ParallelRangeNode.class).first();
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (!context.hasDeviceMapping()) {
            return;
        }

        Meta meta = context.getMeta();
        DomainTree domain = meta.getDomain();
        Coarseness coarseness;
        if (meta.hasProvider(Coarseness.class)) {
            coarseness = meta.getProvider(Coarseness.class);
        } else {
            coarseness = new Coarseness(domain.getDepth());
        }

        OCLDeviceMapping mapping = (OCLDeviceMapping) context.getDeviceMapping();
        if (mapping.getDevice().getDeviceType() == OCLDeviceType.CL_DEVICE_TYPE_CPU) {
            int[] config = new int[3];
            String configCpu = Tornado.getProperty("tornado.opencl.cpu.config");
            if (configCpu != null) {
                int index = 0;
                for (String str : configCpu.split(",")) {
                    config[index] = Integer.parseInt(str);
                }
            } else {
                config[0] = mapping.getDevice().getMaxComputeUnits();
                config[1] = 1;
                config[2] = 1;
            }

            long[] workItems = mapping.getDevice().getMaxWorkItemSizes();
            for (int i = 0; i < domain.getDepth(); i++) {
                if (workItems[i] > 1) {
                    coarseness.setCoarseness(i, domain.get(i).cardinality() / config[i]);
                } else {
                    coarseness.setCoarseness(i, 1);
                }
            }
        }

        debug("coarseness: %s", coarseness);

        if (graph.hasLoops()) {
            final LoopsData data = new LoopsData(graph);
            data.detectedCountedLoops();

            List<LoopEx> loops = data.outerFirst();

            ParallelRangeNode[] parallelRanges = new ParallelRangeNode[domain.getDepth()];
            LoopEx[] parallelLoops = new LoopEx[domain.getDepth()];
            Translation[] translations = new Translation[domain.getDepth()];

            for (LoopEx l : loops) {
                ParallelRangeNode rangeNode = findParallelRange(l.loopBegin());
//                System.out.printf("loop: %s -> range %s\n", l.oldLoopBodyBegin(), rangeNode);
                if (rangeNode != null) {
                    int index = l.loop().getDepth() - 1;
                    parallelLoops[index] = l;
                    parallelRanges[index] = rangeNode;
                    translations[index] = normalise(graph, rangeNode);
                }
            }

            LoopEx insertionLoop = parallelLoops[parallelLoops.length - 1];
//            System.out.println("parallel loops:");
//            System.out.println(Arrays.toString(parallelLoops));
//
//            System.out.printf("insertion loop: %s\n", insertionLoop.oldLoopBodyBegin());

            LoopFragmentInside inside = insertionLoop.inside();
            NodeBitMap nodes = inside.nodes().copy();

            LoopBeginNode oldLoopBegin = insertionLoop.loopBegin();
            AbstractBeginNode oldLoopBodyBegin = pruneNodes(nodes, oldLoopBegin);
            final IfNode oldIf = (IfNode) oldLoopBodyBegin.predecessor();

            ConstantNode zero = graph.addOrUnique(ConstantNode.forInt(0));

            for (int loopIndex = parallelLoops.length - 1; loopIndex >= 0; loopIndex--) {

                ParallelRangeNode range = parallelRanges[loopIndex];
                int domainIndex = range.index();
                ValueNode originalPhi = (ValueNode) range.offset().usages().first();
                ParallelStrideNode stride = range.stride();
                ValueNode opNode = getOp(stride);

                final LoopEndNode[] oldLoopEnds = oldLoopBegin.orderedLoopEnds();
                final List<LoopExitNode> oldLoopExits = oldLoopBegin.loopExits().snapshot();

                // skip coarsness of 1
                if (coarseness.getCoarseness(domainIndex) > 1) {

                    ConstantNode cv = graph.addOrUnique(ConstantNode.forInt(coarseness.getCoarseness(domainIndex)));

                    LoopBeginNode newLoopBegin = graph.addWithoutUnique(new LoopBeginNode());
                    IfNode newIf = createLoop(graph, newLoopBegin, cv);
                    Translation newLoopTranslation = new Translation(zero, cv);
                    ValuePhiNode newPhi = (ValuePhiNode) newLoopBegin.phis().first();

                    /*
                     * use cfg graph to connect the inner loop into the newly
                     * created loop.
                     */
                    BeginNode newBegin = graph.addOrUnique(new BeginNode());
                    EndNode newEnd = graph.addOrUnique(new EndNode());
                    newBegin.setNext(newEnd);

                    oldIf.setTrueSuccessor(newBegin);
                    newLoopBegin.addForwardEnd(newEnd);

                    // oldLoopBodyBegin
                    newIf.setTrueSuccessor(oldLoopBodyBegin);
                    oldLoopBodyBegin = newBegin;

                    for (LoopEndNode loopEnd : oldLoopEnds) {
                        oldLoopBegin.removeUsage(loopEnd);
                        loopEnd.clearInputs();
                        LoopEndNode newLoopEnd = graph.addOrUnique(new LoopEndNode(newLoopBegin));
                        loopEnd.replaceAtPredecessor(newLoopEnd);
                        loopEnd.safeDelete();
                    }

                    LoopEndNode oldLoopEnd = graph.addWithoutUnique(new LoopEndNode(oldLoopBegin));

                    guarantee(oldLoopExits.size() == 1, "unsupported parallel loop: %s", oldLoopBegin);
                    LoopExitNode targetExit = oldLoopExits.get(0);
                    LoopExitNode newExit = graph.addWithoutUnique(new LoopExitNode(newLoopBegin));
                    newExit.setNext(oldLoopEnd);

                    targetExit.replaceAtPredecessor(newExit);
                    oldIf.setFalseSuccessor(targetExit);
                    newIf.setFalseSuccessor(newExit);


                    /*
                     * update the iv of the inner (newest) loop
                     */
                    ValueNode newInnerIV = applyTranslation(graph, translations[loopIndex], newPhi);

                    Translation mergedTranslation = mergeTranslations(graph, newLoopTranslation, translations[loopIndex]);

                    ValueNode newOuterIV = applyTranslation(graph, mergedTranslation, originalPhi);

                    ValueNode newIv = graph.addOrUnique(new AddNode(newInnerIV, newOuterIV));

                    nodes.filter((Node n) -> !n.equals(opNode)).forEach((Node n) -> n.replaceFirstInput(originalPhi, newIv));

                    // reduce the range of the outer loop by coarseness
                    ValueNode rangeValue = graph.addOrUnique(new DivNode(range.value(), cv));
                    range.replaceFirstInput(range.value(), rangeValue);

                    /*
                     * update domain tree - need to change
                     */
                    IntDomain cr = (IntDomain) domain.get(domainIndex);
                    int size = (cr.getLength() - cr.getOffset()) / cr.getStep();
                    size /= coarseness.getCoarseness(domainIndex);
                    cr.setOffset(0);
                    cr.setLength(size);
                    cr.setStep(1);

                    Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "after coarsening index=" + domainIndex);
                } else {
                    ValueNode newIv = applyTranslation(graph, translations[loopIndex], originalPhi);
                    nodes.filter((Node n) -> !n.equals(opNode)).forEach((Node n) -> n.replaceFirstInput(originalPhi, newIv));
                }
            }
        }
    }

    private IfNode createLoop(StructuredGraph graph, LoopBeginNode newLoopBegin, ConstantNode cv) {

        ConstantNode zero = graph.addOrUnique(ConstantNode.forInt(0));
        ConstantNode one = graph.addOrUnique(ConstantNode.forInt(1));

        ValuePhiNode newPhi = graph.addOrUnique(new ValuePhiNode(StampFactory.forKind(JavaKind.Int), newLoopBegin));
        AddNode newStride = graph.addOrUnique(new AddNode(newPhi, one));
        newPhi.initializeValueAt(0, zero);
        newPhi.initializeValueAt(1, newStride);

        LogicNode newLoopCondition = graph.addOrUnique(new IntegerLessThanNode(newPhi, cv));

        IfNode newLoopIf = graph.addWithoutUnique(new IfNode(newLoopCondition, null, null, .5));
        newLoopBegin.setNext(newLoopIf);

        return newLoopIf;
    }

    private static class Translation {

        ValueNode offset;
        ValueNode scale;

        public Translation(ValueNode offset, ValueNode scale) {
            this.offset = offset;
            this.scale = scale;
        }
    }

    private ValueNode getOp(ParallelStrideNode stride) {
        for (Node use : stride.usages()) {
            if (use instanceof ParallelRangeNode) {
                continue;
            } else {
                return (ValueNode) use;
            }
        }
        return null;
    }

    private Translation normalise(StructuredGraph graph, ParallelRangeNode range) {
        ParallelOffsetNode offset = range.offset();
        ParallelStrideNode stride = range.stride();

        ValueNode opNode = getOp(stride);
        if (!(opNode instanceof AddNode)) {
            unsupported("parallel loop uses unsupoorted op: %s", opNode);
        }

        /*
         * normalise loop nest: {offset, op, stride, range} -> {0, op, 1,
         * norm(range)} and translation {offset, scale} where norm(range) ->
         * (range - offset) stride
         */
        ConstantNode zero = graph.addOrUnique(ConstantNode.forInt(0));
        ConstantNode one = graph.addOrUnique(ConstantNode.forInt(1));

        ValueNode offsetValue = offset.value();
        ValueNode strideValue = stride.value();
        ValueNode adjustedRange = graph.addOrUnique(new SubNode(range.value(), offsetValue));
        ValueNode normalisedRange = graph.addOrUnique(new DivNode(adjustedRange, strideValue));

        offset.replaceFirstInput(offsetValue, zero);
        stride.replaceFirstInput(strideValue, one);
        range.replaceFirstInput(range.value(), normalisedRange);

        return new Translation(offsetValue, strideValue);

    }

    private ValueNode applyTranslation(StructuredGraph graph, Translation translation, ValueNode inductionVariable) {

        ValueNode scaledValue = graph.addOrUnique(new MulNode(inductionVariable, translation.scale));
        ValueNode translatedValue = graph.addOrUnique(new AddNode(scaledValue, translation.offset));

        return translatedValue;
    }

    private Translation mergeTranslations(StructuredGraph graph, Translation t0, Translation t1) {

        ValueNode offset = t0.offset;
        ValueNode scale = graph.addOrUnique(new MulNode(t0.scale, t1.scale));

        return new Translation(offset, scale);

    }

    private static AbstractBeginNode pruneNodes(NodeBitMap nodes, LoopBeginNode beginNode) {

        nodes.clear(beginNode);

        IfNode condition = null;
        FixedWithNextNode current = beginNode;
        while (current != null) {
            nodes.clear(current);

            Node next = current.next();
            if (next instanceof FixedWithNextNode) {
                current = (FixedWithNextNode) next;
            } else if (next instanceof IfNode) {
                condition = (IfNode) next;
                current = null;
            }
        }

        nodes.clear(condition);
        nodes.clear(condition.falseSuccessor());
        nodes.clear(condition.condition());

        for (Node n : nodes.snapshot()) {
            if (n instanceof FrameState) {
                nodes.clear(n);
            }
        }

        return condition.trueSuccessor();
    }

}
