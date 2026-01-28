/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal.compiler.plugins;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import uk.ac.manchester.tornado.api.exceptions.TornadoCompilationException;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.vectors.Byte3;
import uk.ac.manchester.tornado.api.types.vectors.Byte4;
import uk.ac.manchester.tornado.api.types.vectors.Double16;
import uk.ac.manchester.tornado.api.types.vectors.Double2;
import uk.ac.manchester.tornado.api.types.vectors.Double3;
import uk.ac.manchester.tornado.api.types.vectors.Double4;
import uk.ac.manchester.tornado.api.types.vectors.Double8;
import uk.ac.manchester.tornado.api.types.vectors.Float16;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;
import uk.ac.manchester.tornado.api.types.vectors.Half16;
import uk.ac.manchester.tornado.api.types.vectors.Half2;
import uk.ac.manchester.tornado.api.types.vectors.Half3;
import uk.ac.manchester.tornado.api.types.vectors.Half4;
import uk.ac.manchester.tornado.api.types.vectors.Half8;
import uk.ac.manchester.tornado.api.types.vectors.Int16;
import uk.ac.manchester.tornado.api.types.vectors.Int2;
import uk.ac.manchester.tornado.api.types.vectors.Int3;
import uk.ac.manchester.tornado.api.types.vectors.Int4;
import uk.ac.manchester.tornado.api.types.vectors.Int8;
import uk.ac.manchester.tornado.api.types.vectors.Short2;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.GetArrayNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.LoadIndexedVectorNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorAddNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorDivNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorLoadElementNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorMulNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorStoreElementProxyNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorStoreGlobalMemory;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorSubNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.PanamaPrivateMemoryNode;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

public final class PTXVectorPlugins {

