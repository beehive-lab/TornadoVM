package uk.ac.manchester.tornado.drivers.opencl.graal.snippets;

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.collections.types.Float2;
import uk.ac.manchester.tornado.collections.types.Float4;
import uk.ac.manchester.tornado.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.drivers.opencl.builtins.OpenCLIntrinsics;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode.ATOMIC_OPERATION;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceAddNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceMulNode;
import uk.ac.manchester.tornado.graal.nodes.OCLReduceSubNode;
import uk.ac.manchester.tornado.graal.nodes.StoreAtomicIndexedNode;

public class ReduceSnippets implements Snippets {

    @Fold
    static LocationIdentity getArrayLocation(JavaKind kind) {
        return NamedLocationIdentity.getArrayLocation(kind);
    }

    /**
     * Dummy snippet to understand the process of node replacements using
     * snippet during lowering.
     * 
     */
    @Snippet
    public static void testReduceLoop(int n, int[] data, int value) {
        int acc = 0;
        for (int i = 0; i < n; i++) {
            acc += value;
        }
        data[0] = acc;
    }

    /**
     * 1D full snippet for OpenCL reductions.
     * 
     * @param inputArray
     * @param outputArray
     * @param localMemory
     * @param gidx
     * @param numGroups
     */
    @Snippet
    public static void reduceIntAdd(int[] inputArray, int[] outputArray, int gidx, int globalSize) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);

        int sizeLocalMemory = 1024;

        // Allocate a chunk of data in local memory
        int[] localMemory = new int[sizeLocalMemory];
        OpenCLIntrinsics.createLocalMemory(localMemory, sizeLocalMemory);

        // Copy input data to local memory
        localMemory[localIdx] = inputArray[gidx];

        int start = localGroupSize / 2;
        // Reduction in local memory
        for (int stride = start; stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (stride > localIdx) {
                localMemory[localIdx] += localMemory[localIdx + stride];
            }
        }

        if (localIdx == 0) {
            int groupID = OpenCLIntrinsics.get_group_id(0);
            outputArray[groupID] = localMemory[0];
        }

        OpenCLIntrinsics.globalBarrier();
        if (gidx == 0) {
            int numGroups = globalSize / localGroupSize;
            for (int i = 1; i < numGroups; i++) {
                outputArray[0] += outputArray[i];
            }
        }
    }

    @Snippet
    public static void reduceIntAdd2(int[] outputArray, int gidx, int globalSize, int value) {

        int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localGroupSize = OpenCLIntrinsics.get_local_size(0);

        int sizeLocalMemory = 16;

        // Allocate a chunk of data in local memory
        int[] localMemory = new int[sizeLocalMemory];
        OpenCLIntrinsics.createLocalMemory(localMemory, sizeLocalMemory);

        // Copy input data to local memory
        localMemory[localIdx] = value;

        int start = localGroupSize / 2;
        // Reduction in local memory
        for (int stride = start; stride > 0; stride /= 2) {
            OpenCLIntrinsics.localBarrier();
            if (localIdx < stride) {
                localMemory[localIdx] += localMemory[localIdx + stride];
            }
        }

        if (localIdx == 0) {
            int groupID = OpenCLIntrinsics.get_group_id(0);
            outputArray[groupID] = localMemory[0];
        }

        // OpenCLIntrinsics.globalBarrier();
        // if (gidx == 0) {
        // int numGroups = globalSize / localGroupSize;
        // for (int i = 1; i < numGroups; i++) {
        // outputArray[0] += outputArray[i];
        // }
        // }
    }

    public static class Templates extends AbstractTemplates {

        @SuppressWarnings("unused") private final SnippetInfo helloSnippet = snippet(ReduceSnippets.class, "testReduceLoop");
        private final SnippetInfo reduceIntSnippet = snippet(ReduceSnippets.class, "reduceIntAdd");
        private final SnippetInfo reduceIntSnippet2 = snippet(ReduceSnippets.class, "reduceIntAdd2");

        public Templates(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, providers, snippetReflection, target);
        }

        public void lower(StoreAtomicIndexedNode storeAtomicIndexed, AddressNode address, OCLWriteAtomicNode memoryWrite, ValueNode globalId, GlobalThreadSizeNode globalSize, LoweringTool tool) {

            StructuredGraph graph = storeAtomicIndexed.graph();

            JavaKind elementKind = storeAtomicIndexed.elementKind();

            ValueNode value = storeAtomicIndexed.value();
            ValueNode array = storeAtomicIndexed.array();
            ValueNode accumulator = storeAtomicIndexed.getAccumulator();

            ATOMIC_OPERATION operation = ATOMIC_OPERATION.CUSTOM;
            if (value instanceof OCLReduceAddNode) {
                operation = ATOMIC_OPERATION.ADD;
            } else if (value instanceof OCLReduceSubNode) {
                operation = ATOMIC_OPERATION.SUB;
            } else if (value instanceof OCLReduceMulNode) {
                operation = ATOMIC_OPERATION.MUL;
            }

            SnippetInfo snippet = reduceIntSnippet2;
            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());

            // args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("gidx", globalId);
            args.add("globalSize", globalSize);
            args.add("value", storeAtomicIndexed.value());

            template(args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, DEFAULT_REPLACER, args);

        }

    }
}
