package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants.DOT;

public class PTXVectorSplit {
    private static final int MAX_VECTOR_SIZE_BYTES = 16;

    public Variable actualVector;
    public PTXKind actualKind;

    public String[] vectorNames;
    public PTXKind newKind;
    public boolean fullUnwrapVector;

    public PTXVectorSplit(Variable actualVector) {
        this.actualVector = actualVector;
        this.actualKind = ((PTXKind) actualVector.getPlatformKind());

        if (actualKind.getSizeInBytes() < MAX_VECTOR_SIZE_BYTES && actualKind.getVectorLength() != 3) {
            this.vectorNames = new String[] { actualVector.getName() };
            this.newKind = actualKind;
            return;
        }

        if (actualKind.getVectorLength() == 3) {
            this.fullUnwrapVector = true;
        }

        this.newKind = lowerVectorPTXKind(actualKind);
        this.vectorNames = new String[actualKind.getVectorLength() / newKind.getVectorLength()];
        for (int i = 0; i < vectorNames.length; i++) {
            vectorNames[i] = actualVector.getName() + i;
        }
    }

    private PTXKind lowerVectorPTXKind(PTXKind vectorKind) {
        fullUnwrapVector = true;
        return vectorKind.getElementKind();

        // TODO the OpenCL Nvidia driver fully unwraps vector types to variables. For
        // now, we do the same due to memory alignment issues (loads and stores on
        // vector types must be aligned by the size of the vector in PTX). The commented
        // code below does what we should normally do if memory alignment wouldn't be an
        // issue.

        // switch (vectorKind) {
        // case DOUBLE3:
        // return PTXKind.F64;
        // case DOUBLE4:
        // case DOUBLE8:
        // return PTXKind.DOUBLE2;
        // case FLOAT8: return PTXKind.FLOAT4;
        // case FLOAT3: return PTXKind.F32;
        // case INT3: return PTXKind.S32;
        // case CHAR3: return PTXKind.U8;
        // default: TornadoInternalError.shouldNotReachHere();
        // }
        // return null;
    }

    public String getVectorElement(int laneId) {
        assert laneId < 16;
        String vectorElement = vectorNames[laneId / newKind.getVectorLength()];
        if (!fullUnwrapVector) {
            vectorElement += DOT + laneIdToVectorSuffix(laneId);
        }
        return vectorElement;
    }

    private String laneIdToVectorSuffix(int laneId) {
        assert laneId < 16;
        switch ((laneId % 4) % newKind.getVectorLength()) {
            case 0:
                return "x";
            case 1:
                return "y";
            case 2:
                return "z";
            case 3:
                return "w";
            default:
                shouldNotReachHere();
        }
        return null;
    }
}