    public static void registerPlugins(final Plugins ps, final InvocationPlugins plugins) {

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                PTXKind vectorKind = PTXKind.resolveToVectorKind(method.getDeclaringClass());
                if (vectorKind == PTXKind.ILLEGAL) {
                    return false;
                }
                if (method.getName().equals("<init>")) {
                    final var vector = resolveReceiver(b, vectorKind, args[0]);
                    if (args.length > 1) {
                        int offset = (vector == args[0]) ? 1 : 0;
                        for (int i = offset; i < args.length; i++) {
                            vector.setElement(i - offset, args[i]);
                        }
                    } else {
                        if (vectorKind.getVectorLength() < 8) {
                            vector.initialiseToDefaultValues(vector.graph());
                        }
                    }
                    return true;
                }
                return false;
            }

        });

        // Adding floats
        registerVectorPlugins(ps, plugins, PTXKind.FLOAT2, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, PTXKind.FLOAT3, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, PTXKind.FLOAT4, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, PTXKind.FLOAT8, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, PTXKind.FLOAT16, FloatArray.class, float.class);

        // Adding ints
        registerVectorPlugins(ps, plugins, PTXKind.INT2, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, PTXKind.INT3, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, PTXKind.INT4, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, PTXKind.INT8, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, PTXKind.INT16, IntArray.class, int.class);

        // Adding shorts
        registerVectorPlugins(ps, plugins, PTXKind.SHORT2, ShortArray.class, short.class);

        // Adding char
        registerVectorPlugins(ps, plugins, PTXKind.CHAR3, ByteArray.class, byte.class);
        registerVectorPlugins(ps, plugins, PTXKind.CHAR4, ByteArray.class, byte.class);

        // Adding double
        registerVectorPlugins(ps, plugins, PTXKind.DOUBLE2, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, PTXKind.DOUBLE3, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, PTXKind.DOUBLE4, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, PTXKind.DOUBLE8, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, PTXKind.DOUBLE16, DoubleArray.class, double.class);

        registerVectorPlugins(ps, plugins, PTXKind.HALF2, HalfFloat.class, short.class);
        registerVectorPlugins(ps, plugins, PTXKind.HALF3, HalfFloat.class, short.class);
        registerVectorPlugins(ps, plugins, PTXKind.HALF4, HalfFloat.class, short.class);
        registerVectorPlugins(ps, plugins, PTXKind.HALF8, HalfFloat.class, short.class);
        registerVectorPlugins(ps, plugins, PTXKind.HALF16, HalfFloat.class, short.class);

        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORFLOAT2, FloatArray.class, Float2.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORFLOAT3, FloatArray.class, Float3.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORFLOAT8, FloatArray.class, Float8.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORFLOAT16, FloatArray.class, Float16.class);

        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORINT2, IntArray.class, Int2.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORINT3, IntArray.class, Int3.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORINT4, IntArray.class, Int4.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORINT8, IntArray.class, Int8.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORINT16, IntArray.class, Int16.class);

        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORDOUBLE2, DoubleArray.class, Double2.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORDOUBLE3, DoubleArray.class, Double3.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORDOUBLE4, DoubleArray.class, Double4.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORDOUBLE8, DoubleArray.class, Double8.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORDOUBLE16, DoubleArray.class, Double16.class);

        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORHALF2, HalfFloatArray.class, Half2.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORHALF3, HalfFloatArray.class, Half3.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORHALF4, HalfFloatArray.class, Half4.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORHALF8, HalfFloatArray.class, Half8.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.VECTORHALF16, HalfFloatArray.class, Half16.class);

        registerVectorCollectionsPlugins(plugins, PTXKind.MATRIX2DFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.MATRIX3DFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.MATRIX4X4FLOAT, FloatArray.class, Float4.class);

        registerVectorCollectionsPlugins(plugins, PTXKind.IMAGEFLOAT3, FloatArray.class, Float3.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.IMAGEFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.IMAGEFLOAT8, FloatArray.class, Float8.class);

        registerVectorCollectionsPlugins(plugins, PTXKind.VOLUMESHORT2, ShortArray.class, Short2.class);

        registerVectorCollectionsPlugins(plugins, PTXKind.IMAGEBYTE3, ByteArray.class, Byte3.class);
        registerVectorCollectionsPlugins(plugins, PTXKind.IMAGEBYTE4, ByteArray.class, Byte4.class);
    }

    private static VectorValueNode resolveReceiver(GraphBuilderContext b, PTXKind vectorKind, ValueNode thisObject) {
        VectorValueNode vector = null;
        if (thisObject instanceof PiNode piNode) {
            thisObject = piNode.getOriginalNode();
        }
        if (thisObject instanceof VectorValueNode vectorValueNode) {
            vector = vectorValueNode;
        }
        guarantee(vector != null, "unable to resolve vector");
        return vector;
    }

    private static Class<?> resolveJavaClass(String panamaType) throws TornadoCompilationException {

        return switch (panamaType) {
            case String s when s.contains("IntArray") -> int.class;
            case String s when s.contains("DoubleArray") -> double.class;
            case String s when s.contains("FloatArray") -> float.class;
            case String s when s.contains("HalfFloatArray") -> short.class;
            default -> throw new TornadoCompilationException("Private vectors that use " + panamaType + " for storage are not currently supported.");
        };
    }

    private static void registerVectorCollectionsPlugins(final InvocationPlugins plugins, final PTXKind vectorKind, final Class<?> storageType, final Class<?> vectorClass) {
        final var declaringClass = vectorKind.getJavaClass();
        final var r = new Registration(plugins, declaringClass);
        r.register(new InvocationPlugin("loadFromArray", Receiver.class, storageType, int.class) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode index) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(vectorClass);
                PTXKind kind = PTXKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                // node needed to enforce the value of the nodes stamp
                LoadIndexedVectorNode indexedLoad = new LoadIndexedVectorNode(kind, array, index, elementKind);
                b.push(JavaKind.Object, b.append(indexedLoad));
                return true;
            }
        });

        r.register(new InvocationPlugin("storeToArray", Receiver.class, vectorClass, storageType, int.class) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode array, ValueNode index) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(vectorClass);
                PTXKind kind = PTXKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                // No need to set stamp as it is inferred from the stamp of the incoming value
                StoreIndexedNode indexedStore = new StoreIndexedNode(array, index, null, null, elementKind, value);
                b.append(b.append(indexedStore));
                return true;
            }
        });

    }

    private static void registerVectorPlugins(final Plugins ps, final InvocationPlugins plugins, final PTXKind vectorKind, final Class<?> storageType, final Class<?> elementType) {
        final var declaringClass = vectorKind.getJavaClass();
        final var javaElementKind = vectorKind.getElementKind().asJavaKind();
        final var r = new Registration(plugins, declaringClass);
        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (method.getName().equals("<init>") && (method.toString().contains("FloatArray.<init>(int)") || method.toString().contains("DoubleArray.<init>(int)") || method.toString()
                        .contains("IntArray.<init>(int)") || method.toString().contains("HalfFloatArray.<init>(int)"))) {
                    Class<?> javaType = resolveJavaClass(method.toString());
                    b.append(new PanamaPrivateMemoryNode(b.getMetaAccess().lookupJavaType(javaType), args[1]));
                    return true;
                }
                return false;
            }
        });
        r.register(new InvocationPlugin("get", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode laneId) {
                receiver.get(true);
                final VectorLoadElementNode loadElement = new VectorLoadElementNode(vectorKind.getElementKind(), receiver.get(true), laneId);
                b.push(javaElementKind, b.append(loadElement));
                return true;
            }
        });

        r.register(new InvocationPlugin("set", Receiver.class, vectorKind.getJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (receiver.get(true) instanceof ParameterNode) {
                    receiver.get(true);
                    final AddressNode address = new OffsetAddressNode(receiver.get(true), null);
                    final VectorStoreGlobalMemory store = new VectorStoreGlobalMemory(vectorKind, address, value);
                    b.add(b.append(store));
                    return true;
                }
                return false;
            }
        });

        r.register(new InvocationPlugin("set", Receiver.class, int.class, elementType) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode laneId, ValueNode value) {
                receiver.get(true);
                final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(vectorKind.getElementKind(), receiver.get(true), laneId, value);
                b.add(b.append(store));
                return true;
            }
        });

        r.register(new InvocationPlugin("set", Receiver.class, int.class, storageType) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode laneId, ValueNode value) {
                receiver.get(true);
                final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(vectorKind.getElementKind(), receiver.get(true), laneId, value);
                b.add(b.append(store));
                return true;
            }
        });

        r.register(new InvocationPlugin("add", Receiver.class, declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver reciever, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                PTXKind kind = PTXKind.fromResolvedJavaType(resolvedType);
                VectorAddNode addNode = new VectorAddNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(addNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("sub", Receiver.class, declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver reciever, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                PTXKind kind = PTXKind.fromResolvedJavaType(resolvedType);
                VectorSubNode subNode = new VectorSubNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(subNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("mult", Receiver.class, declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                PTXKind kind = PTXKind.fromResolvedJavaType(resolvedType);
                VectorMulNode multNode = new VectorMulNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(multNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("div", Receiver.class, declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                PTXKind kind = PTXKind.fromResolvedJavaType(resolvedType);
                VectorDivNode divNode = new VectorDivNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(divNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("getArray", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                PTXKind kind = PTXKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                ValueNode array = receiver.get(true);
                GetArrayNode getArrayNode = new GetArrayNode(kind, array, elementKind);
                b.push(JavaKind.Object, b.append(getArrayNode));
                return true;
            }
        });
    }

    static void registerParameterPlugins(Plugins plugins) {
        plugins.appendParameterPlugin((GraphBuilderTool tool, int index, StampPair stampPair) -> {
            if (stampPair.getTrustedStamp() instanceof ObjectStamp objectStamp) {
                if (objectStamp.type().getAnnotation(Vector.class) != null) {
                    PTXKind kind = PTXKind.fromResolvedJavaType(objectStamp.type());
                    return new ParameterNode(index, StampPair.createSingle(PTXStampFactory.getStampFor(kind)));
                }
            }
            return null;
        });
    }
}
