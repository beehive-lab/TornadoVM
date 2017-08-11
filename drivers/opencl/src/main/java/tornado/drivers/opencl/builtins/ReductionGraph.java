//package tornado.drivers.opencl.builtins;
//
//import java.lang.reflect.Array;
//import jdk.vm.ci.meta.JavaConstant;
//import jdk.vm.ci.meta.ResolvedJavaMethod;
//import org.graalvm.compiler.core.common.type.StampFactory;
//import org.graalvm.compiler.debug.Debug;
//import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
//import org.graalvm.compiler.nodes.*;
//import org.graalvm.compiler.nodes.calc.AddNode;
//import org.graalvm.compiler.nodes.calc.BinaryNode;
//import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
//import org.graalvm.compiler.nodes.memory.FloatingReadNode;
//import tornado.api.ReductionOp;
//import tornado.api.meta.TaskMetaData;
//import tornado.common.RuntimeUtilities;
//import tornado.common.SchedulableTask;
//import tornado.common.enums.Access;
//import tornado.drivers.opencl.OCLDevice;
//import tornado.drivers.opencl.OCLTargetDescription;
//import tornado.drivers.opencl.graal.OCLInstalledCode;
//import tornado.drivers.opencl.graal.OCLProviders;
//import tornado.drivers.opencl.graal.OCLStampFactory;
//import tornado.drivers.opencl.graal.compiler.OCLCompiler;
//import tornado.drivers.opencl.graal.lir.OCLKind;
//import tornado.drivers.opencl.graal.nodes.GroupIdNode;
//import tornado.drivers.opencl.graal.nodes.OCLBarrierNode.OCLMemFenceNode;
//import tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
//import tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation;
//import tornado.drivers.opencl.graal.nodes.OCLMemoryRegion;
//import tornado.drivers.opencl.graal.nodes.OCLMemoryRegion.Region;
//import tornado.drivers.opencl.graal.nodes.vector.VectorAddNode;
//import tornado.drivers.opencl.graal.nodes.vector.VectorElementSelectNode;
//import tornado.drivers.opencl.graal.nodes.vector.VectorSelectionNode;
//import tornado.drivers.opencl.graal.nodes.vector.VectorSelectionNode.VectorSelection;
//import tornado.drivers.opencl.runtime.OCLMemoryRegions;
//import tornado.drivers.opencl.runtime.OCLTornadoDevice;
//import tornado.meta.domain.DomainTree;
//import tornado.meta.domain.IntDomain;
//import tornado.runtime.TornadoRuntime;
//
//import static org.graalvm.compiler.debug.Debug.INFO_LEVEL;
//import static tornado.common.exceptions.TornadoInternalError.*;
//
//public class ReductionGraph<T> implements SchedulableTask {
//
//    protected final ReductionOp op;
//    protected final T base;
//    protected final TaskMetaData meta;
//    protected OCLTornadoDevice mapping;
//    protected final Object[] args;
//    protected final Access[] access;
//    protected T partialResult;
//
//    public ReductionGraph(T base, ReductionOp op) {
//        this.base = base;
//        this.op = op;
//        this.args = new Object[]{base, null};
//        this.access = new Access[]{Access.READ, Access.WRITE};
//        meta = new TaskMetaData();
//
//        Class<?> type = base.getClass();
//        assert type.isArray() && type.getComponentType().isPrimitive();
//
//    }
//
//    public T getResult() {
//        return partialResult;
//    }
//
//    private int sizeOf(Object obj) {
//        Class<?> type = obj.getClass().getComponentType();
//        int elementLength = -1;
//        if (type == int.class || type == float.class) {
//            elementLength = 4;
//        } else if (type == long.class || type == double.class) {
//            elementLength = 8;
//        } else {
//            unimplemented();
//        }
//        return elementLength;
//    }
//
//    private void calcStragegy() {
//
//        final int sizeOfType = sizeOf(base);
//
//    }
//
//    public OCLInstalledCode buildReduceGraph() {
//        final int numElements = Array.getLength(base);
//        final Class<?> type = base.getClass();
//        final Class<?> elementType = type.getComponentType();
//        OCLKind elementKind = OCLKind.fromJavaClass(elementType);
//        final OCLDevice device = mapping.getDevice();
//        final long localMemorySize = device.getLocalMemorySize();
//        final long maxWorkGroupSize = Math.min(localMemorySize / elementKind.getByteCount(), device.getMaxWorkGroupSize());
//
//        System.out.printf("reduction:\n");
//        System.out.printf("\tlocal mem: %s\n", RuntimeUtilities.humanReadableByteCount(localMemorySize, true));
//        System.out.printf("\tmax work group size: %d\n", maxWorkGroupSize);
//        System.out.printf("\tmax compute units: %d\n", device.getMaxComputeUnits());
//
//        int targetWorkGroupSize = (numElements / 2 < 128) ? 128 : device.getMaxComputeUnits() * 128;
//        DomainTree domain = new DomainTree(1);
//        domain.set(0, new IntDomain(0, 1, targetWorkGroupSize));
//        meta.setDomain(domain);
//
//        int numWorkGroups = Math.min(1, targetWorkGroupSize / 128);
//        // declare array for partial results
//        partialResult = (T) Array.newInstance(elementType, numWorkGroups);
//        args[1] = partialResult;
//
//        OCLMemoryRegions memoryRegions = new OCLMemoryRegions();
//        meta.addProvider(OCLMemoryRegions.class, memoryRegions);
//        memoryRegions.allocLocal((int) targetWorkGroupSize * elementKind.getByteCount());
//
//        ResolvedJavaMethod method = null;
//        try {
//            method = TornadoRuntime.runtime.resolveMethod(getClass().getDeclaredMethod("reduce"));
//        } catch (NoSuchMethodException | SecurityException ex) {
//            shouldNotReachHere();
//        }
//
//        /*
//         * generate parallel reduction loop
//         */
//        final StructuredGraph graph = new StructuredGraph("reduction", method, AllowAssumptions.NO);
//
//        ParameterNode param0 = graph.addOrUnique(new ParameterNode(0, StampFactory.forKind(Kind.fromJavaClass(type))));
//        ParameterNode param1 = graph.addOrUnique(new ParameterNode(1, StampFactory.forKind(Kind.fromJavaClass(type))));
//
//        ConstantNode arrayLength = graph.addOrUnique(ConstantNode.forInt(numElements));
//
//        ConstantNode zero = graph.addOrUnique(ConstantNode.forPrimitive(JavaConstant.INT_0, graph));
//        ConstantNode one = graph.addOrUnique(ConstantNode.forPrimitive(JavaConstant.INT_1, graph));
//
//        GlobalThreadSize gsize = graph.addOrUnique(new GlobalThreadSize(zero));
//
//        GlobalThreadId gid = graph.addOrUnique(new GlobalThreadId(zero));
//
//        LoopBeginNode loopBeginNode = new LoopBeginNode();
//        LoopEndNode loopEndNode = new LoopEndNode(loopBeginNode);
//        LoopExitNode loopExitNode = new LoopExitNode(loopBeginNode);
//
//        FixedWithNextNode current = graph.start();
//        EndNode endNode = graph.add(new EndNode());
//        current.setNext(endNode);
//
//        loopBeginNode.addForwardEnd(endNode);
//        graph.add(loopBeginNode);
//        graph.add(loopEndNode);
//        graph.add(loopExitNode);
//
//        BeginNode beginInner = new BeginNode();
////        BeginNode beginExit = new BeginNode();
//
//        beginInner.setNext(loopEndNode);
////        beginExit.setNext(loopExitNode);
//
//        graph.add(beginInner);
////        graph.add(beginExit);
//
//        ValuePhiNode loopBoundsPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(Kind.Int), loopBeginNode));
//        AddNode addNode = graph.addOrUnique(new AddNode(loopBoundsPhi, gsize));
//        loopBoundsPhi.initializeValueAt(0, gid);
//        loopBoundsPhi.initializeValueAt(1, addNode);
//
//        IntegerLessThanNode condition1 = graph.addOrUnique(new IntegerLessThanNode(loopBoundsPhi, arrayLength));
//
//        ValuePhiNode reductionPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(elementKind), loopBeginNode));
//
//        IndexedLocationNode indexedLocation = graph.addWithoutUnique(new IndexedLocationNode(NamedLocationIdentity.getArrayLocation(elementKind), 24, loopBoundsPhi, elementKind.getByteCount()));
//        FloatingReadNode element = graph.addWithoutUnique(new FloatingReadNode(param0, indexedLocation, null, StampFactory.forKind(elementKind)));
//
//        JavaConstant initialValue = JavaConstant.NULL_POINTER;
//        BinaryNode opNode = null;
//        switch (op) {
//
//            case MIN:
//                if (elementKind == Kind.Int) {
//                    opNode = graph.addOrUnique((BinaryNode) OCLIntBinaryIntrinsicNode.create(reductionPhi, element, Operation.MAX, elementKind));
//                    initialValue = JavaConstant.forInt(Integer.MAX_VALUE);
//                }
//                break;
//            case MAX:
//                if (elementKind == Kind.Int) {
//                    opNode = graph.addOrUnique((BinaryNode) OCLIntBinaryIntrinsicNode.create(reductionPhi, element, Operation.MIN, elementKind));
//                    initialValue = JavaConstant.forInt(Integer.MIN_VALUE);
//                }
//                break;
//            case ADD:
//                if (elementKind == Kind.Int) {
//                    opNode = graph.addOrUnique(new AddNode(reductionPhi, element));
//                    initialValue = JavaConstant.INT_0;
//                }
//            default:
//
//                break;
//
//        }
//        guarantee(opNode != null, "op not implemented: %s", op);
//        guarantee(initialValue != JavaConstant.NULL_POINTER, "type not implemented: %s", elementKind.getJavaName());
//
//        ConstantNode initialValueNode = ConstantNode.forPrimitive(initialValue, graph);
//        reductionPhi.initializeValueAt(0, initialValueNode);
//        reductionPhi.initializeValueAt(1, opNode);
//
//        // if true -> inner loop, else -> loop exit
//        IfNode ifNode = new IfNode(condition1, beginInner, loopExitNode, .99);
//        graph.add(ifNode);
//
//        loopBeginNode.setNext(ifNode);
//
//        /*
//         * generate store of reduction to local memory
//         */
//        OCLMemoryRegion localRegion = graph.addOrUnique(new OCLMemoryRegion(Region.LOCAL));
//        LocalThreadId localIdNode = graph.addOrUnique(new LocalThreadId(zero));
//        IndexedLocationNode localIndexedNode = graph.addWithoutUnique(new IndexedLocationNode(NamedLocationIdentity.getArrayLocation(elementKind), 0, localIdNode, elementKind.getByteCount()));
//
//        WriteNode writeNode = graph.add(new WriteNode(localRegion, reductionPhi, localIndexedNode, BarrierType.NONE));
//        loopExitNode.setNext(writeNode);
//
//        OCLMemFenceNode localMemFence = graph.addWithoutUnique(new OCLMemFenceNode(OCLBarrier.OCLMemFenceFlags.LOCAL));
//        writeNode.setNext(localMemFence);
//
//        OCLKind vKind = mapping.getBackend().getTarget().getOCLKind(elementKind, 4);
//        final int gs = 128 / vKind.getByteCount();
//        // 8 threads x
//        System.out.printf("\tvector: %s\n", vKind.toString());
//        System.out.printf("\tthread: %d\n", 128 / vKind.getByteCount());
//
//        insertL0Phase(graph, localMemFence, elementKind, op, initialValueNode, gs, localRegion);
//
////        FixedWithNextNode endL1Node = insertL1Phase(graph, localMemFence, elementKind, opNode, initialValueNode, gs, localRegion);
//        Debug.dump(INFO_LEVEL, graph, "build reduce graph");
//
//        byte[] code = OCLCompiler.compileGraphForDevice(graph, meta, "reduce", (OCLProviders) mapping.getBackend().getProviders(), mapping.getBackend());
//        for (byte b : code) {
//            System.out.printf("%c", b);
//        }
//
////        OCLInstalledCode ic = mapping.getBackend().getCodeCache()..installOCLProgram("reduce", code);
//        return null;
//    }
//
//    private BinaryNode insertReduceOp(StructuredGraph graph, ReductionOp op, OCLKind oclKind, ValueNode a, ValueNode b) {
//        BinaryNode opNode = null;
//        switch (op) {
//
//            case MIN:
//            //opNode = graph.addOrUnique((BinaryNode) OCLIntBinaryIntrinsicNode.create(a, b, Operation.MAX, elementKind));
//            //break;
//            case MAX:
//                //opNode = graph.addOrUnique((BinaryNode) OCLIntBinaryIntrinsicNode.create(a, b, Operation.MIN, elementKind));
//                unimplemented();
//                break;
//            case ADD:
//                if (oclKind.isVector()) {
//                    opNode = graph.addWithoutUnique(new VectorAddNode(oclKind, a, b));
//                } else {
//                    opNode = graph.addWithoutUnique(new AddNode(a, b));
//                }
//                break;
//            default:
//                unimplemented();
//                break;
//
//        }
//
//        return opNode;
//    }
//
//    private void insertL0Phase(StructuredGraph graph, OCLMemFenceNode localMemFenceNode, Kind elementKind, ReductionOp op, ConstantNode initialValue, int targetSize, OCLMemoryRegion localRegion) {
//        ConstantNode groupSize = graph.addOrUnique(ConstantNode.forInt(targetSize));
//        ConstantNode zero = graph.addOrUnique(ConstantNode.forInt(0));
//
////        LocalThreadId localIdNode = graph.addOrUnique(new LocalThreadId(zero));
////        LocalThreadSize localSizeNode = graph.addOrUnique(new LocalThreadSize(zero));
//
//        ConstantNode vectorLength = graph.addOrUnique(ConstantNode.forInt(4));
//        OCLKind vKind = mapping.getBackend().getTarget().getOCLKind(elementKind, 4);
//
//        IntegerLessThanNode condition2 = graph.addOrUnique(new IntegerLessThanNode(localIdNode, groupSize));
//        BeginNode if2TrueBegin = graph.add(new BeginNode());
//        EndNode trueEnd = graph.add(new EndNode());
//        if2TrueBegin.setNext(trueEnd);
//
//        ReturnNode returnNode = graph.addWithoutUnique(new ReturnNode(null));
//        IfNode if2Stmt = graph.add(new IfNode(condition2, if2TrueBegin, returnNode, .5));
//        localMemFenceNode.setNext(if2Stmt);
//
//
//        /*
//         * create the local memory reduction
//         */
//        // for(i=ltid;i<lsize;i+=groups * vl)
//        LoopBeginNode l2Begin = graph.add(new LoopBeginNode());
//
//        LoopEndNode l2End = graph.add(new LoopEndNode(l2Begin));
//        LoopExitNode l2Exit = graph.add(new LoopExitNode(l2Begin));
//
//        l2Begin.addForwardEnd(trueEnd);
//
//        ValuePhiNode l2Iv = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(Kind.Int), l2Begin));
//
//        ConstantNode l2IvStride = graph.addOrUnique(ConstantNode.forInt(vKind.getVectorLength() * targetSize));
//        MulNode l2Vstart = graph.addOrUnique(new MulNode(localIdNode, vectorLength));
//        AddNode l2IvInit = graph.addOrUnique(new AddNode(l2Vstart, l2IvStride));
//        l2Iv.initializeValueAt(0, l2IvInit);
//
//        AddNode l2IvUpdate = graph.addWithoutUnique(new AddNode(l2Iv, l2IvStride));
//        l2Iv.initializeValueAt(1, l2IvUpdate);
//
//        IntegerLessThanNode l2Condition = graph.addOrUnique(new IntegerLessThanNode(l2Iv, localSizeNode));
//        BeginNode l2Inner = graph.add(new BeginNode());
//        l2Inner.setNext(l2End);
//        IfNode l2Header = graph.add(new IfNode(l2Condition, l2Inner, l2Exit, .5));
//        l2Begin.setNext(l2Header);
//        l2Exit.setNext(graph.add(new ReturnNode(null)));
//
////        ResolvedJavaType resolvedVectorType = mapping.getBackend().getProviders().getMetaAccess().lookupJavaType(vKind.getJavaClass());
//        IndexedLocationNode vectorIndex1 = graph.addWithoutUnique(new IndexedLocationNode(NamedLocationIdentity.getArrayLocation(elementKind), 0, l2Vstart, elementKind.getByteCount()));
//        FloatingReadNode readVector1 = graph.addWithoutUnique(new FloatingReadNode(localRegion, vectorIndex1, localMemFenceNode, OCLStampFactory.getStampFor(vKind)));
//
////VectorReadNode readVector1 = graph.addWithoutUnique(new VectorReadNode(vKind, localRegion, vectorIndex1, BarrierType.PRECISE));
////       readVector1.setForceFixed(true);
////        graph.addAfterFixed(l2Begin, readVector1);
//        //VectorValueNode vValue1 = graph.addWithoutUnique(new VectorValueNode(resolvedVectorType, vKind, readVector1));
//        ValuePhiNode vectorPhi = graph.addWithoutUnique(new ValuePhiNode(readVector1.stamp(), l2Begin));
//        vectorPhi.initializeValueAt(0, readVector1);
//
//        IndexedLocationNode vectorIndex2 = graph.addWithoutUnique(new IndexedLocationNode(NamedLocationIdentity.getArrayLocation(elementKind), 0, l2Iv, elementKind.getByteCount()));
//        FloatingReadNode readVector2 = graph.addWithoutUnique(new FloatingReadNode(localRegion, vectorIndex2, null, readVector1.stamp()));
//        //VectorValueNode vValue2 = graph.addWithoutUnique(new VectorValueNode(resolvedVectorType, vKind, readVector2));
//
//        // VectorAddNode vectorAdd = graph.addWithoutUnique(new VectorAddNode(vKind, vectorPhi, readVector2));
//        BinaryNode reduceOp = insertReduceOp(graph, op, vKind, vectorPhi, readVector2);
//
//        vectorPhi.initializeValueAt(1, reduceOp);
//
//        ValueNode finalLocalValue = genReduceVector(graph, vKind, op, vectorPhi);
//
//        //VectorWriteNode vectorWrite = graph.addWithoutUnique(new VectorWriteNode(vKind,localRegion,vectorPhi,vectorIndex1,BarrierType.NONE));
//        IndexedLocationNode localIndexedNode = graph.addWithoutUnique(new IndexedLocationNode(NamedLocationIdentity.getArrayLocation(elementKind), 0, localIdNode, elementKind.getByteCount()));
//
//        WriteNode vectorWrite = graph.addWithoutUnique(new WriteNode(localRegion, finalLocalValue, localIndexedNode, BarrierType.PRECISE));
//
//        graph.addAfterFixed(l2Exit, vectorWrite);
//
//        OCLMemFenceNode endL0Fence = graph.addWithoutUnique(new OCLMemFenceNode(OCLBarrier.OCLMemFenceFlags.LOCAL));
////         graph.addAfterFixed(endL0Fence,vectorWrite);
//        vectorWrite.setNext(endL0Fence);
//
//        ReturnNode endNode = graph.addWithoutUnique(new ReturnNode(null));
//        endL0Fence.setNext(endNode);
////graph.addAfterFixed(endL0Fence, vectorWrite);
//
////        endL0Fence.setNext(l2Exit);
////graph.addAfterFixed(l2Exit, endL0Fence);
//        insertL2Phase(graph, elementKind, vKind, endL0Fence, localRegion, targetSize, 4);
//
//    }
//
//    private ValueNode genReduceVector(StructuredGraph graph, OCLKind vKind, ReductionOp op, ValueNode inVector) {
//        OCLTargetDescription target = mapping.getBackend().getTarget();
//        /*
//         * reduce vector into single element and store
//         */
//        for (int i = vKind.getVectorLength(); i > 1; i = i >>> 1) {
//            OCLKind nextKind = target.getVectorByLength(vKind.getElementKind(), i >>> 1);
//            VectorSelectionNode hiNode = graph.addWithoutUnique(new VectorSelectionNode(VectorSelection.Hi));
//            VectorElementSelectNode selectHiNode = graph.addWithoutUnique(new VectorElementSelectNode(nextKind, inVector, hiNode));
//
//            VectorSelectionNode loNode = graph.addWithoutUnique(new VectorSelectionNode(VectorSelection.LO));
//            VectorElementSelectNode selectLoNode = graph.addWithoutUnique(new VectorElementSelectNode(nextKind, inVector, loNode));
//
//            VectorAddNode addNode = graph.addOrUnique(new VectorAddNode(nextKind, selectHiNode, selectLoNode));
//            inVector = addNode;
//        }
//
//        return inVector;
//    }
//
//    private void insertL2Phase(StructuredGraph graph, Kind elementKind, OCLKind kind, OCLMemFenceNode lastBarrier, OCLMemoryRegion localRegion, int targetSize, int stride) {
//
//        /*
//         * final phase which is executed by thread zero only
//         */
//        // generate if stmt
//        ConstantNode zero = graph.addOrUnique(ConstantNode.forInt(0));
//        ConstantNode one = graph.addOrUnique(ConstantNode.forInt(1));
//        LocalThreadId localIdNode = graph.addOrUnique(new LocalThreadId(zero));
//        IntegerEqualsNode condition = graph.addOrUnique(new IntegerEqualsNode(localIdNode, zero));
//        BeginNode ifTrueBegin = graph.add(new BeginNode());
//        //EndNode trueEnd = graph.add(new EndNode());
//        //ifTrueBegin.setNext(trueEnd);
//
//        ReturnNode returnNode = graph.addWithoutUnique(new ReturnNode(null));
//        IfNode ifStmt = graph.add(new IfNode(condition, ifTrueBegin, returnNode, .5));
//        lastBarrier.setNext(ifStmt);
//        FixedWithNextNode insert = ifTrueBegin;
//
//        // insert X vector loads
//        // insert after true begin
//        IndexedLocationNode vectorIndex1 = graph.addWithoutUnique(new IndexedLocationNode(NamedLocationIdentity.getArrayLocation(elementKind), 0, zero, elementKind.getByteCount()));
//        FloatingReadNode readVector1 = graph.addWithoutUnique(new FloatingReadNode(localRegion, vectorIndex1, lastBarrier, OCLStampFactory.getStampFor(kind)));
//
//        ValueNode reduceValue = readVector1;
//        for (int i = stride; i < targetSize; i += stride) {
//            ConstantNode index = graph.addOrUnique(ConstantNode.forInt(i));
//            IndexedLocationNode vectorIndex2 = graph.addWithoutUnique(new IndexedLocationNode(NamedLocationIdentity.getArrayLocation(elementKind), 0, index, 1));
//            FloatingReadNode readVector2 = graph.addWithoutUnique(new FloatingReadNode(localRegion, vectorIndex2, lastBarrier, OCLStampFactory.getStampFor(kind)));
//
//            BinaryNode reduceOp = insertReduceOp(graph, op, kind, reduceValue, readVector2);
//            reduceValue = reduceOp;
//
//        }
//
//        // insert vector reduce
//        ValueNode finalValue = genReduceVector(graph, kind, op, reduceValue);
//
//        GroupIdNode groupId = graph.addOrUnique(new GroupIdNode(zero));
//        IndexedLocationNode returnIndex = graph.addWithoutUnique(new IndexedLocationNode(NamedLocationIdentity.getArrayLocation(elementKind), 24, groupId, elementKind.getByteCount()));
//        WriteNode finalWrite = graph.addWithoutUnique(new WriteNode(graph.getParameter(1), finalValue, returnIndex, BarrierType.PRECISE));
//        ifTrueBegin.setNext(finalWrite);
//
//        ReturnNode returnEndNode = graph.addWithoutUnique(new ReturnNode(null));
//        finalWrite.setNext(returnEndNode);
////graph.replaceFixed(trueEnd, returnEndNode);
//
//    }
//
//    private void reduce() {
//
//    }
//
//    @Override
//    public Object[] getArguments() {
//        return args;
//    }
//
//    @Override
//    public Access[] getArgumentsAccess() {
//        return access;
//    }
//
//    @Override
//    public Meta meta() {
//        return meta;
//    }
//
//    @Override
//    public SchedulableTask mapTo(DeviceMapping mapping) {
//        this.mapping = (OCLDeviceMapping) mapping;
//        return this;
//    }
//
//    @Override
//    public DeviceMapping getDeviceMapping() {
//        return mapping;
//    }
//
//    @Override
//    public String getName() {
//        return String.format("reduction<%s[%d],%s>", base.getClass().getName(), Array.getLength(base), op);
//    }
//
//}
