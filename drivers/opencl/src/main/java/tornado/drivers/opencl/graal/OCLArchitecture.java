package tornado.drivers.opencl.graal;

import java.nio.ByteOrder;

import com.oracle.graal.api.code.Architecture;
import com.oracle.graal.api.code.Register.RegisterCategory;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.PlatformKind;

import static com.oracle.graal.api.code.MemoryBarriers.*;

public class OCLArchitecture extends Architecture {

  public static final RegisterCategory INTEGER = new RegisterCategory("int");
  public static final RegisterCategory FLOATING_POINT = new RegisterCategory("float");

  public OCLArchitecture(final int wordSize,final ByteOrder byteOrder) {
    super("OpenCL", wordSize, byteOrder, false, null, LOAD_STORE | STORE_STORE, 1, 0, wordSize);
  }

  @Override
public int getReturnAddressSize() {
	return this.getWordSize();
}

@Override
  public boolean canStoreValue(RegisterCategory category, PlatformKind platformKind) {


    return false;
  }

  @Override
  public PlatformKind getLargestStorableKind(RegisterCategory category) {
   return Kind.Long;
  }

}
