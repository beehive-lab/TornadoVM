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
import uk.ac.manchester.tornado.drivers.opencl.builtins.OpenCLIntrinsics;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLWriteAtomicNode.ATOMIC_OPERATION;
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

    @Snippet
    public static void reduceIntAdd(int[] inputArray, int[] outputArray, int[] localMemory, int gidx, int groupSize, int numGroups) {

        //@formatter:off
        /*
         * int idx = OpenCLIntrinsics.get_global_id(0); 
         * int localIdx =OpenCLIntrinsics.get_local_id(0); 
         * int groupSize = OpenCLIntrinsics.get_group_size(0);
         */
        //@formatter:on

        // int[] lm = new int[128];
        //
        // // Copy input data to local memory
        // lm[localIdx] = inputArray[gidx];

        // int localIdx = OpenCLIntrinsics.get_local_id(0);
        int localIdx = 0;

        // Reduction in local memory
        for (int stride = 1; stride < (groupSize / 2); stride *= 2) {
            // Node substitution for this barrier
            OpenCLIntrinsics.localBarrier();
            // if (stride > localIdx) {
            inputArray[localIdx] += inputArray[localIdx + stride];
            // }
        }

        // Final copy to global memory
        if (localIdx == 0) {
            outputArray[0] += inputArray[0];
        }

        // Note: This is expensive, but it's the final
        // reduction with the elements left from the first
        // reduction.
        OpenCLIntrinsics.globalBarrier();

        // Final reduction - sequential
        if (gidx == 0) {
            for (int i = 1; i < numGroups; i++) {
                outputArray[0] += outputArray[i];
            }
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo helloSnippet = snippet(ReduceSnippets.class, "testReduceLoop");
        private final SnippetInfo reduceIntSnippet = snippet(ReduceSnippets.class, "reduceIntAdd");

        public Templates(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, providers, snippetReflection, target);
        }

        public void lower(StoreAtomicIndexedNode storeAtomicIndexed, AddressNode address, OCLWriteAtomicNode memoryWrite, LoweringTool tool) {

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

            SnippetInfo snippet = reduceIntSnippet;
            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());

            // TODO: pass the corresponding nodes to the snippet.
            args.add("inputData", storeAtomicIndexed.getInputArray());
            args.add("outputArray", storeAtomicIndexed.array());
            args.add("localMemory", storeAtomicIndexed.array());
            args.add("gidx", storeAtomicIndexed.index());
            // args.add("localIdx", storeAtomicIndexed.index());
            args.add("groupSize", storeAtomicIndexed.index());
            args.add("numGroups", storeAtomicIndexed.index());

            template(args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, DEFAULT_REPLACER, args);

        }

    }
}
