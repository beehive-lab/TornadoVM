package tornado.runtime;

import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.JavaKind;
import sun.misc.Unsafe;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

public class TornadoVMConfig extends HotSpotVMConfigAccess {

    public TornadoVMConfig(HotSpotVMConfigStore store) {
        super(store);
    }

    public final boolean useCompressedClassPointers = getFlag("UseCompressedClassPointers", Boolean.class);
    final boolean useCompressedOops = getFlag("UseCompressedOops", Boolean.class);
    public final int arrayOopDescSize = getTypeSize("arrayOopDesc");
    public final int hubOffset = getFieldOffset("oopDesc::_metadata._klass", Integer.class, "Klass*");
    public final int narrowKlassSize = getTypeSize("narrowKlass");
    public final int instanceKlassFieldsOffset = getFieldOffset("InstanceKlass::_fields", Integer.class, "Array<u2>*");

//    @HotSpotVMValue(expression = "arrayOopDesc::length_offset_in_bytes()") @Stable public int arrayLengthOffset;
    public final int arrayOopDescLengthOffset() {
        return useCompressedClassPointers ? hubOffset + narrowKlassSize : arrayOopDescSize;
    }

    public int getArrayBaseOffset(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
            case Byte:
                return Unsafe.ARRAY_BYTE_BASE_OFFSET;
            case Char:
                return Unsafe.ARRAY_CHAR_BASE_OFFSET;
            case Short:
                return Unsafe.ARRAY_SHORT_BASE_OFFSET;
            case Int:
                return Unsafe.ARRAY_INT_BASE_OFFSET;
            case Long:
                return Unsafe.ARRAY_LONG_BASE_OFFSET;
            case Float:
                return Unsafe.ARRAY_FLOAT_BASE_OFFSET;
            case Double:
                return Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
            case Object:
                return Unsafe.ARRAY_OBJECT_BASE_OFFSET;
            default:
                throw shouldNotReachHere();
        }
    }

    public int getArrayIndexScale(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_INDEX_SCALE;
            case Byte:
                return Unsafe.ARRAY_BYTE_INDEX_SCALE;
            case Char:
                return Unsafe.ARRAY_CHAR_INDEX_SCALE;
            case Short:
                return Unsafe.ARRAY_SHORT_INDEX_SCALE;
            case Int:
                return Unsafe.ARRAY_INT_INDEX_SCALE;
            case Long:
                return Unsafe.ARRAY_LONG_INDEX_SCALE;
            case Float:
                return Unsafe.ARRAY_FLOAT_INDEX_SCALE;
            case Double:
                return Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
            case Object:
                return Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            default:
                throw shouldNotReachHere();
        }
    }

}
