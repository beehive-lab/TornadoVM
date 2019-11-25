package uk.ac.manchester.tornado.runtime.graal.compiler;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.runtime.JVMCI;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.gc.CardTableBarrierSet;
import org.graalvm.compiler.nodes.gc.G1BarrierSet;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.spi.GCProvider;


/* This class is a copy of org.graalvm.compiler.hotspot.meta.HotSpotGCProvider
   We do this because the constructor of HotSpotGCProvider takes GraalHotSpotVMConfig as a parameter of which
   we don't know how to obtain an instance and can't be instantiated unless using reflection.
 */
public class TornadoGCProvider implements GCProvider {

    private static class TornadoGCProviderConfig extends HotSpotVMConfigAccess {

        TornadoGCProviderConfig(HotSpotVMConfigStore store) {
            super(store);
        }

        final boolean useG1GC = (Boolean)this.getFlag("UseG1GC", Boolean.class);
        final boolean useDeferredInitBarriers = (Boolean)this.getFlag("ReduceInitialCardMarks", Boolean.class);
    }

    private final BarrierSet barrierSet;

    public TornadoGCProvider() {
        TornadoGCProviderConfig gcConfig = new TornadoGCProviderConfig(((HotSpotJVMCIRuntime) JVMCI.getRuntime()).getConfigStore());
        this.barrierSet = this.createBarrierSet(gcConfig);
    }

    public BarrierSet getBarrierSet() {
        return this.barrierSet;
    }

    private BarrierSet createBarrierSet(TornadoGCProviderConfig config) {
        final boolean useDeferredInitBarriers = config.useDeferredInitBarriers;
        return (BarrierSet)(config.useG1GC ? new G1BarrierSet() {
            protected boolean writeRequiresPostBarrier(FixedAccessNode initializingWrite, ValueNode writtenValue) {
                if (!super.writeRequiresPostBarrier(initializingWrite, writtenValue)) {
                    return false;
                } else {
                    return !useDeferredInitBarriers || !TornadoGCProvider.this.isWriteToNewObject(initializingWrite);
                }
            }
        } : new CardTableBarrierSet() {
            protected boolean writeRequiresBarrier(FixedAccessNode initializingWrite, ValueNode writtenValue) {
                if (!super.writeRequiresBarrier(initializingWrite, writtenValue)) {
                    return false;
                } else {
                    return !useDeferredInitBarriers || !TornadoGCProvider.this.isWriteToNewObject(initializingWrite);
                }
            }
        });
    }

    private boolean isWriteToNewObject(FixedAccessNode initializingWrite) {
        if (!initializingWrite.getLocationIdentity().isInit()) {
            return false;
        } else {
            ValueNode base = initializingWrite.getAddress().getBase();
            if (base instanceof AbstractNewObjectNode) {
                for(Node pred = initializingWrite.predecessor(); pred != null; pred = pred.predecessor()) {
                    if (pred == base) {
                        return true;
                    }

                    if (pred instanceof AbstractNewObjectNode) {
                        initializingWrite.getDebug().log(2, "Disallowed deferred init because %s was last allocation instead of %s", pred, base);
                        return false;
                    }
                }
            }

            initializingWrite.getDebug().log(2, "Unable to find allocation for deferred init for %s with base %s", initializingWrite, base);
            return false;
        }
    }
}
