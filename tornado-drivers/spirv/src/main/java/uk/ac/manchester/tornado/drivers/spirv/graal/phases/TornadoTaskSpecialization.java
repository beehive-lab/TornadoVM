/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2023 APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.analysis.TornadoValueTypeReplacement;
import uk.ac.manchester.tornado.drivers.common.compiler.phases.loops.TornadoLoopUnroller;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVKernelContextAccessNode;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoTaskSpecialization extends BasePhase<TornadoHighTierContext> {

    private static final int MAX_ITERATIONS = 15;
    private static final String WARNING_GRID_SCHEDULER_DYNAMIC_LOOP_BOUNDS = "[TornadoVM] Warning: The loop bounds will be configured by the GridScheduler. Check the grid by using the flag --threadInfo.";
    private final CanonicalizerPhase canonicalizer;
    private final TornadoValueTypeReplacement valueTypeReplacement;
    private final DeadCodeEliminationPhase deadCodeElimination;
    private final TornadoLoopUnroller loopUnroll;
    private long batchThreads;
    private boolean gridScheduling;
    private int index;
    private boolean printOnce = true;

    public TornadoTaskSpecialization(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
        this.valueTypeReplacement = new TornadoValueTypeReplacement();
        this.deadCodeElimination = new DeadCodeEliminationPhase();
        this.loopUnroll = new TornadoLoopUnroller(canonicalizer);
    }

    private static boolean hasPanamaArraySizeNode(StructuredGraph graph) {
        for (LoadFieldNode loadField : graph.getNodes().filter(LoadFieldNode.class)) {
            final ResolvedJavaField field = loadField.field();
            if (field.getType().getJavaKind().isPrimitive()) {
                if (loadField.toString().contains("numberOfElements")) {
                    return true;
                }
            }
        }
        return false;
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

    private <T> T lookup(Object object, TornadoTaskSpecialization.FunctionThatThrows<Object, T> function) throws IllegalArgumentException, IllegalAccessException {
        return function.apply(object);
    }

    private Object lookupRefField(StructuredGraph graph, Node node, Object obj, String field) {
        final Class<?> type = obj.getClass();
        final Field f = lookupField(type, field);
        Object result;
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
                     * propagate all constants from connected final fields
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
            e.printStackTrace();
        }
        return constant;
    }

    private void printWarningMessageForDynamicLoopBounds() {
        if (printOnce) {
            System.out.println(WARNING_GRID_SCHEDULER_DYNAMIC_LOOP_BOUNDS);
            printOnce = false;
        }
    }

    private void evaluate(final StructuredGraph graph, final Node node, final Object value) {
        if (node instanceof ArrayLengthNode arrayLength) {
            int length = Array.getLength(value);

            /*
             * This condition covers the case that loop bounds should be taken based on the
             * grid size given by {@link GridScheduler}. This allows the loop bounds to be
             * dynamically configured, without requiring recompilation.
             *
             * This condition will disable the FPGA HLS loop optimizations, because the loop
             * bound is not retrievable at compile time.
             */
            if (gridScheduling && isParameterInvolvedInParallelLoopBound(node)) {
                printWarningMessageForDynamicLoopBounds();
                ConstantNode constantValue = graph.addOrUnique(ConstantNode.forInt(index));
                SPIRVKernelContextAccessNode kernelContextAccessNode = graph.addOrUnique(new SPIRVKernelContextAccessNode(constantValue));
                node.replaceAtUsages(kernelContextAccessNode);
                index++;
            } else {
                final ConstantNode constant = (batchThreads <= 0) ? ConstantNode.forInt(length) : ConstantNode.forInt((int) batchThreads);
                node.replaceAtUsages(graph.addOrUnique(constant));
            }
            arrayLength.clearInputs();
            GraphUtil.removeFixedWithUnusedInputs(arrayLength);
        } else if (node instanceof LoadFieldNode loadField) {
            final ResolvedJavaField field = loadField.field();
            if (field.getType().getJavaKind().isPrimitive()) {
                ConstantNode constant;
                if (node.toString().contains("numberOfElements")) {
                    if (batchThreads <= 0) {
                        constant = lookupPrimField(graph, node, value, field.getName(), field.getJavaKind());
                    } else {
                        constant = ConstantNode.forInt((int) batchThreads);
                    }
                } else {
                    constant = lookupPrimField(graph, node, value, field.getName(), field.getJavaKind());
                }
                constant = graph.addOrUnique(constant);
                node.replaceAtUsages(constant);
                loadField.clearInputs();
                graph.removeFixed(loadField);
            } else if (field.isFinal()) {
                Object object = lookupRefField(graph, node, value, field.getName());
                node.usages().forEach(n -> evaluate(graph, n, object));
            } else if (!field.isFinal()) {
                throw new TornadoBailoutRuntimeException("Non-final objects introduced via scope are not supported");
            }
        } else if (node instanceof IsNullNode isNullNode) {
            final boolean isNull = (value == null);
            if (isNull) {
                isNullNode.replaceAtUsages(LogicConstantNode.tautology(graph));
            } else {
                isNullNode.replaceAtUsages(LogicConstantNode.contradiction(graph));
            }
            isNullNode.safeDelete();
        } else if (node instanceof PiNode piNode) {
            piNode.replaceAtUsages(piNode.getOriginalNode());
            piNode.safeDelete();
        }
    }

    private ConstantNode createPrimitiveConstantFromObjectParameter(Object obj, StructuredGraph graph) {
        return switch (obj) {
            case Boolean objBoolean -> ConstantNode.forBoolean(objBoolean, graph);
            case Byte objByte -> ConstantNode.forByte(objByte, graph);
            case Character objChar -> ConstantNode.forChar(objChar, graph);
            case Short objShort -> ConstantNode.forShort(objShort, graph);
            case HalfFloat objHalfFloat -> ConstantNode.forFloat(objHalfFloat.getFloat32(), graph);
            case Integer objInteger -> ConstantNode.forInt(objInteger, graph);
            case Float objFloat -> ConstantNode.forFloat(objFloat, graph);
            case Double objDouble -> ConstantNode.forDouble(objDouble, graph);
            case Long objLong -> ConstantNode.forLong(objLong, graph);
            case Object object -> {
                unimplemented("createPrimitiveConstantFromObjectParameter: %s", obj);
                yield null;
            }
        };
    }

    private boolean isParameterInvolvedInParallelLoopBound(Node parameterNode) {
        AtomicBoolean parameterInLoopBound = new AtomicBoolean(false);
        parameterNode.usages().snapshot().forEach(node -> {
            if (node instanceof ParallelRangeNode) {
                parameterInLoopBound.set(true);
            }
        });
        return parameterInLoopBound.get();
    }

    private void propagateParameters(StructuredGraph graph, ParameterNode parameterNode, Object[] args) {
        if (args[parameterNode.index()] != null && RuntimeUtilities.isBoxedPrimitiveClass(args[parameterNode.index()].getClass())) {
            /*
             * This condition covers the case that loop bounds should be taken based on the
             * grid size given by {@link GridScheduler}. This allows the loop bounds to be
             * dynamically configured, without requiring recompilation.
             *
             * This condition will disable the FPGA HLS loop optimizations, because the loop
             * bound is not retrievable at compile time.
             */
            if (gridScheduling && isParameterInvolvedInParallelLoopBound(parameterNode)) {
                printWarningMessageForDynamicLoopBounds();
                ConstantNode constantValue = graph.addOrUnique(ConstantNode.forInt(index));
                SPIRVKernelContextAccessNode kernelContextAccessNode = graph.addOrUnique(new SPIRVKernelContextAccessNode(constantValue));
                parameterNode.replaceAtUsages(kernelContextAccessNode);
                index++;
            } else {
                var value = args[parameterNode.index()];
                ConstantNode primitiveConstant = createPrimitiveConstantFromObjectParameter(value, graph);

                parameterNode.replaceAtAllUsages(primitiveConstant, true);
                parameterNode.safeDelete();

                //remove Unbox nodes, they are not needed for constant values and cause compilation errors.
                //Kernels work only with primitive type operands.
                //So every primitive wrapper bound to have UnboxNode that can also be optionally protected by FixedGuardNode that checks that parameter is not null.
                //This pair is removed for constant values.
                //Otherwise there will be problems during Graph canonicalization.
                graph.getNodes().filter(n -> n instanceof PiNode piNode && piNode.object() == primitiveConstant).snapshot().forEach(node -> {
                    var usagesSnapshot = node.usages().snapshot();
                    node.replaceAtAllUsages(primitiveConstant, true);
                    node.safeDelete();

                    usagesSnapshot.forEach(n -> {
                        if (n instanceof UnboxNode unboxNode) {
                            var prev = n.predecessor();

                            unboxNode.replaceAtAllUsages(primitiveConstant, true);
                            graph.removeFixed(unboxNode);

                            if (prev instanceof FixedGuardNode fixedGuardNode) {
                                if (fixedGuardNode.condition() instanceof IsNullNode isNullNode && isNullNode.getValue() == primitiveConstant) {
                                    fixedGuardNode.clearInputs();
                                    graph.removeFixed(fixedGuardNode);
                                }
                            }
                        }
                    });
                });
            }
        } else {
            parameterNode.usages().snapshot().forEach(n -> {
                evaluate(graph, n, args[parameterNode.index()]);
            });
        }
    }

    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        int iterations = 0;
        int lastNodeCount = graph.getNodeCount();
        boolean hasWork = true;
        this.batchThreads = context.getBatchCompilationConfig().getBatchThreads();
        this.gridScheduling = context.isGridSchedulerEnabled();

        while (hasWork) {
            final Graph.Mark mark = graph.getMark();
            if (context.hasArgs()) {
                getDebugContext().dump(DebugContext.INFO_LEVEL, graph, "Before Phase Propagate Parameters");
                for (final ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                    propagateParameters(graph, param, context.getArgs());
                }
                getDebugContext().dump(DebugContext.INFO_LEVEL, graph, "After Phase Propagate Parameters");
            }

            canonicalizer.apply(graph, context);

            graph.getNewNodes(mark).filter(PiNode.class).forEach(pi -> {
                if (pi.stamp(NodeView.DEFAULT) instanceof ObjectStamp && pi.object().stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                    pi.replaceAtUsages(pi.object());
                    pi.clearInputs();
                    pi.safeDelete();
                }
            });

            getDebugContext().dump(DebugContext.INFO_LEVEL, graph, "After Phase Pi Node Removal");

            loopUnroll.execute(graph, context);

            valueTypeReplacement.execute(graph, context);

            canonicalizer.apply(graph, context);

            deadCodeElimination.run(graph);

            getDebugContext().dump(DebugContext.INFO_LEVEL, graph, "After TaskSpecialisation iteration = " + iterations);

            hasWork = (lastNodeCount != graph.getNodeCount() || graph.getNewNodes(mark).isNotEmpty() || hasPanamaArraySizeNode(graph)) && (iterations < MAX_ITERATIONS);
            lastNodeCount = graph.getNodeCount();
            iterations++;
        }

        graph.getNodes().filter(ParallelRangeNode.class).forEach(range -> {
            if (range.value() instanceof PhiNode phiNode) {
                NodeIterable<Node> usages = range.usages();
                for (Node usage : usages) {
                    if (usage instanceof IntegerLessThanNode less) {
                        ConstantNode constant = null;
                        if (less.getX() instanceof ConstantNode) {
                            constant = (ConstantNode) less.getX();
                        } else if (less.getY() instanceof ConstantNode) {
                            constant = (ConstantNode) less.getY();
                        }

                        // we swap the values between the new Constant and the PhiNode
                        if (constant != null) {
                            getDebugContext().dump(DebugContext.INFO_LEVEL, graph, "Before Swapping Constant-Phi");
                            ParallelRangeNode pr = new ParallelRangeNode(range.index(), constant, range.offset(), range.stride());
                            graph.addOrUnique(pr);
                            range.safeDelete();

                            IntegerLessThanNode intLess = new IntegerLessThanNode(phiNode, pr);
                            graph.addOrUnique(intLess);
                            less.usages().first().replaceAllInputs(less, intLess);
                            less.safeDelete();
                            getDebugContext().dump(DebugContext.INFO_LEVEL, graph, "After Swapping Constant-Phi");
                        }
                    }
                }
            }
        });

        TornadoLogger logger = new TornadoLogger(this.getClass());
        if (iterations == MAX_ITERATIONS) {
            logger.warn("TaskSpecialisation unable to complete after %d iterations", iterations);
        }
        logger.debug("TaskSpecialisation ran %d iterations", iterations);
        logger.debug("valid graph? %s", graph.verify());
        index = 0;
    }

    @FunctionalInterface
    private interface FunctionThatThrows<T, R> {
        R apply(T t) throws IllegalArgumentException, IllegalAccessException;
    }
}
