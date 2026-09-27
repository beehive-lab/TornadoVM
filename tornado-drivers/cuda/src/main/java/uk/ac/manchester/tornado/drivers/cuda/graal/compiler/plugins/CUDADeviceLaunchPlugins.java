/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.compiler.plugins;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.ConstantNode;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.extended.BoxNode;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import tornado.graal.compiler.nodes.java.LoadFieldNode;
import tornado.graal.compiler.nodes.java.NewArrayNode;
import tornado.graal.compiler.nodes.java.StoreIndexedNode;
import tornado.graal.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.enums.DeviceLaunchMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDADeviceLaunchNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.runtime.analyzer.TaskUtils;
import uk.ac.manchester.tornado.runtime.jvmci.TornadoObjectConstant;

/**
 * Intrinsifies {@code KernelContext.launch} and {@code launch3D} (CUDA Dynamic Parallelism) into a
 * {@link CUDADeviceLaunchNode}.
 *
 * <p>
 * The child is named by a {@code static final DeviceKernel} field. Its {@code getstatic} is not
 * folded while the graph is built and TornadoVM cannot turn an object constant back into an object,
 * so the field is read on the host, by reflection, and the method reference it holds is resolved the
 * same way a task's entry point is.
 * </p>
 */
final class CUDADeviceLaunchPlugins {

    private static final String FIELD_REQUIREMENT = "The DeviceKernel passed to KernelContext.launch must be read from a static final field, "
            + "e.g. static final DeviceKernel CHILD = DeviceKernel.of(MyKernels::child);";

    private static final String MODE_REQUIREMENT = "The DeviceLaunchMode passed to KernelContext.launch must be a constant, e.g. DeviceLaunchMode.TAIL.";

    private static final String LOCAL_ARRAY_REJECTION = " is a local-memory array (KernelContext.allocate*LocalArray). Local memory belongs to the launching thread block "
            + "and is not visible to a kernel it launches; pass a global array instead.";

    private CUDADeviceLaunchPlugins() {
    }

    static void register(Registration r) {
        r.register(new LaunchPlugin(false, false, DeviceKernel.class, int.class, int.class, Object[].class));
        r.register(new LaunchPlugin(true, false, DeviceKernel.class, DeviceLaunchMode.class, int.class, int.class, Object[].class));
        r.register(new LaunchPlugin(true, true, DeviceKernel.class, DeviceLaunchMode.class, int.class, int.class, int.class, int.class, int.class, int.class, Object[].class));
    }

    private static final class LaunchPlugin extends InvocationPlugin {

        private final boolean hasMode;
        private final boolean is3D;
        private final int parameterCount;

        LaunchPlugin(boolean hasMode, boolean is3D, Class<?>... parameters) {
            super(is3D ? "launch3D" : "launch", prepend(Receiver.class, parameters));
            this.hasMode = hasMode;
            this.is3D = is3D;
            this.parameterCount = parameters.length;
        }

        private static Class<?>[] prepend(Class<?> first, Class<?>[] rest) {
            Class<?>[] all = new Class<?>[rest.length + 1];
            all[0] = first;
            System.arraycopy(rest, 0, all, 1, rest.length);
            return all;
        }

        @Override
        public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
            // No null check on the KernelContext: it is unused, and in a kernel that was itself
            // launched from the device the parameter is not known to be non-null, so a check would
            // survive to code generation, which has no lowering for it.
            receiver.get(false);
            // Low-arity plugins get the arguments without the receiver, wider ones with it.
            ValueNode[] actual = args;
            if (args.length == parameterCount + 1) {
                actual = new ValueNode[parameterCount];
                System.arraycopy(args, 1, actual, 0, parameterCount);
            }

            int next = 0;
            ResolvedJavaMethod child = resolveChild(b, actual[next++]);
            DeviceLaunchMode mode = hasMode ? CUDAEnumFolding.resolveEnumConstant(b, actual[next++], DeviceLaunchMode.class, MODE_REQUIREMENT) : DeviceLaunchMode.DEFAULT;

            ValueNode one = ConstantNode.forInt(1, b.getGraph());
            ValueNode[] sizes;
            if (is3D) {
                sizes = new ValueNode[6];
                for (int i = 0; i < 6; i++) {
                    sizes[i] = actual[next++];
                }
            } else {
                sizes = new ValueNode[] { actual[next], one, one, actual[next + 1], one, one };
                next += 2;
            }

