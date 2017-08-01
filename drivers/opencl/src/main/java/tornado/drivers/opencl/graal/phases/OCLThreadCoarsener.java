/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal.phases;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.loop.InductionVariable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.loop.phases.LoopTransformations;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.util.EconomicMap;
import tornado.api.meta.Coarseness;
import tornado.api.meta.TaskMetaData;
import tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;
import tornado.graal.phases.TornadoHighTierContext;
import tornado.meta.domain.DomainTree;
import tornado.meta.domain.IntDomain;

import static tornado.common.Tornado.debug;
import static tornado.common.Tornado.warn;
import static tornado.common.exceptions.TornadoInternalError.*;
import static tornado.common.exceptions.TornadoUnsupportedError.unsupported;
import static tornado.graal.loop.LoopCanonicalizer.canonicalizeLoop;

public class OCLThreadCoarsener extends BasePhase<TornadoHighTierContext> {

    private final CanonicalizerPhase canonicalizer;

    public OCLThreadCoarsener(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    private boolean checkPhis(StructuredGraph graph, LoopEx loop) {
        final LoopBeginNode loopBegin = loop.loopBegin();
        final EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        for (PhiNode phi : loopBegin.phis()) {
            if (!ivs.containsKey(phi)) {
                warn("Unable to parallelize loop because of dependency: %s", GraphUtil.approxSourceLocation(phi));
                return false;
            }
        }
        return true;
    }

    private void replaceParallelNodesSimple(StructuredGraph graph, ParallelRangeNode range) {
        ParallelOffsetNode offset = range.offset();
        ParallelStrideNode stride = range.stride();

        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));
        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));

        // replace useages of parallel placeholders
        offset.replaceAtUsages(threadId);
        stride.safeDelete();
        range.replaceAndDelete(range.value());
    }

    private void coarsen(StructuredGraph graph, TornadoHighTierContext context, ThreadBody body, Coarseness coarseness, DomainTree domain, boolean canAssumeExact) {
        for (int i = 0; i < domain.getDepth(); i++) {
            if (coarseness.getCoarseness(i) > 1) {
                unimplemented();
            } else if (coarseness.getCoarseness(i) == 1) {
                replaceParallelNodesSimple(graph, body.getRange(i));
            } else {
                shouldNotReachHere();
            }
        }
    }

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

    class ThreadBody {

        int dims;
        AbstractBeginNode begin;
        ValueNode[] threadIds;
        ParallelRangeNode[] ranges;
        List<EndNode> ends;

        public ThreadBody() {
            dims = 0;
            threadIds = new ValueNode[3];
            ranges = new ParallelRangeNode[3];
        }

        public void pushDim(ValueNode value, ParallelRangeNode range, AbstractBeginNode begin, List<EndNode> ends) {
            threadIds[dims] = value;
            ranges[dims] = range;
            dims++;
            this.begin = begin;
            this.ends = ends;
        }

        public int getDims() {
            return dims;
        }

        public ValueNode getId(int index) {
            return threadIds[index];
        }

        public ParallelRangeNode getRange(int index) {
            return ranges[index];
        }

    }

    private void simplifyParallelLoop(StructuredGraph graph, LoopEx loop, ParallelRangeNode rangeNode, ThreadBody body) {

        FixedNode entryPoint = loop.entryPoint();
        LoopBeginNode loopBegin = loop.loopBegin();
        LoopExitNode loopExit = loopBegin.loopExits().first();

        guarantee(loopBegin.phis().count() == 1, "malformed parallel loop");
        PhiNode phi = loopBegin.phis().first();
        ValueNode ivNode = phi.firstValue();

        BeginNode newEntry = graph.add(new BeginNode());
        IfNode condition = (IfNode) loopExit.predecessor();
        newEntry.setNext(condition);
        entryPoint.replaceAtPredecessor(newEntry);

        condition.setTrueSuccessorProbability(0.95);

        loopBegin.setNext(null);

        FixedNode mergeTarget = loopExit.next();
        final MergeNode newMergeNode = graph.addOrUnique(new MergeNode());
        newMergeNode.setNext(mergeTarget);

        BeginNode exitBegin = graph.add(new BeginNode());
        EndNode exitReplacement = graph.add(new EndNode());
        exitBegin.setNext(exitReplacement);
        condition.setFalseSuccessor(exitBegin);
        newMergeNode.addForwardEnd(exitReplacement);

        loopExit.setNext(null);

        List<EndNode> threadBodyEnds = new ArrayList<>(loopBegin.loopEnds().count());
        loopBegin.loopEnds().forEach((LoopEndNode end) -> {
            final EndNode newEndNode = graph.add(new EndNode());
            end.replaceAtPredecessor(newEndNode);
            loopBegin.removeEnd(end);
            end.safeDelete();
            newMergeNode.addForwardEnd(newEndNode);
            threadBodyEnds.add(newEndNode);
        });

        phi.replaceAndDelete(ivNode);

        entryPoint.safeDelete();
        loopBegin.setStateAfter(null);
        loopBegin.safeDelete();
        loopExit.safeDelete();

//        GraphUtil.killCFG(entryPoint);
        Debug.dump(Debug.BASIC_LEVEL, graph, "after end swap");
        body.pushDim(ivNode, rangeNode, condition.trueSuccessor(), threadBodyEnds);
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        TaskMetaData meta = context.getMeta();
        if (meta == null || !meta.hasDomain() || !meta.enableThreadCoarsener()) {
            return;
        }

        final DomainTree domain = meta.getDomain();

        if (domain.getDepth() == 0) {
            return;
        }

        Coarseness coarseness = meta.getCoarseness();

        OCLDeviceMapping mapping = (OCLDeviceMapping) context.getDeviceMapping();
        if (meta.shouldCoarsenWithCpuConfig()) {
            int[] config = new int[3];

            if (meta.isCpuConfigDefined()) {
                String configCpu = meta.getCpuConfig();
                int index = 0;
                for (String str : configCpu.split(",")) {
                    config[index] = Integer.parseInt(str);
                    index++;
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
            LoopsData loopsData = new LoopsData(graph);
            loopsData.detectedCountedLoops();

            final ParallelRangeNode[] ranges = new ParallelRangeNode[3];
            Deque<LoopBeginNode> toCanonicalize = new ArrayDeque<>();

            int index = 0;
            for (LoopEx loop : loopsData.outerFirst()) {
                final LoopBeginNode loopBegin = loop.loopBegin();
                ranges[index] = findParallelRange(loopBegin);
                if (ranges[index] != null) {

                    guarantee(checkPhis(graph, loop), "Unable to parallelize loop: %s", GraphUtil.approxSourceLocation(loopBegin));

                    if (loopBegin.loopEnds().count() > 1) {
//                        unimplemented("loop end canonicalization: %s", GraphUtil.approxSourceLocation(loopBegin));
                        toCanonicalize.push(loopBegin);

                    }

                    if (loopBegin.loopExits().count() > 1) {
                        unimplemented("loop exit canonicalization: %s", GraphUtil.approxSourceLocation(loopBegin));
                    }
                    index++;
                } else {
                    break;
                }
            }

            if (!toCanonicalize.isEmpty()) {
                while (!toCanonicalize.isEmpty()) {
                    final LoopBeginNode loopBegin = toCanonicalize.pop();
                    canonicalizeLoop(graph, loopBegin);
                    Debug.dump(Debug.BASIC_LEVEL, graph, "canonicalize loop ends: " + loopBegin);
                }

                loopsData = new LoopsData(graph);
                loopsData.detectedCountedLoops();
            }

            ThreadBody body = new ThreadBody();
            for (LoopEx loop : loopsData.outerFirst()) {
                ParallelRangeNode rangeNode = findParallelRange(loop.loopBegin());
                if (rangeNode != null) {
//                    System.out.printf("**** simplify loop %d\n", rangeNode.index());
                    Mark mark = graph.getMark();
                    simplifyParallelLoop(graph, loop, rangeNode, body);
                    canonicalizer.applyIncremental(graph, context, mark);
                }
            }

            coarsen(graph, context, body, coarseness, domain, meta.canAssumeExact());
            graph.clearLastSchedule();
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

    private IfNode findLastIfNode(FixedNode node) {
        FixedNode current = (FixedNode) node.predecessor();
        while (current != null && !(current instanceof IfNode)) {
            current = (FixedNode) current.predecessor();
        }

        return (current instanceof IfNode) ? (IfNode) current : null;
    }

    private void insertInnerLoop(StructuredGraph graph, LoopBeginNode oldLoopBegin, PhiNode oldIv, ParallelRangeNode range, ValueNode coarseness, ValueNode stride, boolean exact) {

        guarantee(oldLoopBegin.loopExits().count() == 1, "multiple loop exists");
        LoopExitNode oldLoopExit = oldLoopBegin.loopExits().first();

        guarantee(oldLoopBegin.getLoopEndCount() == 1, "multiple loop ends");
        LoopEndNode oldLoopEnd = oldLoopBegin.loopEnds().first();

        IfNode oldLoopCondition = findLastIfNode(oldLoopExit);
        guarantee(oldLoopCondition != null, "could not find old loop condition");

        LoopBeginNode newLoopBegin = graph.addWithoutUnique(new LoopBeginNode());
        LoopExitNode newLoopExit1 = graph.addWithoutUnique(new LoopExitNode(newLoopBegin));
        LoopExitNode newLoopExit2 = graph.addWithoutUnique(new LoopExitNode(newLoopBegin));

        ValuePhiNode newPhi = graph.addOrUnique(new ValuePhiNode(StampFactory.forKind(JavaKind.Int), newLoopBegin));

        oldIv.replaceAtMatchingUsages(newPhi, node -> {
            return node.getId() != range.usages().first().getId() && node.getId() != oldIv.valueAt(1).getId();
        });

        AddNode newStride = graph.addOrUnique(new AddNode(newPhi, stride));
        newPhi.initializeValueAt(0, oldIv);
        newPhi.initializeValueAt(1, newStride);

        ValueNode coarsenessLimit = graph.addOrUnique(new AddNode(oldIv, coarseness));
        LogicNode lessThanCoarsenessLimit = graph.addOrUnique(new IntegerLessThanNode(newPhi, coarsenessLimit));

        LogicNode loopLimit = lessThanCoarsenessLimit;
        if (!exact) {
            /*
             * loop condition to check we are still in range
             */
            loopLimit = graph.addOrUnique(new IntegerLessThanNode(newPhi, range));
        }
        AbstractBeginNode trueSuccessor = oldLoopCondition.trueSuccessor();

        BeginNode innerBegin = graph.addWithoutUnique(new BeginNode());
        IfNode newLoopIf = graph.addWithoutUnique(new IfNode(loopLimit, innerBegin, newLoopExit1, .95));
        newLoopBegin.setNext(newLoopIf);

        /*
         * insert check to ensure we are within coarseness limits
         */
        if (!exact) {
            LoopEndNode newLoopEnd2 = graph.addWithoutUnique(new LoopEndNode(oldLoopBegin));
            newLoopExit2.setNext(newLoopEnd2);
            oldIv.initializeValueAt(2, oldIv.valueAt(1));

            IfNode innerIf = graph.addWithoutUnique(new IfNode(lessThanCoarsenessLimit, trueSuccessor, newLoopExit2, .95));
            innerBegin.setNext(innerIf);
        }

        BeginNode newBegin = graph.addWithoutUnique(new BeginNode());
        EndNode newEnd = graph.addWithoutUnique(new EndNode());
        newBegin.setNext(newEnd);
        newLoopBegin.addForwardEnd(newEnd);

        /*
         * update condition on old loop to point to inner loop
         */
        oldLoopCondition.setTrueSuccessor(newBegin);

        LoopEndNode newLoopEnd1 = graph.addWithoutUnique(new LoopEndNode(newLoopBegin));

        oldLoopEnd.replaceAtPredecessor(newLoopEnd1);
        graph.addAfterFixed(newLoopExit1, oldLoopEnd);
    }

    private void coarsen(StructuredGraph graph, TornadoHighTierContext context, LoopEx loop, ParallelRangeNode range, Coarseness coarseness, DomainTree domain, boolean exact, NodeBitMap killList) {
        ParallelOffsetNode offset = range.offset();
        ParallelStrideNode stride = range.stride();

        guarantee(offset.usages().first() instanceof PhiNode, "unable to find original phi");
        PhiNode originalIV = (PhiNode) offset.usages().first();

        ValueNode opNode = getOp(stride);
        if (!(opNode instanceof AddNode)) {
            unsupported("parallel loop uses unsupported op: %s", opNode);
        }

        final ValueNode desiredCoarseness = ConstantNode.forInt(coarseness.getCoarseness(range.index()), graph);
        final ValueNode updatedStride = graph.addOrUnique(new MulNode(stride.value(), desiredCoarseness));
        final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(offset.index()));
        final GlobalThreadIdNode threadId = graph.addOrUnique(new GlobalThreadIdNode(index));
        final ValueNode x1 = graph.addOrUnique(new MulNode(threadId, updatedStride));
        final AddNode x0 = graph.addOrUnique(new AddNode(x1, offset.value()));
        final GlobalThreadSizeNode threadCount = graph.addOrUnique(new GlobalThreadSizeNode(index));
        final ValueNode updatedStride1 = graph.addOrUnique(new MulNode(threadCount, updatedStride));

        // replace useages of parallel placeholders
        offset.replaceAtUsages(x0);
        stride.replaceAtUsages(updatedStride1);
        Debug.dump(Debug.BASIC_LEVEL, graph, "after re-writing=" + range.index());
        System.out.printf("**** coarseness=%d,exact=%s\n", coarseness.getCoarseness(range.index()), exact);

        if (coarseness.getCoarseness(range.index()) > 1) {
            insertInnerLoop(graph, loop.loopBegin(), originalIV, range, desiredCoarseness, stride.value(), exact);
            /*
             * update domain tree - need to change
             */
            final int cv = coarseness.getCoarseness(range.index());
            final IntDomain cr = (IntDomain) domain.get(range.index());
            final int size = (cr.cardinality() + cv - 1) / cv;
            cr.setOffset(0);
            cr.setLength(size);
            cr.setStep(1);
        } else if (coarseness.getCoarseness(range.index()) == 1 && exact) {
            final LoopBeginNode loopBegin = loop.loopBegin();
            guarantee(loopBegin.loopExits().count() == 1, "malformed loop");

            final LoopExitNode loopExit = loopBegin.loopExits().first();
            final FixedWithNextNode preLoopEntry = (FixedWithNextNode) loop.entryPoint().predecessor();
            Mark mark = graph.getMark();
            LoopTransformations.peel(loop);
            Debug.dump(Debug.BASIC_LEVEL, graph, "after peel=" + range.index());

            FixedNode loopEntry = loop.entryPoint();
            loopEntry.replaceAtPredecessor(loopExit.next());
            loopExit.setNext(null);

            GraphUtil.killCFG(loopEntry);
            Debug.dump(Debug.BASIC_LEVEL, graph, "after kill cfg=" + range.index());
//            canonicalizer.apply(graph, context);
//            canonicalizer.applyIncremental(graph, context, graph.getMark());

            guarantee(preLoopEntry.next() instanceof IfNode, "malformed peel of loop");
            IfNode condition = (IfNode) preLoopEntry.next();

            killList.mark(condition);

//            AbstractBeginNode trueBranch = condition.trueSuccessor();
//            graph.removeSplitPropagate(condition, trueBranch);
//            Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "after kill split=" + range.index());
//            GraphUtil.killCFG(falseBranch);
//            mark = graph.getMark();
//            canonicalizer.applyIncremental(graph, context, graph.getMark());
//            canonicalizer.apply(graph, context);
//
//            Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "after canonical=" + range.index());
        }
        /*
         * delete all parallel placeholders
         */
        if (offset.isAlive()) {
            offset.safeDelete();
        }
        if (stride.isAlive()) {
            stride.safeDelete();
        }
        if (range.isAlive()) {
            range.replaceAndDelete(range.value());
        }

        Debug.dump(Debug.BASIC_LEVEL, graph, "after coarsening index=" + range.index());
    }

}
