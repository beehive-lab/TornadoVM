package uk.ac.manchester.tornado.drivers.opencl.graal.snippets;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.meta.JavaKind;

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
    public static void hello(int n, int[] data) {
        // JavaKind kind = JavaKind.Int;
        // final int scale = arrayIndexScale(kind);
        // int arrayBaseOffset = arrayBaseOffset(kind);
        // LocationIdentity arrayLocation = getArrayLocation(kind);
        for (int i = 0; i < n; i++) {
            // data[i] = 10;
            // RawStoreNode.storeObject(data, arrayBaseOffset + i + (long) i *
            // scale, i, kind, arrayLocation, false);
        }
    }
}
