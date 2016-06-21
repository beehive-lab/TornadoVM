package tornado.drivers.opencl.graal.nodes.vector;

import java.util.List;

import tornado.common.Tornado;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLBinaryIntrinsic;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLMemorySpace;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLOp2;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLOp3;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLOp4;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler.OCLOp8;
import tornado.drivers.opencl.graal.compiler.OCLNodeLIRBuilder;
import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLEmitable;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction;
import tornado.drivers.opencl.graal.lir.OCLUnary;
import tornado.drivers.opencl.graal.lir.OCLVectorAssign;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;
import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.AllocatableValue;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PrimitiveConstant;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(nameTemplate = "{p#kind/s}")
public class VectorValueNode extends FloatingNode implements LIRLowerable {
	public static final NodeClass<VectorValueNode>	TYPE	= NodeClass
																	.create(VectorValueNode.class);

	@OptionalInput(InputType.Association)
	private ValueNode								origin;

	@Input
	NodeInputList<ValueNode>						values;

	private final VectorKind						kind;
	private boolean									needsLoad;

	public VectorValueNode(VectorKind kind) {
		super(TYPE, StampFactory.objectNonNull());
		this.kind = kind;
		this.values = new NodeInputList<>(this, kind.getVectorLength());
		needsLoad = false;
	}

	public VectorValueNode(VectorKind kind, ValueNode origin) {
		this(kind);
		this.origin = origin;
	}

	public VectorValueNode(VectorKind kind, ValueNode origin, ValueNode value) {
		this(kind, origin);
		values.add(value);
	}

	public VectorValueNode(VectorKind kind, ValueNode origin, ValueNode[] inputs) {
		this(kind, origin);

		assert (inputs.length == kind.getVectorLength());

		for (int i = 0; i < kind.getVectorLength(); i++)
			values.add(inputs[i]);
	}

	public void initialiseToDefaultValues(StructuredGraph graph) {
		final ConstantNode defaultValue = ConstantNode.defaultForKind(kind.getElementKind(), graph);
		for (int i = 0; i < kind.getVectorLength(); i++) {
			values.add(defaultValue);
		}
	}

	public void setNeedsLoad() {
		needsLoad = true;
	}

	public VectorKind getVectorKind() {
		return kind;
	}

	public ValueNode length() {
		return ConstantNode.forInt(kind.getVectorLength());
	}

	public ValueNode getElement(int index) {
		return values.get(index);
	}

	public void setElement(int index, ValueNode value) {
		if (values.get(index) != null) values.get(index).replaceAtUsages(value);
		else values.set(index, value);
	}

	@Override
	public void generate(NodeLIRBuilderTool gen) {
		final LIRGeneratorTool tool = gen.getLIRGeneratorTool();

		if (origin instanceof VectorReadNode || origin instanceof InvokeNode) {
			gen.setResult(this, gen.operand(origin));
		} else if (origin instanceof ValuePhiNode) {
			
			final ValuePhiNode phi = (ValuePhiNode) origin;
			
			final Value phiOperand = ((OCLNodeLIRBuilder) gen).operandForPhi(phi);
			
			final AllocatableValue result = (gen.hasOperand(this)) ? (Variable) gen.operand(this)
					: tool.newVariable(LIRKind.value(getVectorKind()));
			tool.append(new OCLLIRInstruction.AssignStmt(result, phiOperand));
			gen.setResult(this, result);

		} else if (origin instanceof ParameterNode) {
			if (needsLoad && !gen.hasOperand(this)) {
					final Value addressOfObject = gen.operand(origin);
					final Variable result = tool.newVariable(LIRKind.value(getVectorKind()));

					final OCLUnary.MemoryAccess memoryAccess = new OCLUnary.MemoryAccess(
							OCLMemorySpace.GLOBAL, addressOfObject);
					memoryAccess.setKind(getVectorKind().getElementKind());

					final OCLBinaryIntrinsic intrinsic = VectorUtil
							.resolveLoadIntrinsic(getVectorKind());
					final AssignStmt stmt = new AssignStmt(result, new OCLBinary.Intrinsic(
							intrinsic, result.getLIRKind(), PrimitiveConstant.INT_0, memoryAccess));

					tool.append(stmt);
					Tornado.trace("emitVectorLoad: %s = %s(%d, %s)", result, intrinsic.toString(),
							0, memoryAccess);
					gen.setResult(this, result);
			} else if (!needsLoad) {
				gen.setResult(this, gen.operand(origin));
			}
		} else if (origin == null) {
			final AllocatableValue result = (gen.hasOperand(this)) ? (Variable) gen.operand(this)
					: tool.newVariable(LIRKind.value(getVectorKind()));
			
			/*
			 * two cases:
			 * 1. when this vector state has elements assigned individually
			 * 2. when this vector is assigned by a vector operation
			 */
			final int numValues = values.nonNull().count();
			final ValueNode firstValue = values.nonNull().first();

			if (firstValue instanceof VectorValueNode || firstValue instanceof VectorOp) {
				tool.append(new OCLLIRInstruction.AssignStmt(result, gen.operand(values.first())));
				gen.setResult(this, result);
			} else {
				if (numValues > 0 && gen.hasOperand(firstValue)) {
					generateVectorAssign(gen, tool, result);
				} else {
					gen.setResult(this, result);
				}

			}
		}
	}

