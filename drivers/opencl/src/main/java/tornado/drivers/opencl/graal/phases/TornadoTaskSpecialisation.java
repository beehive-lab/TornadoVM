package tornado.drivers.opencl.graal.phases;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.graal.phases.TornadoHighTierContext;
import tornado.graal.phases.TornadoLoopUnroller;
import tornado.graal.phases.TornadoValueTypeReplacement;
import tornado.runtime.ObjectReference;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaField;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.GuardingPiNode;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.java.ArrayLengthNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;

public class TornadoTaskSpecialisation extends BasePhase<TornadoHighTierContext> {

	public static final int						MAX_ITERATIONS	= 10;

	private final CanonicalizerPhase			canonicalizer;
	private final TornadoValueTypeReplacement	valueTypeReplacement;
	private final DeadCodeEliminationPhase		deadCodeElimination;
	private final TornadoLoopUnroller			loopUnroller;

	public TornadoTaskSpecialisation(CanonicalizerPhase canonicalizer) {
		this.canonicalizer = canonicalizer;
		this.valueTypeReplacement = new TornadoValueTypeReplacement();
		this.deadCodeElimination = new DeadCodeEliminationPhase();
		this.loopUnroller = new TornadoLoopUnroller(canonicalizer);

	}

	private Field lookupField(Class<?> type, String field) {
//		Tornado.debug("lookup field: class=%s, field=%s", type.toString(), field);
		Field f = null;
		try {
			f = type.getDeclaredField(field);
			if (!f.isAccessible()) f.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException e) {
			if (type.getSuperclass() != null) f = lookupField(type.getSuperclass(), field);
			else e.printStackTrace();
		}
		return f;
	}

	@FunctionalInterface
	private interface FunctionThatThrows<T, R> {
		R apply(T t) throws IllegalArgumentException, IllegalAccessException;
	}

	private <T> T lookup(Object object, FunctionThatThrows<Object, T> function)
			throws IllegalArgumentException, IllegalAccessException {
		return function.apply(object);
	}

