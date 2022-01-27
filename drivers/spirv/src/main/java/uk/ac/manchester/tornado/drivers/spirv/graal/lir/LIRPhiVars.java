package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public class LIRPhiVars {

    private List<PhiMeta> phiVars;

    public LIRPhiVars() {
        this.phiVars = new ArrayList<>();
    }

    public void insertPhiValue(AllocatableValue resultPhi, Value value) {
        phiVars.add(new PhiMeta(resultPhi, value));
    }

    public List<PhiMeta> getPhiVars() {
        return phiVars;
    }

    public static class PhiMeta {
        AllocatableValue resultPhi;
        Value value;

        public PhiMeta(AllocatableValue resultPhi, Value value) {
            this.resultPhi = resultPhi;
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        public AllocatableValue getResultPhi() {
            return resultPhi;
        }
    }

}
