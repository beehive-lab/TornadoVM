/*
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoValueTypeReplacement;

public class TornadoTaskSpecialisation extends BasePhase<TornadoHighTierContext> {

    public static final int MAX_ITERATIONS = 10;

    private final CanonicalizerPhase canonicalizer;
    private final TornadoValueTypeReplacement valueTypeReplacement;
    private final DeadCodeEliminationPhase deadCodeElimination;
    // private final TornadoLoopUnroller loopUnroller;

    public TornadoTaskSpecialisation(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
        this.valueTypeReplacement = new TornadoValueTypeReplacement();
        this.deadCodeElimination = new DeadCodeEliminationPhase();
        // this.loopUnroller = new TornadoLoopUnroller(canonicalizer);

    }

    private Field lookupField(Class<?> type, String field) {
        Field f = null;
        try {
            f = type.getDeclaredField(field);
            if (!f.isAccessible()) {
                f.setAccessible(true);
            }
        } catch (NoSuchFieldException | SecurityException e) {
            if (type.getSuperclass() != null) {
                f = lookupField(type.getSuperclass(), field);
            } else {
                e.printStackTrace();
            }
        }
        return f;
    }

    @FunctionalInterface
    private interface FunctionThatThrows<T, R> {
        R apply(T t) throws IllegalArgumentException, IllegalAccessException;
    }

    private <T> T lookup(Object object, FunctionThatThrows<Object, T> function) throws IllegalArgumentException, IllegalAccessException {
        return function.apply(object);
    }

    private Object lookupRefField(StructuredGraph graph, Node node, Object obj, String field) {
        final Class<?> type = obj.getClass();
        final Field f = lookupField(type, field);
        Object result = null;
        try {
            result = f.get(obj);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private ConstantNode lookupPrimField(StructuredGraph graph, Node node, Object obj, String field, JavaKind kind) {
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
                        node.usages().filter(LoadFieldNode.class).forEach(load -> evaluate(graph, load, value));
                        node.usages().filter(ArrayLengthNode.class).forEach(arrayLength -> evaluate(graph, arrayLength, value));
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

    private void evaluate(final StructuredGraph graph, final Node node, final Object value) {

        // final Object value = (param instanceof ObjectReference) ?
        // ((ObjectReference<?,?>) param)
        // .get() : param;
        // Tornado.debug("evaluate: node=%s, object=%s", node, value);
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
            // Tornado.debug("load field: name=%s, type=%s, declaring class=%s",
            // field.getName(),
            // field.getType().toJavaName(),
            // field.getDeclaringClass().getName());
            if (field.getType().getJavaKind().isPrimitive()) {
                ConstantNode constant = lookupPrimField(graph, node, value, field.getName(), field.getJavaKind());
                constant = graph.addOrUnique(constant);
                // Tornado.debug("Replaced %s with %s", node, constant);
                node.replaceAtUsages(constant);
                loadField.clearInputs();
                graph.removeFixed(loadField);
                // Tornado.debug("removed %s", loadField);
            } else if (field.isFinal()) {
                // Tornado.debug("propagating final fields...");
                Object object = lookupRefField(graph, node, value, field.getName());
                node.usages().forEach(n -> evaluate(graph, n, object));
            }
        } else if (node instanceof IsNullNode) {
            final IsNullNode isNullNode = (IsNullNode) node;
            final boolean isNull = (value == null);
            if (isNull) {
                isNullNode.replaceAtUsages(LogicConstantNode.tautology(graph));
            } else {
                isNullNode.replaceAtUsages(LogicConstantNode.contradiction(graph));
            }
            isNullNode.safeDelete();
        } else if (node instanceof PiNode) {
            PiNode piNode = (PiNode) node;
            piNode.replaceAtUsages(piNode.getOriginalNode());
            piNode.safeDelete();
        }
    }

    private ConstantNode createConstantFromObject(Object obj) {
        ConstantNode result = null;
        if (obj instanceof Float) {
            result = ConstantNode.forFloat((float) obj);
        } else if (obj instanceof Integer) {
            result = ConstantNode.forInt((int) obj);
        } else if (obj instanceof Double) {
            result = ConstantNode.forDouble((double) obj);
        } else {
            unimplemented("createConstantFromObject: %s", obj);
        }

        return result;
    }

    private void propagateParameters(StructuredGraph graph, ParameterNode parameterNode, Object[] args) {
        if (args[parameterNode.index()] != null && RuntimeUtilities.isBoxedPrimitiveClass(args[parameterNode.index()].getClass())) {
            ConstantNode constant = createConstantFromObject(args[parameterNode.index()]);
            graph.addWithoutUnique(constant);
            parameterNode.replaceAtUsages(constant);
        } else {
            parameterNode.usages().snapshot().forEach(n -> evaluate(graph, n, args[parameterNode.index()]));
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
                    if (OpenCL.ACCELERATOR_IS_FPGA)
                        ;
                    else
                        propagateParameters(graph, param, context.getArgs());
                }
                Debug.dump(Debug.INFO_LEVEL, graph, "After Phase Propagate Parameters");
            } else {
                for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                    assumeNonNull(graph, param);
                }
                Debug.dump(Debug.INFO_LEVEL, graph, "After Phase assume non null Parameters");
            }

            canonicalizer.apply(graph, context);

            graph.getNewNodes(mark).filter(PiNode.class).forEach(pi -> {
                if (pi.stamp() instanceof ObjectStamp && pi.object().stamp() instanceof ObjectStamp) {
                    pi.replaceAtUsages(pi.object());

                    pi.clearInputs();
                    pi.safeDelete();
                    // graph.removeFloating(pi);
                }
            });

            Debug.dump(Debug.INFO_LEVEL, graph, "After Phase Pi Node Removal");

            if (!OpenCL.ACCELERATOR_IS_FPGA) {
                loopUnroller.execute(graph, context);
            }

            valueTypeReplacement.execute(graph, context);

            canonicalizer.apply(graph, context);

            deadCodeElimination.run(graph);

            Debug.dump(Debug.INFO_LEVEL, graph, "After TaskSpecialisation iteration=" + iterations);

            // boolean hasGuardingPiNodes =
            // graph.getNodes().filter(GuardingPiNode.class).isNotEmpty();
            hasWork = (lastNodeCount != graph.getNodeCount() || graph.getNewNodes(mark).isNotEmpty()) // ||
                                                                                                      // hasGuardingPiNodes)
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
        if (param.getStackKind().isObject() && param.usages().filter(IsNullNode.class).count() > 0) {
            final IsNullNode isNullNode = (IsNullNode) param.usages().filter(IsNullNode.class).first();
            // for (final GuardingPiNode guardingPiNode :
            // isNullNode.usages().filter(GuardingPiNode.class).distinct()) {
            // guardingPiNode.replaceAtUsages(param);
            // }

        }

    }

}