	private Object lookupRefField(StructuredGraph graph, Node node, Object obj, String field) {
		final Class<?> type = obj.getClass();
		final Field f = lookupField(type, field);
		Object result = null;
		try {
			result = f.get(obj);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

	private ConstantNode lookupPrimField(StructuredGraph graph, Node node, Object obj,
			String field, Kind kind) {
		final Class<?> type = obj.getClass();
		final Field f = lookupField(type, field);
		ConstantNode constant = null;
		try {
			switch (kind) {
				case Boolean:
					constant = ConstantNode.forBoolean(lookup(obj, f::getBoolean));
					break;
				case Byte:
					constant = ConstantNode.forByte(lookup(obj, f::getByte), graph);
					break;
				case Char:
					constant = ConstantNode.forChar(lookup(obj, f::getChar), graph);
					break;
				case Double:
					constant = ConstantNode.forDouble(lookup(obj, f::getDouble));
					break;
				case Float:
					constant = ConstantNode.forFloat(lookup(obj, f::getFloat));
					break;
				case Int:
					constant = ConstantNode.forInt(lookup(obj, f::getInt));
					break;
				case Long:
					constant = ConstantNode.forLong(lookup(obj, f::getLong));
					break;
				case Short:
					constant = ConstantNode.forShort(lookup(obj, f::getShort), graph);
					break;
				case Object:
					/*
					 * propagate all constants from connected final fields...cool!
					 */
					if (Modifier.isFinal(f.getModifiers())) {
						final Object value = lookup(obj, f::get);
						node.usages().filter(LoadFieldNode.class)
								.forEach(load -> evaluate(graph, load, value));
						node.usages().filter(ArrayLengthNode.class)
								.forEach(arrayLength -> evaluate(graph, arrayLength, value));
					}
					break;
				case Illegal:
				case Void:
				default:
					break;
			}

		} catch (IllegalArgumentException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return constant;
	}

	private void evaluate(final StructuredGraph graph, final Node node, final Object param) {

		final Object value = (param instanceof ObjectReference) ? ((ObjectReference<?,?>) param)
				.get() : param;
		//Tornado.debug("evaluate: node=%s, object=%s", node, value);

		if (node instanceof ArrayLengthNode) {
			ArrayLengthNode arrayLength = (ArrayLengthNode) node;
			int length = Array.getLength(value);
			final ConstantNode constant = ConstantNode.forInt(length);
			node.replaceAtUsages(graph.addOrUnique(constant));
			arrayLength.clearInputs();
			GraphUtil.removeFixedWithUnusedInputs(arrayLength);
		} else if (node instanceof LoadFieldNode) {
			final LoadFieldNode loadField = (LoadFieldNode) node;
			final ResolvedJavaField field = loadField.field();
//			Tornado.debug("load field: name=%s, type=%s, declaring class=%s", field.getName(),
//					field.getType().toJavaName(), field.getDeclaringClass().getName());
			if (field.getType().getKind().isPrimitive()) {
				ConstantNode constant = lookupPrimField(graph, node, value, field.getName(),
						field.getKind());
				constant = graph.addOrUnique(constant);
//				Tornado.debug("Replaced %s with %s", node, constant);
				node.replaceAtUsages(constant);
				loadField.clearInputs();
				graph.removeFixed(loadField);
//				Tornado.debug("removed %s", loadField);
			} else if (field.isFinal()) {
//				Tornado.debug("propagating final fields...");
				Object object = lookupRefField(graph, node, value, field.getName());
				node.usages().forEach(n -> evaluate(graph, n, object));
			}
		} else if (node instanceof IsNullNode) {
			final IsNullNode isNullNode = (IsNullNode) node;
			final boolean isNull = (param == null);
			if (isNull) isNullNode.replaceAtUsages(LogicConstantNode.tautology(graph));
			else isNullNode.replaceAtUsages(LogicConstantNode.contradiction(graph));
			graph.removeFloating(isNullNode);
		}
	}

	private ConstantNode createConstantFromObject(Object obj) {
		ConstantNode result = null;
		if (obj instanceof Float) {
			result = ConstantNode.forFloat((float) obj);
		} else if (obj instanceof Integer) {
			result = ConstantNode.forInt((int) obj);
		}

		return result;
	}

	private void propagateParameters(StructuredGraph graph, ParameterNode parameterNode,
			Object[] args) {
		if (args[parameterNode.index()] != null
				&& RuntimeUtilities.isBoxedPrimitiveClass(args[parameterNode.index()].getClass())) {
			ConstantNode constant = createConstantFromObject(args[parameterNode.index()]);
			graph.addWithoutUnique(constant);
			parameterNode.replaceAtUsages(constant);
		} else {
			parameterNode.usages().snapshot()
					.forEach(n -> evaluate(graph, n, args[parameterNode.index()]));
		}
	}

	@Override
	protected void run(StructuredGraph graph, TornadoHighTierContext context) {

		int iterations = 0;

		int lastNodeCount = graph.getNodeCount();
		boolean hasWork = true;
		while (hasWork) {
			final Mark mark = graph.getMark();

			if (context.hasArgs()) {
				for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
					propagateParameters(graph, param, context.getArgs());
				}
				Debug.dump(graph, "After Phase Propagate Parameters");
			} else {
				for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
					assumeNonNull(graph, param);
				}
				Debug.dump(graph, "After Phase assume non null Parameters");
			}

			canonicalizer.apply(graph, context);

			graph.getNewNodes(mark)
					.filter(PiNode.class)
					.forEach(
							pi -> {
								if (pi.stamp() instanceof ObjectStamp
										&& pi.object().stamp() instanceof ObjectStamp) {
									pi.replaceAtUsages(pi.object());

									pi.clearInputs();
									graph.removeFloating(pi);
								}
							});

			Debug.dump(graph, "After Phase Pi Node Removal");

			loopUnroller.execute(graph, context);

			valueTypeReplacement.execute(graph, context);

			canonicalizer.apply(graph, context);

			deadCodeElimination.run(graph);

			Debug.dump(graph, "After TaskSpecialisation iteration=" + iterations);

			boolean hasGuardingPiNodes = graph.getNodes().filter(GuardingPiNode.class).isNotEmpty();

			hasWork = (lastNodeCount != graph.getNodeCount()
					|| graph.getNewNodes(mark).isNotEmpty() || hasGuardingPiNodes)
					&& (iterations < MAX_ITERATIONS);
			lastNodeCount = graph.getNodeCount();
			iterations++;
		}

		if (iterations == MAX_ITERATIONS) {
			Tornado.warn("TaskSpecialisation unable to complete after %d iterations", iterations);
		}

		Tornado.debug("TaskSpecialisation ran %d iterations", iterations);

		Tornado.debug("valid graph? %s", graph.verify());
	}

	private void assumeNonNull(StructuredGraph graph, ParameterNode param) {
		if(param.getKind().isObject() && param.usages().filter(IsNullNode.class).count() > 0){
			final IsNullNode isNullNode = (IsNullNode) param.usages().filter(IsNullNode.class).first();
			for(final GuardingPiNode guardingPiNode : isNullNode.usages().filter(GuardingPiNode.class).distinct()){
				guardingPiNode.replaceAtUsages(param);
			}
			
			
			
		}
		
	}

}
