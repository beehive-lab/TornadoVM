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
package tornado.graal.phases.lir;

import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;
import com.oracle.graal.lir.phases.LIRPhaseSuite;

public class TornadoAllocationStage extends LIRPhaseSuite<AllocationContext> {

	public TornadoAllocationStage() {
		//appendPhase(new PhiValueAssociationPhase());
		//appendPhase(new ControlFlowOptimization());
		//appendPhase(new VirtualRegisterAllocationPhase());
	}
}
