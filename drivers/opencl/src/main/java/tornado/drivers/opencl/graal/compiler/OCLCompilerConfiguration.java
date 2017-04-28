/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal.compiler;

import tornado.graal.compiler.TornadoSketchTier;
import com.oracle.graal.lir.phases.LIRPhaseSuite;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PostAllocationOptimizationStage;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationStage;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import tornado.graal.compiler.*;
import tornado.graal.phases.lir.TornadoAllocationStage;

public class OCLCompilerConfiguration implements TornadoCompilerConfiguration {

    @Override
    public TornadoAllocationStage createAllocationStage() {
        return new TornadoAllocationStage();
    }

    @Override
    public TornadoSketchTier createSketchTier(CustomCanonicalizer canonicalizer) {
        return new TornadoSketchTier(canonicalizer);
    }

    @Override
    public TornadoHighTier createHighTier(CustomCanonicalizer canonicalizer) {
        return new OCLHighTier(canonicalizer);
    }

    @Override
    public TornadoLowTier createLowTier(AddressLowering addressLowering) {
        return new OCLLowTier(addressLowering);
    }

    @Override
    public TornadoMidTier createMidTier() {
        return new OCLMidTier();
    }

    @Override
    public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage() {
        return new PostAllocationOptimizationStage();
    }

    @Override
    public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage() {
        return new PreAllocationOptimizationStage();
    }

}
