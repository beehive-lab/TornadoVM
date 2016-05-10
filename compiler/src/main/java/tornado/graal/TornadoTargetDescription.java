package tornado.graal;

import tornado.common.Tornado;

import com.oracle.graal.api.code.Architecture;
import com.oracle.graal.api.code.ReferenceMap;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;

public class TornadoTargetDescription extends TargetDescription {

	public TornadoTargetDescription(Architecture arch) {
		super(arch, true, 16, 4096, true);
	}

	@Override
	  public LIRKind getLIRKind(Kind javaKind) {
		switch (javaKind) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
            case Long:
            case Float:
            case Double:
                return LIRKind.value(javaKind);
            case Object:
                return LIRKind.reference(javaKind);
            default:
                return LIRKind.Illegal;
        }
    }

	@Override
	public ReferenceMap createReferenceMap(boolean arg0, int arg1) {
		// TODO Auto-generated method stub
		return null;
	}

}
