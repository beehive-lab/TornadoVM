package tornado.drivers.opencl.graal.lir;

import jdk.vm.ci.meta.Value;

public interface OCLIntBuiltinFunctionLIRGenerator {

    /*
     * Unary intrinsics
     */
    Value emitIntAbs(Value value);

    Value emitIntClz(Value value);

    Value emitIntPopcount(Value value);


    /*
     * Binary intrinsics
     */
    Value emitIntMin(Value x, Value y);

    Value emitIntMax(Value x, Value y);

    /*
     * Ternary intrinsics
     */
    Value emitIntClamp(Value x, Value y, Value z);

    Value emitIntMad24(Value x, Value y, Value z);

    Value emitIntMadHi(Value x, Value y, Value z);

    Value emitIntMadSat(Value x, Value y, Value z);

}
