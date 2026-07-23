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
package uk.ac.manchester.tornado.drivers.cuda.graal.compiler.plugins;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
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
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAStampFactory;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.Half2IntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.GetArrayNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.LoadIndexedVectorNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorAddNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorDivNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorLoadElementNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorMulNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorStoreElementProxyNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorStoreGlobalMemory;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorSubNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.PanamaPrivateMemoryNode;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.TORNADO_ENABLE_BIFS;

public final class CUDAVectorPlugins {

    public static void registerPlugins(final Plugins ps, final InvocationPlugins plugins) {

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                CUDAKind vectorKind = CUDAKind.resolveToVectorKind(method.getDeclaringClass());

                if (vectorKind == CUDAKind.ILLEGAL) {
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
        registerVectorPlugins(ps, plugins, CUDAKind.FLOAT2, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, CUDAKind.FLOAT3, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, CUDAKind.FLOAT4, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, CUDAKind.FLOAT8, FloatArray.class, float.class);
        registerVectorPlugins(ps, plugins, CUDAKind.FLOAT16, FloatArray.class, float.class);

        // Adding half floats
        registerVectorPlugins(ps, plugins, CUDAKind.HALF2, HalfFloat.class, short.class);
        registerVectorPlugins(ps, plugins, CUDAKind.HALF3, HalfFloat.class, short.class);
        registerVectorPlugins(ps, plugins, CUDAKind.HALF4, HalfFloat.class, short.class);
        registerVectorPlugins(ps, plugins, CUDAKind.HALF8, HalfFloat.class, short.class);
        registerVectorPlugins(ps, plugins, CUDAKind.HALF16, HalfFloat.class, short.class);

        // Adding ints
        registerVectorPlugins(ps, plugins, CUDAKind.INT2, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, CUDAKind.INT3, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, CUDAKind.INT4, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, CUDAKind.INT8, IntArray.class, int.class);
        registerVectorPlugins(ps, plugins, CUDAKind.INT16, IntArray.class, int.class);

        // Adding shorts
        registerVectorPlugins(ps, plugins, CUDAKind.SHORT2, ShortArray.class, short.class);

        // Adding char
        registerVectorPlugins(ps, plugins, CUDAKind.CHAR3, ByteArray.class, byte.class);
        registerVectorPlugins(ps, plugins, CUDAKind.CHAR4, ByteArray.class, byte.class);

        // Adding double
        registerVectorPlugins(ps, plugins, CUDAKind.DOUBLE2, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, CUDAKind.DOUBLE3, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, CUDAKind.DOUBLE4, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, CUDAKind.DOUBLE8, DoubleArray.class, double.class);
        registerVectorPlugins(ps, plugins, CUDAKind.DOUBLE16, DoubleArray.class, double.class);

        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORFLOAT2, FloatArray.class, Float2.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORFLOAT3, FloatArray.class, Float3.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORFLOAT8, FloatArray.class, Float8.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORFLOAT16, FloatArray.class, Float16.class);

        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORINT2, IntArray.class, Int2.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORINT3, IntArray.class, Int3.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORINT4, IntArray.class, Int4.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORINT8, IntArray.class, Int8.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORINT16, IntArray.class, Int16.class);

        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORDOUBLE2, DoubleArray.class, Double2.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORDOUBLE3, DoubleArray.class, Double3.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORDOUBLE4, DoubleArray.class, Double4.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORDOUBLE8, DoubleArray.class, Double8.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORDOUBLE16, DoubleArray.class, Double16.class);

        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORHALF2, HalfFloatArray.class, Half2.class);

        registerHalf2PackedPlugins(plugins);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORHALF3, HalfFloatArray.class, Half3.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORHALF4, HalfFloatArray.class, Half4.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORHALF8, HalfFloatArray.class, Half8.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.VECTORHALF16, HalfFloatArray.class, Half16.class);

        registerVectorCollectionsPlugins(plugins, CUDAKind.MATRIX2DFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.MATRIX3DFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.MATRIX4X4FLOAT, FloatArray.class, Float4.class);

        registerVectorCollectionsPlugins(plugins, CUDAKind.IMAGEFLOAT3, FloatArray.class, Float3.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.IMAGEFLOAT4, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.IMAGEFLOAT8, FloatArray.class, Float8.class);

        registerVectorCollectionsPlugins(plugins, CUDAKind.VOLUMESHORT2, ShortArray.class, Short2.class);

        registerVectorCollectionsPlugins(plugins, CUDAKind.IMAGEBYTE3, ByteArray.class, Byte3.class);
        registerVectorCollectionsPlugins(plugins, CUDAKind.IMAGEBYTE4, ByteArray.class, Byte4.class);

        /*
         * Geometric BIFS for floating point vectors
         */
        if (TORNADO_ENABLE_BIFS) {
            registerGeometricBIFS(plugins, CUDAKind.FLOAT3);
            registerGeometricBIFS(plugins, CUDAKind.FLOAT4);
        }
    }

    /**
     * Packed half2 support: single 32-bit loads/stores on {@link HalfFloatArray} plus the
     * {@link Half2} fma/conversion helpers, mapped to cuda_fp16.h packed intrinsics.
     */
    private static void registerHalf2PackedPlugins(final InvocationPlugins plugins) {
        final Registration arrayRegistration = new Registration(plugins, HalfFloatArray.class);

        arrayRegistration.register(new InvocationPlugin("getHalf2", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index) {
                LoadIndexedVectorNode indexedLoad = new LoadIndexedVectorNode(CUDAKind.HALF2, receiver.get(true), index, JavaKind.Short);
                b.push(JavaKind.Object, b.append(indexedLoad));
                return true;
            }
        });

        arrayRegistration.register(new InvocationPlugin("setHalf2", Receiver.class, int.class, Half2.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index, ValueNode value) {
                StoreIndexedNode indexedStore = new StoreIndexedNode(receiver.get(true), index, null, null, JavaKind.Short, value);
                b.append(indexedStore);
                return true;
            }
        });

        final Registration half2Registration = new Registration(plugins, Half2.class);

        half2Registration.register(new InvocationPlugin("fma", Half2.class, Half2.class, Half2.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode x, ValueNode c) {
                Half2IntrinsicNode fmaNode = new Half2IntrinsicNode("__hfma2", CUDAStampFactory.getStampFor(CUDAKind.HALF2), a, x, c);
                b.push(JavaKind.Object, b.append(fmaNode));
                return true;
            }
        });

        half2Registration.register(new InvocationPlugin("lowFloat", Half2.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode vector) {
                Half2IntrinsicNode lowNode = new Half2IntrinsicNode("__low2float", StampFactory.forKind(JavaKind.Float), vector);
                b.push(JavaKind.Float, b.append(lowNode));
                return true;
            }
        });

        half2Registration.register(new InvocationPlugin("highFloat", Half2.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode vector) {
                Half2IntrinsicNode highNode = new Half2IntrinsicNode("__high2float", StampFactory.forKind(JavaKind.Float), vector);
                b.push(JavaKind.Float, b.append(highNode));
                return true;
            }
        });

        half2Registration.register(new InvocationPlugin("fromFloats", float.class, float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                Half2IntrinsicNode packNode = new Half2IntrinsicNode("__floats2half2_rn", CUDAStampFactory.getStampFor(CUDAKind.HALF2), x, y);
                b.push(JavaKind.Object, b.append(packNode));
                return true;
            }
        });
    }

    private static VectorValueNode resolveReceiver(ValueNode thisObject) {
        VectorValueNode vector = null;
        if (thisObject instanceof PiNode piNode) {
            thisObject = piNode.getOriginalNode();
        }
        if (thisObject instanceof VectorValueNode vectorValueNode) {
            vector = vectorValueNode;
        }
        guarantee(vector != null, "[Vector Plugins] unable to resolve vector");
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

    private static void registerVectorCollectionsPlugins(final InvocationPlugins plugins, final CUDAKind vectorKind, final Class<?> storageType, final Class<?> vectorClass) {

        final Class<?> declaringClass = vectorKind.getJavaClass();

        final Registration r = new Registration(plugins, declaringClass);
        r.register(new InvocationPlugin("loadFromArray", Receiver.class, storageType, int.class) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode index) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(vectorClass);
                CUDAKind kind = CUDAKind.fromResolvedJavaType(resolvedType);
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
                CUDAKind kind = CUDAKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                // No need to set stamp as it is inferred from the stamp of the incoming value
                StoreIndexedNode indexedStore = new StoreIndexedNode(array, index, null, null, elementKind, value);
                b.append(b.append(indexedStore));
                return true;
            }
        });

    }

    private static void registerVectorPlugins(final Plugins ps, final InvocationPlugins plugins, final CUDAKind vectorKind, final Class<?> storageType, final Class<?> elementType) {

        final Class<?> declaringClass = vectorKind.getJavaClass();
        final JavaKind javaElementKind = vectorKind.getElementKind().asJavaKind();

        final Registration r = new Registration(plugins, declaringClass);

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
                final VectorLoadElementNode loadElement = new VectorLoadElementNode(vectorKind.getElementKind(), receiver.get(true), laneId);
                b.push(javaElementKind, b.append(loadElement));
                return true;
            }
        });

        r.register(new InvocationPlugin("set", Receiver.class, vectorKind.getJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (receiver.get(true) instanceof ParameterNode) {
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
                final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(vectorKind.getElementKind(), receiver.get(true), laneId, value);
                b.add(b.append(store));
                return true;
            }
        });

        r.register(new InvocationPlugin("set", Receiver.class, int.class, storageType) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode laneId, ValueNode value) {
                final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(vectorKind.getElementKind(), receiver.get(true), laneId, value);
                b.add(b.append(store));
                return true;
            }
        });

        r.register(new InvocationPlugin("add", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                CUDAKind kind = CUDAKind.fromResolvedJavaType(resolvedType);
                VectorAddNode addNode = new VectorAddNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(addNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("sub", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                CUDAKind kind = CUDAKind.fromResolvedJavaType(resolvedType);
                VectorSubNode subNode = new VectorSubNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(subNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("mult", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                CUDAKind kind = CUDAKind.fromResolvedJavaType(resolvedType);
                VectorMulNode multNode = new VectorMulNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(multNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("div", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                CUDAKind kind = CUDAKind.fromResolvedJavaType(resolvedType);
                VectorDivNode divNode = new VectorDivNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(divNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("getArray", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                CUDAKind kind = CUDAKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                ValueNode array = receiver.get(true);
                GetArrayNode getArrayNode = new GetArrayNode(kind, array, elementKind);
                b.push(JavaKind.Object, b.append(getArrayNode));
                return true;
            }
        });

    }

    private static void registerGeometricBIFS(final InvocationPlugins plugins, final CUDAKind vectorKind) {
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
                    CUDAKind kind = CUDAKind.fromResolvedJavaType(objStampType);
                    return new ParameterNode(index, StampPair.createSingle(CUDAStampFactory.getStampFor(kind)));
                }
            }
            return null;
        });
    }
}
