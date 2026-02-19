/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;

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
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.GetArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.LoadIndexedVectorNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.SPIRVVectorValueNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.VectorAddNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.VectorDivNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.VectorLoadElementNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.VectorMultNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.VectorStoreElementProxyNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.VectorStoreGlobalMemory;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.VectorSubNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.PanamaPrivateMemoryNode;

public class SPIRVVectorPlugins {

    public static void registerPlugins(final Plugins plugins, final InvocationPlugins invocationPlugins) {

        plugins.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                SPIRVKind vectorKind = SPIRVKind.fromResolvedJavaTypeToVectorKind(method.getDeclaringClass());
                if (vectorKind == SPIRVKind.ILLEGAL) {
                    return false;
                }
                if (method.getName().equals("<init>")) {
                    final SPIRVVectorValueNode vectorValueNode = resolveReceiver(args[0]);
                    if (args.length > 1) {
                        int offset = (vectorValueNode == args[0]) ? 1 : 0;
                        for (int i = offset; i < args.length; i++) {
                            vectorValueNode.setElement(i - offset, args[i]);
                        }
                    } else {
                        if (vectorKind.getVectorLength() < 8) {
                            vectorValueNode.initialiseToDefaultValues(vectorValueNode.graph());
                        }
                    }
                    return true;
                }
                return false;
            }
        });

        // Byte
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR3_INT_8, ByteArray.class, byte.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR4_INT_8, ByteArray.class, byte.class);

        // Floats
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR2_FLOAT_32, FloatArray.class, float.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR3_FLOAT_32, FloatArray.class, float.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR4_FLOAT_32, FloatArray.class, float.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR8_FLOAT_32, FloatArray.class, float.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR16_FLOAT_32, FloatArray.class, float.class);

        // Adding ints
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR2_INT_32, IntArray.class, int.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR3_INT_32, IntArray.class, int.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR4_INT_32, IntArray.class, int.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR8_INT_32, IntArray.class, int.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR16_INT_32, IntArray.class, int.class);

        // Short
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR2_INT_16, ShortArray.class, short.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR3_INT_16, ShortArray.class, short.class);

        // Doubles
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR2_FLOAT_64, DoubleArray.class, double.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR3_FLOAT_64, DoubleArray.class, double.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR4_FLOAT_64, DoubleArray.class, double.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR8_FLOAT_64, DoubleArray.class, double.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR16_FLOAT_64, DoubleArray.class, double.class);

        // Half Floats
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR2_HALF_FLOAT, HalfFloat.class, float.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR3_HALF_FLOAT, HalfFloat.class, float.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR4_HALF_FLOAT, HalfFloat.class, float.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR8_HALF_FLOAT, HalfFloat.class, float.class);
        registerVectorPlugins(plugins, invocationPlugins, SPIRVKind.OP_TYPE_VECTOR16_HALF_FLOAT, HalfFloat.class, float.class);

        // VectorFloats
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORFLOAT2_FLOAT_32, FloatArray.class, Float2.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORFLOAT3_FLOAT_32, FloatArray.class, Float3.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORFLOAT4_FLOAT_32, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORFLOAT8_FLOAT_32, FloatArray.class, Float8.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORFLOAT16_FLOAT_32, FloatArray.class, Float16.class);

        // VectorInts
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORINT2_INT_32, IntArray.class, Int2.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORINT3_INT_32, IntArray.class, Int3.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORINT4_INT_32, IntArray.class, Int4.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORINT8_INT_32, IntArray.class, Int8.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORINT16_INT_32, IntArray.class, Int16.class);

        // VectorDoubles
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORDOUBLE2_FLOAT_64, DoubleArray.class, Double2.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORDOUBLE3_FLOAT_64, DoubleArray.class, Double3.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORDOUBLE4_FLOAT_64, DoubleArray.class, Double4.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORDOUBLE8_FLOAT_64, DoubleArray.class, Double8.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORDOUBLE16_FLOAT_64, DoubleArray.class, Double16.class);

        // VectorHalfFloats
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORHALF2_FLOAT_16, HalfFloatArray.class, Half2.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORHALF3_FLOAT_16, HalfFloatArray.class, Half3.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORHALF4_FLOAT_16, HalfFloatArray.class, Half4.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORHALF8_FLOAT_16, HalfFloatArray.class, Half8.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTORHALF16_FLOAT_16, HalfFloatArray.class, Half16.class);

        // Matrices
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_MATRIX2DFLOAT4_FLOAT_32, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_MATRIX3DFLOAT4_FLOAT_32, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_MATRIX4X4FLOAT_FLOAT_32, FloatArray.class, Float4.class);

        // ImageFloats
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_IMAGEFLOAT3_FLOAT_32, FloatArray.class, Float3.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_IMAGEFLOAT4_FLOAT_32, FloatArray.class, Float4.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_IMAGEFLOAT8_FLOAT_32, FloatArray.class, Float8.class);

        // VolumeShort
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_VECTOR2_VOLUME_INT_16, ShortArray.class, Short2.class);

        // ImageBytes
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_IMAGEBYTE3_INT_8, ByteArray.class, Byte3.class);
        registerVectorCollectionsPlugins(invocationPlugins, SPIRVKind.OP_TYPE_IMAGEBYTE4_INT_8, ByteArray.class, Byte4.class);

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

    private static void registerVectorCollectionsPlugins(final InvocationPlugins plugins, final SPIRVKind vectorKind, final Class<?> storageType, final Class<?> vectorClass) {

        final Class<?> declaringClass = vectorKind.getJavaClass();

        final Registration r = new Registration(plugins, declaringClass);
        r.register(new InvocationPlugin("loadFromArray", Receiver.class, storageType, int.class) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode index) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(vectorClass);
                SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(resolvedType);
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
                SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                // No need to set stamp as it is inferred from the stamp of the incoming value
                StoreIndexedNode indexedStore = new StoreIndexedNode(array, index, null, null, elementKind, value);
                b.append(b.append(indexedStore));
                return true;
            }
        });

    }

    private static void registerVectorPlugins(final Plugins ps, final InvocationPlugins plugins, final SPIRVKind spirvVectorKind, final Class<?> storageType, final Class<?> elementType) {

        final Class<?> declaringClass = spirvVectorKind.getJavaClass();
        final JavaKind javaElementKind = spirvVectorKind.getElementKind().asJavaKind();

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
                receiver.get(true);
                final VectorLoadElementNode loadElement = new VectorLoadElementNode(spirvVectorKind.getElementKind(), receiver.get(true), laneId);
                b.push(javaElementKind, b.append(loadElement));
                return true;
            }
        });

        r.register(new InvocationPlugin("set", Receiver.class, int.class, storageType) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode laneId, ValueNode value) {
                receiver.get(true);
                final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(spirvVectorKind.getElementKind(), receiver.get(true), laneId, value);
                b.add(b.append(store));
                return true;
            }
        });

        r.register(new InvocationPlugin("set", Receiver.class, spirvVectorKind.getJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (receiver.get(true) instanceof ParameterNode) {
                    final AddressNode address = new OffsetAddressNode(receiver.get(true), null);
                    final VectorStoreGlobalMemory store = new VectorStoreGlobalMemory(spirvVectorKind, address, value);
                    b.add(b.append(store));
                    return true;
                }
                return false;
            }
        });

        r.register(new InvocationPlugin("set", Receiver.class, int.class, elementType) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode laneId, ValueNode value) {
                final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(spirvVectorKind.getElementKind(), receiver.get(true), laneId, value);
                b.add(b.append(store));
                return true;
            }
        });

        r.register(new InvocationPlugin("add", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(resolvedType);
                VectorAddNode addNode = new VectorAddNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(addNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("sub", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(resolvedType);
                VectorSubNode addNode = new VectorSubNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(addNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("mul", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(resolvedType);
                VectorMultNode addNode = new VectorMultNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(addNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("div", declaringClass, declaringClass) {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(resolvedType);
                VectorDivNode addNode = new VectorDivNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(addNode));
                return true;
            }
        });

        r.register(new InvocationPlugin("getArray", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                ValueNode array = receiver.get(true);
                GetArrayNode getArrayNode = new GetArrayNode(kind, array, elementKind);
                b.push(JavaKind.Object, b.append(getArrayNode));
                return true;
            }
        });

    }

    private static SPIRVVectorValueNode resolveReceiver(ValueNode thisObject) {
        SPIRVVectorValueNode vector = null;
        if (thisObject instanceof PiNode) {
            thisObject = ((PiNode) thisObject).getOriginalNode();
        }
        if (thisObject instanceof SPIRVVectorValueNode) {
            vector = (SPIRVVectorValueNode) thisObject;
        }
        guarantee(vector != null, "[Vector Plugins] unable to resolve vector");
        return vector;
    }

    /**
     * If the parameter passed is a vector, we attach vector information (SPIRVKind)
     * to the parameter node.
     *
     * @param plugins
     *     {@link Plugins}
     */
    public static void registerParameterPlugins(Plugins plugins) {
        plugins.appendParameterPlugin((GraphBuilderTool tool, int index, StampPair stampPair) -> {
            if (stampPair.getTrustedStamp() instanceof ObjectStamp objectStamp) {
                if (objectStamp.type().getAnnotation(Vector.class) != null) {
                    SPIRVKind kind = SPIRVKind.fromResolvedJavaTypeToVectorKind(objectStamp.type());
                    return new ParameterNode(index, StampPair.createSingle(SPIRVStampFactory.getStampFor(kind)));
                }
            }
            return null;
        });
    }
}
