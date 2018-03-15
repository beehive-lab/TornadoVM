package uk.ac.manchester.tornado.drivers.opencl.graal.snippets;

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import java.util.Arrays;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
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

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo helloSnippet = snippet(ReduceSnippets.class, "testReduceLoop");

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

            SnippetInfo snippet = helloSnippet;
            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("n", 128);
            args.add("data", storeAtomicIndexed.array());
            args.addConst("index", memoryWrite.value());

            template(args).instantiate(providers.getMetaAccess(), storeAtomicIndexed, DEFAULT_REPLACER, args);

        }

    }
}