            NewArrayNode varargs = varargsArray(actual[next]);
            ValueNode[] launchArguments = varargsValues(varargs);
            int kernelContextIndex = checkSignature(b, child, launchArguments);
            b.add(new CUDADeviceLaunchNode(child, mode, sizes, launchArguments, kernelContextIndex));
            // Only now that the launch is in the graph: the builder appends after its last fixed
            // node, which until then is one of the array stores removed here.
            removeVarargs(varargs);
            return true;
        }
    }

    /** The child kernel named by the {@code DeviceKernel} argument. */
    private static ResolvedJavaMethod resolveChild(GraphBuilderContext b, ValueNode node) {
        Object handle = null;
        if (node instanceof LoadFieldNode load && load.field().isStatic()) {
            handle = readStaticFinalField(b, load.field());
        } else {
            JavaConstant constant = node.asJavaConstant();
            if (constant instanceof TornadoObjectConstant objectConstant) {
                handle = objectConstant.getObject();
            }
        }
        if (!(handle instanceof DeviceKernel deviceKernel)) {
            throw new TornadoRuntimeException(FIELD_REQUIREMENT);
        }
        Method method = TaskUtils.resolveMethodHandle(deviceKernel.getKernel());
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new TornadoRuntimeException("A kernel launched from the device must be a static method: " + method);
        }
        return b.getMetaAccess().lookupJavaMethod(method);
    }

    private static Object readStaticFinalField(GraphBuilderContext b, ResolvedJavaField field) {
        try {
            Field javaField = holderClass(b, field).getDeclaredField(field.getName());
            if (!Modifier.isFinal(javaField.getModifiers())) {
                throw new TornadoRuntimeException(FIELD_REQUIREMENT + " Field " + field.format("%H.%n") + " is not final.");
            }
            javaField.setAccessible(true);
            return javaField.get(null);
        } catch (ReflectiveOperationException e) {
            throw new TornadoRuntimeException("Unable to read DeviceKernel field " + field.format("%H.%n") + ": " + e);
        }
    }

    /** The class declaring {@code field}, as a host {@link Class}. */
    private static Class<?> holderClass(GraphBuilderContext b, ResolvedJavaField field) throws ClassNotFoundException {
        JavaConstant mirror = b.getConstantReflection().asJavaClass(field.getDeclaringClass());
        if (mirror instanceof TornadoObjectConstant objectConstant && objectConstant.getObject() instanceof Class<?> holder) {
            return holder;
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return Class.forName(field.getDeclaringClass().toJavaName(), true, loader != null ? loader : ClassLoader.getSystemClassLoader());
    }

    private static NewArrayNode varargsArray(ValueNode node) {
        if (!(node instanceof NewArrayNode newArray) || newArray.dimension(0).asJavaConstant() == null) {
            throw new TornadoRuntimeException("The arguments of KernelContext.launch must be passed directly, not as a pre-built Object[].");
        }
        return newArray;
    }

    /** The values javac stored into the varargs {@code Object[]}, in order and unboxed. */
    private static ValueNode[] varargsValues(NewArrayNode newArray) {
        int length = newArray.dimension(0).asJavaConstant().asInt();
        ValueNode[] values = new ValueNode[length];
        for (Node usage : newArray.usages()) {
            if (usage instanceof StoreIndexedNode store && store.index().isJavaConstant()) {
                ValueNode value = store.value();
                if (value instanceof BoxNode box) {
                    value = box.getValue();
                }
                values[store.index().asJavaConstant().asInt()] = value;
            }
        }
        for (int i = 0; i < length; i++) {
            if (values[i] == null) {
                throw new TornadoRuntimeException("Could not determine argument " + i + " of KernelContext.launch.");
            }
        }
        return values;
    }

    /**
     * Removes the varargs array, its stores and the boxes that fed them, without leaving gaps
     * between fixed nodes.
     */
    private static void removeVarargs(NewArrayNode newArray) {
        while (newArray.hasUsages()) {
            Node n = newArray.usages().first();
            BoxNode box = n instanceof StoreIndexedNode store && store.value() instanceof BoxNode b ? b : null;
            if (n instanceof FixedWithNextNode fixed) {
                GraphUtil.unlinkFixedNode(fixed);
            }
            n.clearInputs();
            n.safeDelete();
            if (box != null && box.hasNoUsages()) {
                GraphUtil.unlinkFixedNode(box);
                box.clearInputs();
                box.safeDelete();
            }
        }
        GraphUtil.unlinkFixedNode(newArray);
        newArray.clearInputs();
        newArray.safeDelete();
    }

    /**
     * Checks the launch arguments against the child's parameters (after its KernelContext) and
     * returns the position of the child's KernelContext parameter, or -1 if it has none.
     */
    private static int checkSignature(GraphBuilderContext b, ResolvedJavaMethod child, ValueNode[] arguments) {
        Signature signature = child.getSignature();
        int count = signature.getParameterCount(false);
        int kernelContextIndex = -1;
        int next = 0;
        for (int i = 0; i < count; i++) {
            JavaType type = signature.getParameterType(i, child.getDeclaringClass());
            if (type.toJavaName().equals(KernelContext.class.getName())) {
                kernelContextIndex = i;
                continue;
            }
            if (next >= arguments.length) {
                throw new TornadoRuntimeException(mismatch(child, arguments.length));
            }
            if (GraphUtil.unproxify(arguments[next]) instanceof LocalArrayNode) {
                throw new TornadoRuntimeException("Argument " + next + " of the launch of " + child.format("%H.%n") + LOCAL_ARRAY_REJECTION);
            }
            JavaKind expected = signature.getParameterKind(i).getStackKind();
            JavaKind actual = arguments[next].getStackKind();
            if (expected != actual) {
                throw new TornadoRuntimeException("Argument " + next + " of the launch of " + child.format("%H.%n") + " is a " + actual.getJavaName() + " but parameter "
                        + i + " of the kernel is a " + type.toJavaName() + ".");
            }
            next++;
        }
        if (next != arguments.length) {
            throw new TornadoRuntimeException(mismatch(child, arguments.length));
        }
        return kernelContextIndex;
    }

    private static String mismatch(ResolvedJavaMethod child, int given) {
        return "KernelContext.launch of " + child.format("%H.%n(%p)") + " was given " + given + " arguments; pass one for every parameter after the KernelContext.";
    }
}