	private Value getParam(NodeLIRBuilderTool gen, LIRGeneratorTool tool, int index) {
		final ValueNode valueNode = values.get(index);

		return (valueNode == null) ? kind.getDefaultValue() : tool.loadNonConst(gen
				.operand(valueNode));
	}

	private void generateVectorAssign(NodeLIRBuilderTool gen, LIRGeneratorTool tool,
			AllocatableValue result) {

		OCLEmitable assignExpr = null;

		Value s0, s1, s2, s3, s4, s5, s6, s7;
		switch (kind.getVectorLength()) {
			case 2:
				final OCLOp2 op2 = VectorUtil.resolveAssignOp2(getVectorKind());
				s0 = getParam(gen, tool, 0);
				s1 = getParam(gen, tool, 1);
				assignExpr = new OCLVectorAssign.Assign2Expr(op2, getVectorKind(), s0, s1);
				break;
			case 3:
				final OCLOp3 op3 = VectorUtil.resolveAssignOp3(getVectorKind());
				s0 = getParam(gen, tool, 0);
				s1 = getParam(gen, tool, 1);
				s2 = getParam(gen, tool, 2);
				assignExpr = new OCLVectorAssign.Assign3Expr(op3, getVectorKind(), s0, s1, s2);
				break;
			case 4:
				final OCLOp4 op4 = VectorUtil.resolveAssignOp4(getVectorKind());
				s0 = getParam(gen, tool, 0);
				s1 = getParam(gen, tool, 1);
				s2 = getParam(gen, tool, 2);
				s3 = getParam(gen, tool, 3);
				assignExpr = new OCLVectorAssign.Assign4Expr(op4, getVectorKind(), s0, s1, s2, s3);
				break;
			case 8:
				final OCLOp8 op8 = VectorUtil.resolveAssignOp8(getVectorKind());
				s0 = getParam(gen, tool, 0);
				s1 = getParam(gen, tool, 1);
				s2 = getParam(gen, tool, 2);
				s3 = getParam(gen, tool, 3);
				s4 = getParam(gen, tool, 4);
				s5 = getParam(gen, tool, 5);
				s6 = getParam(gen, tool, 6);
				s7 = getParam(gen, tool, 7);
				assignExpr = new OCLVectorAssign.Assign8Expr(op8, getVectorKind(), s0, s1, s2, s3,
						s4, s5, s6, s7);
				break;

			default:
				TornadoInternalError.unimplemented("new vector length = " + kind.getVectorLength());
		}

		tool.append(new OCLLIRInstruction.AssignStmt(result, assignExpr));

		gen.setResult(this, result);
	}

	@Deprecated
	public List<VectorLoadElementNode> getLaneUses() {
		return usages().filter(VectorLoadElementNode.class).snapshot();
	}

	public boolean allLanesUsed() {
		return (usages().filter(VectorLoadElementNode.class).distinct().count() == kind
				.getVectorLength());
	}

	public List<VectorStoreElementProxyNode> getLanesInputs() {
		return usages().filter(VectorStoreElementProxyNode.class).snapshot();
	}

	public boolean allLanesSet() {
		return (values.count() == 1 && values.first() instanceof VectorValueNode)
				|| (values.filter(VectorLoadElementNode.class).distinct().count() == kind
						.getVectorLength());
	}

	public void set(ValueNode value) {
		values.clear();
		values.add(value);

	}

	public void deleteUnusedLoads() {
		usages().filter(VectorLoadElementNode.class).forEach(node -> {
			if (node.hasNoUsages()) {
				values.remove(node);
				node.safeDelete();
			}
		});

	}

	public boolean isLaneSet(int index) {
		if (values.count() < index) return false;

		return false;
	}

	public void setOrigin(ValueNode value) {
		this.updateUsages(origin, value);
		origin = value;
	}

	public void clearOrigin() {
		this.replaceFirstInput(origin, null);
	}

	public ValueNode getOrigin() {
		return origin;
	}

	public VectorValueNode duplicate() {
		return graph().addWithoutUnique(new VectorValueNode(kind));
	}

}
