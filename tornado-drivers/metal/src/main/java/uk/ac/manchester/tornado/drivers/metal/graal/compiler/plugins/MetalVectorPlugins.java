/*
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.metal.graal.compiler.plugins;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.TORNADO_ENABLE_BIFS;

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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.exceptions.TornadoCompilationException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
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
import uk.ac.manchester.tornado.drivers.metal.graal.MetalStampFactory;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.GetArrayNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.LoadIndexedVectorNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.VectorAddNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.VectorDivNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.VectorLoadElementNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.VectorMulNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.VectorStoreElementProxyNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.VectorStoreGlobalMemory;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.VectorSubNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.PanamaPrivateMemoryNode;

public final class MetalVectorPlugins {

    public static void registerPlugins(final Plugins ps, final InvocationPlugins plugins) {

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                MetalKind vectorKind = MetalKind.resolveToVectorKind(method.getDeclaringClass());

                if (vectorKind == MetalKind.ILLEGAL) {
                    return false;
                }
                if (method.getName().equals("<init>")) {
                    final VectorValueNode vector = resolveReceiver(args[0]);
                    if (args.length > 1) {
                        int offset = (vector == args[0]) ? 1 : 0;
                        for (int i = offset; i < args.length; i++) {
                            vector.setElement(i - offset, args[i]);
                        }
                    } else {
                        // BUG check whether this should be <= 8
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
        registerVectorPlugins(ps, plugins, MetalKind.FLOAT2, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, MetalKind.FLOAT3, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, MetalKind.FLOAT4, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, MetalKind.FLOAT8, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, MetalKind.FLOAT16, FloatArray.class, float.class);

        // Adding half floats
        registerVectorPlugins(ps, plugins, MetalKind.HALF2, HalfFloatArray.class, HalfFloat.class);
        registerVectorPlugins(ps, plugins, MetalKind.HALF3, HalfFloatArray.class, HalfFloat.class);
        registerVectorPlugins(ps, plugins, MetalKind.HALF4, HalfFloatArray.class, HalfFloat.class);
        registerVectorPlugins(ps, plugins, MetalKind.HALF8, HalfFloatArray.class, HalfFloat.class);
        registerVectorPlugins(ps, plugins, MetalKind.HALF16, HalfFloatArray.class, HalfFloat.class);

        // Adding ints
        registerVectorPlugins(ps, plugins, MetalKind.INT2, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, MetalKind.INT3, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, MetalKind.INT4, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, MetalKind.INT8, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, MetalKind.INT16, IntArray.class, int.class);

        // Adding shorts
        registerVectorPlugins(ps, plugins, MetalKind.SHORT2, ShortArray.class, short.class);

        // Adding char
        registerVectorPlugins(ps, plugins, MetalKind.CHAR3, ByteArray.class, byte.class);
        registerVectorPlugins(ps, plugins, MetalKind.CHAR4, ByteArray.class, byte.class);

        // Adding double
        registerVectorPlugins(ps, plugins, MetalKind.DOUBLE2, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, MetalKind.DOUBLE3, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, MetalKind.DOUBLE4, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, MetalKind.DOUBLE8, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, MetalKind.DOUBLE16, DoubleArray.class, double.class);

        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORFLOAT2, FloatArray.class, Float2.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORFLOAT3, FloatArray.class, Float3.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORFLOAT8, FloatArray.class, Float8.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORFLOAT16, FloatArray.class, Float16.class);

        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORINT2, IntArray.class, Int2.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORINT3, IntArray.class, Int3.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORINT4, IntArray.class, Int4.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORINT8, IntArray.class, Int8.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORINT16, IntArray.class, Int16.class);

        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORDOUBLE2, DoubleArray.class, Double2.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORDOUBLE3, DoubleArray.class, Double3.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORDOUBLE4, DoubleArray.class, Double4.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORDOUBLE8, DoubleArray.class, Double8.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORDOUBLE16, DoubleArray.class, Double16.class);

        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORHALF2, HalfFloatArray.class, Half2.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORHALF3, HalfFloatArray.class, Half3.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORHALF4, HalfFloatArray.class, Half4.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORHALF8, HalfFloatArray.class, Half8.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.VECTORHALF16, HalfFloatArray.class, Half16.class);

        registerVectorCollectionsPlugins(plugins, MetalKind.MATRIX2DFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.MATRIX3DFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.MATRIX4X4FLOAT, FloatArray.class, Float4.class);

        registerVectorCollectionsPlugins(plugins, MetalKind.IMAGEFLOAT3, FloatArray.class, Float3.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.IMAGEFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.IMAGEFLOAT8, FloatArray.class, Float8.class);

        registerVectorCollectionsPlugins(plugins, MetalKind.VOLUMESHORT2, ShortArray.class, Short2.class);

        registerVectorCollectionsPlugins(plugins, MetalKind.IMAGEBYTE3, ByteArray.class, Byte3.class);
        registerVectorCollectionsPlugins(plugins, MetalKind.IMAGEBYTE4, ByteArray.class, Byte4.class);

        /*
         * Geometric BIFS for floating point vectors
         */
        if (TORNADO_ENABLE_BIFS) {
            registerGeometricBIFS(plugins, MetalKind.FLOAT3);
            registerGeometricBIFS(plugins, MetalKind.FLOAT4);
        }
    }

    private static VectorValueNode resolveReceiver(ValueNode thisObject) {
        VectorValueNode vector = null;
        if (thisObject instanceof PiNode) {
            thisObject = ((PiNode) thisObject).getOriginalNode();
        }
        if (thisObject instanceof VectorValueNode vectorValueNode) {
            vector = vectorValueNode;
        }
        guarantee(vector != null, "[Vector Plugins] unable to resolve vector");
        return vector;
    }

    private static VectorValueNode resolveReceiver(Receiver receiver) {
        ValueNode thisObject = receiver.get();
        return resolveReceiver(thisObject);
    }

    private static Class<?> resolveJavaClass(String panamaType) throws TornadoCompilationException {
        if (panamaType.contains("IntArray")) {
            return int.class;
        } else if (panamaType.contains("DoubleArray")) {
            return double.class;
        } else if (panamaType.contains("FloatArray")) {
            return float.class;
        } else if (panamaType.contains("HalfFloatArray")) {
            return short.class;
        } else {
            throw new TornadoCompilationException("Private vectors that use " + panamaType + " for storage are not currently supported.");
        }
    }

    private static void registerVectorCollectionsPlugins(final InvocationPlugins plugins, final MetalKind vectorKind, final Class<?> storageType, final Class<?> vectorClass) {

        final Class<?> declaringClass = vectorKind.getJavaClass();

        final Registration r = new Registration(plugins, declaringClass);
        r.register(new InvocationPlugin("loadFromArray", Receiver.class, storageType, int.class) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode index) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(vectorClass);
                MetalKind kind = MetalKind.fromResolvedJavaType(resolvedType);
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
                MetalKind kind = MetalKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                // No need to set stamp as it is inferred from the stamp of the incoming value
                StoreIndexedNode indexedStore = new StoreIndexedNode(array, index, null, null, elementKind, value);
                b.append(b.append(indexedStore));
                return true;
            }
        });

        r.register(new InvocationPlugin("getArray", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(vectorClass);
                MetalKind kind = MetalKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                ValueNode array = receiver.get();
                GetArrayNode getArrayNode = new GetArrayNode(kind, array, elementKind);
                b.push(JavaKind.Object, b.append(getArrayNode));
                return true;
            }
        });

    }

    private static void registerVectorPlugins(final Plugins ps, final InvocationPlugins plugins, final MetalKind vectorKind, final Class<?> storageType, final Class<?> elementType) {

        final Class<?> declaringClass = vectorKind.getJavaClass();
        final JavaKind javaElementKind = vectorKind.getElementKind().asJavaKind();

        final Registration r = new Registration(plugins, declaringClass);

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (method.getName().equals("<init>") && (method.toString().contains("FloatArray.<init>(int)") || method.toString().contains("DoubleArray.<init>(int)") || method.toString().contains(
                        "IntArray.<init>(int)") || method.toString().contains("HalfFloatArray.<init>(int)"))) {
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
                final VectorLoadElementNode loadElement = new VectorLoadElementNode(vectorKind.getElementKind(), receiver.get(), laneId);
                b.push(javaElementKind, b.append(loadElement));
                return true;
            }
        });

        r.register(new InvocationPlugin("set", Receiver.class, vectorKind.getJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (receiver.get() instanceof ParameterNode) {
                    final AddressNode address = new OffsetAddressNode(receiver.get(), null);
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
                final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(vectorKind.getElementKind(), receiver.get(), laneId, value);
                b.add(b.append(store));
                return true;
            }
        });

        r.register(new InvocationPlugin("add", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                MetalKind kind = MetalKind.fromResolvedJavaType(resolvedType);
                VectorAddNode addNode = new VectorAddNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(addNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("sub", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                MetalKind kind = MetalKind.fromResolvedJavaType(resolvedType);
                VectorSubNode subNode = new VectorSubNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(subNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("mult", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                MetalKind kind = MetalKind.fromResolvedJavaType(resolvedType);
                VectorMulNode multNode = new VectorMulNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(multNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("div", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                MetalKind kind = MetalKind.fromResolvedJavaType(resolvedType);
                VectorDivNode divNode = new VectorDivNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(divNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("getArray", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                MetalKind kind = MetalKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                ValueNode array = receiver.get();
                GetArrayNode getArrayNode = new GetArrayNode(kind, array, elementKind);
                b.push(JavaKind.Object, b.append(getArrayNode));
                return true;
            }
        });

    }

    private static void registerGeometricBIFS(final InvocationPlugins plugins, final MetalKind vectorKind) {
        final Class<?> declaringClass = vectorKind.getJavaClass();
        final Registration r = new Registration(plugins, declaringClass);
        r.register(new InvocationPlugin("dot", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                TornadoInternalError.unimplemented();
                return true;
            }
        });
    }

    static void registerParameterPlugins(Plugins plugins) {
        plugins.appendParameterPlugin((GraphBuilderTool tool, int index, StampPair stampPair) -> {
            if (stampPair.getTrustedStamp() instanceof ObjectStamp) {
                ObjectStamp objStamp = (ObjectStamp) stampPair.getTrustedStamp();
                ResolvedJavaType objStampType = objStamp.type();
                if (objStampType != null && objStampType.getAnnotation(Vector.class) != null) {
                    MetalKind kind = MetalKind.fromResolvedJavaType(objStampType);
                    return new ParameterNode(index, StampPair.createSingle(MetalStampFactory.getStampFor(kind)));
                }
            }
            return null;
        });
    }
}
