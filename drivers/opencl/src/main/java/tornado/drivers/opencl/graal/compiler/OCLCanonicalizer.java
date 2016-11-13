package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.spi.SimplifierTool;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.calc.DivNode;
import com.oracle.graal.nodes.calc.MulNode;
import com.oracle.graal.nodes.calc.SubNode;
import com.oracle.graal.nodes.memory.WriteNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import java.util.BitSet;
import java.util.List;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.Vector;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.OCLStamp;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.nodes.vector.*;
import tornado.meta.Meta;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLCanonicalizer extends CustomCanonicalizer {

    protected MetaAccessProvider metaAccess;
    protected ResolvedJavaMethod method;
    protected Meta meta;
    protected Object[] args;

    public void setContext(MetaAccessProvider metaAccess, ResolvedJavaMethod method, Object[] args, Meta meta) {
        this.metaAccess = metaAccess;
        this.method = method;
        this.meta = meta;
        this.args = args;
    }

    @Override
    public Node canonicalize(Node node) {

        if (node instanceof VectorElementOpNode) {
            return canonicalizeVectorElementOp((VectorElementOpNode) node);
        } else if (node instanceof WriteNode) {
//			final WriteNode writeNode = (WriteNode) node;
//			if(writeNode.object() instanceof AtomicAccessNode){
//				final AtomicAccessNode atomic = (AtomicAccessNode) writeNode.object();
//				return new AtomicWriteNode(atomic.value(),writeNode.value(),writeNode.location(),writeNode.getBarrierType());
//			}
        }

        /*
         * else if (node instanceof IsNullNode) { final IsNullNode nullCheck =
         * (IsNullNode) node; if(nullCheck.getValue() instanceof ParameterNode){
         * final ParameterNode param = (ParameterNode) nullCheck.getValue();
         * if(param.graph().method() == method){
         * System.out.printf("canonicalize: isnull=%s,
         * param=%s\n",nullCheck,param); if(args[param.index()] == null)
         * nullCheck.replaceAndDelete(LogicConstantNode.tautology(param.graph()));
         * else
         * nullCheck.replaceAndDelete(LogicConstantNode.contradiction(param.graph()));
         * } } } else if (node instanceof PiNode) { final PiNode pi = (PiNode)
         * node; if (pi.stamp() instanceof ObjectStamp && pi.object().stamp()
         * instanceof ObjectStamp) { pi.replaceAtUsages(pi.object());
         *
         * pi.clearInputs(); pi.graph().removeFloating(pi); } }
         */
        return node;
    }

    private Node canonicalizeVectorElementOp(VectorElementOpNode node) {

//		if(node.needsResolving()){
//			ValueNode origin = node.getOrigin();
//			if(origin instanceof PiNode){
//				origin = ((PiNode)origin).getOriginalNode();
//			}
//
//			if(!(origin instanceof VectorValueNode)){
//			final VectorValueNode vector = node.graph().addOrUnique(new VectorValueNode(node.getVectorKind(),origin));
//			//System.out.printf("canonicalize: node=%s, origin=%s\n",node,node.getOrigin());
//			origin.replaceAtMatchingUsages(vector, n -> !n.equals(vector));
//			//System.out.printf("canonicalize: vector origin=%s\n",vector.getOrigin());
//			node.setVector(vector);
//
//			} else {
//				node.setVector((VectorValueNode) origin);
//				GraphUtil.tryKillUnused(origin);
//			}
//
//
//
//
//		}
        return node;
    }

    @Override
    public void simplify(Node node, SimplifierTool tool) {
//		sSystem.out.printf("simplify: node=%s\n",node);
        if (node instanceof VectorValueNode) {
//			System.out.printf("simplify: node=%s\n",node);
//            simplfyVectorValueNode((VectorValueNode) node, tool);
        } else if (node instanceof ValuePhiNode) {
            final ValuePhiNode phi = (ValuePhiNode) node;
            if (phi.valueAt(0) instanceof VectorValueNode && phi.singleValue().equals(ValuePhiNode.MULTIPLE_VALUES) && phi.usages().count() > 1) {
                //System.out.printf("simplify: phi=%s\n",phi.toString());
                final VectorValueNode firstValue = (VectorValueNode) phi.valueAt(0);
//                unimplemented();
                final VectorValueNode newVector = phi.graph().addOrUnique(new VectorValueNode(firstValue.getOCLKind(), phi));
                phi.replaceAtMatchingUsages(newVector, usage -> !usage.equals(newVector));
                //System.out.printf("simplify: inserted=%s\n",newVector.toString());
            }
        }
    }

    private void simplfyVectorValueNode(VectorValueNode node, SimplifierTool tool) {
        if (node.getElement(0) instanceof VectorValueNode) {
            final VectorValueNode origin = (VectorValueNode) node.getElement(0);

            if (origin.stamp() == node.stamp()) {
                node.replaceAtUsages(origin);
            }

            GraphUtil.tryKillUnused(node);
        }

        final int numInputs = node.inputs().count();
        if (numInputs == node.getOCLKind().getVectorLength()) {
            final List<Node> ops = node.inputs().snapshot();
            final VectorOp op = getVectorOp(node, ops);
//			System.out.printf("vector op: %s\n",op);
            if (op != VectorOp.ILLEGAL) {
                simplifyVectorOp(node, op, ops);
            }
        }

    }

    private void simplifyVectorOp(VectorValueNode node, VectorOp op, List<Node> ops) {
//		System.out.printf("simplifyVectorOp: node=%s, op=%s\n",node,op);
        final List<VectorLoadElementNode> loads = getVector(ops.get(0));
        final ValueNode vectorA = loads.get(0).getVector();
        final ValueNode vectorB = loads.get(1).getVector();

        TornadoInternalError.guarantee(vectorA.stamp() instanceof OCLStamp, "vector stamp is invalid: ", vectorA.stamp().getClass().getName());
        final OCLStamp vectorAStamp = (OCLStamp) vectorA.stamp();
        final OCLKind oclKind = vectorAStamp.getOCLKind();

        switch (op) {
            case ADD:
                final VectorAddNode addNode = node.graph().addOrUnique(new VectorAddNode(oclKind, vectorA, vectorB));
                node.set(addNode);
                for (Node n : ops) {
                    //n.removeUsage(node);
                    GraphUtil.tryKillUnused(n);
                }
                break;
            case DIV:
                final VectorDivNode divNode = node.graph().addOrUnique(new VectorDivNode(oclKind, vectorA, vectorB));
                node.set(divNode);
                for (Node n : ops) {
                    //n.removeUsage(node);
                    GraphUtil.tryKillUnused(n);
                }
                break;
            case MULT:
                final VectorMulNode mulNode = node.graph().addOrUnique(new VectorMulNode(oclKind, vectorA, vectorB));
                node.set(mulNode);
                for (Node n : ops) {
                    //n.removeUsage(node);
                    GraphUtil.tryKillUnused(n);
                }

                break;
            case SUB:

                final VectorSubNode subNode = node.graph().addOrUnique(new VectorSubNode(oclKind, vectorA, vectorB));
                node.set(subNode);
                for (Node n : ops) {
                    //n.removeUsage(node);
                    GraphUtil.tryKillUnused(n);
                }

                break;
        }

    }

    private List<VectorLoadElementNode> getVector(Node op) {
        return op.inputs().filter(VectorLoadElementNode.class).snapshot();
    }

    private VectorOp getVectorOp(VectorValueNode vector, List<Node> ops) {
        unimplemented();
        return null;
//        final VectorOp baseOp = resolveVectorOp(ops.get(0));
////		System.out.printf("baseOp: %s\n",baseOp);
//        if (baseOp == VectorOp.ILLEGAL) {
//            return VectorOp.ILLEGAL;
//        }
//
//        List<VectorLoadElementNode> loads = getVector(ops.get(0));
//
//        final Set<VectorValueNode> vectors = new HashSet<>();
//
//        loads.forEach(load -> vectors.add(load.getVector()));
////		System.out.printf("vectors: size=%d\n",vectors.size());
//        if (vectors.size() != 2) {
//            return VectorOp.ILLEGAL;
//        }
//
//        final VectorValueNode vectorA = loads.get(0).getVector();
//        final VectorValueNode vectorB = loads.get(1).getVector();
//        int loadIdx = checkLoadIndex(loads.get(0), loads.get(1));
//
//        final BitSet lanesA = new BitSet(ops.size());
//        final BitSet lanesB = new BitSet(ops.size());
//
//        boolean conflicts = mapLoadsToLanes(loads, vectorA, lanesA, vectorB, lanesB);
////		System.out.printf("lane conflicts=%s\n",conflicts);
//        if (conflicts || loadIdx == -1 || !lanesA.get(loadIdx) || !lanesB.get(loadIdx)) {
//            return VectorOp.ILLEGAL;
//        }
//
//        for (int i = 1; i < ops.size(); i++) {
//            if (resolveVectorOp(ops.get(i)) != baseOp) {
//                return VectorOp.ILLEGAL;
//            }
//
////				System.out.printf("ops match\n");
//            loads = getVector(ops.get(i));
//
//            if (loads.size() != 2) {
//                return VectorOp.ILLEGAL;
//            }
//
////				System.out.printf("size match\n");
//            if (!vectors.contains(loads.get(0).getVector()) || !vectors.contains(loads.get(1).getVector())) {
//                return VectorOp.ILLEGAL;
//            }
////				System.out.printf("this match\n");
//
//            conflicts = mapLoadsToLanes(loads, vectorA, lanesA, vectorB, lanesB);
////				System.out.printf("lane conflicts=%s\n",conflicts);
//            loadIdx = checkLoadIndex(loads.get(0), loads.get(1));
//            if (conflicts || loadIdx == -1 || !lanesA.get(loadIdx) || !lanesB.get(loadIdx)) {
//                return VectorOp.ILLEGAL;
//            }
//
//        }
//
//        return baseOp;
//    }
//
//    private int checkLoadIndex(VectorLoadElementNode loadA,
//            VectorLoadElementNode loadB) {
//        int indexA = loadA.laneId();
//        int indexB = loadB.laneId();
//
//        return (indexA == indexB) ? indexA : -1;
    }

    private boolean mapLoadsToLanes(List<VectorLoadElementNode> loads, VectorValueNode vectorA,
            BitSet lanesA, VectorValueNode vectorB, BitSet lanesB) {

        for (VectorLoadElementNode load : loads) {
            final BitSet lanes = (load.getVector() == vectorA) ? lanesA : lanesB;
            if (lanes.get(load.laneId())) {
                return true;
            }

            lanes.set(load.laneId());
        }

        return false;
    }

    private void checkVectorStamps(ParameterNode param) {
        if (param.stamp() instanceof ObjectStamp) {
            ObjectStamp objStamp = (ObjectStamp) param.stamp();
            if (objStamp.type().getAnnotation(Vector.class) != null) {
                OCLKind kind = OCLKind.fromResolvedJavaType(objStamp.type());
                param.setStamp(OCLStampFactory.getStampFor(kind));

            }
        }
    }

    public enum VectorOp {
        MULT, ADD, SUB, DIV, ILLEGAL
    };

    private VectorOp resolveVectorOp(Node op) {
        if (op instanceof MulNode) {
            return VectorOp.MULT;
        } else if (op instanceof AddNode) {
            return VectorOp.ADD;
        } else if (op instanceof SubNode) {
            return VectorOp.SUB;
        } else if (op instanceof DivNode) {
            return VectorOp.DIV;
        }

        return VectorOp.ILLEGAL;
    }

}
