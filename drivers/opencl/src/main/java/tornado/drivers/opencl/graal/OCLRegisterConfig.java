package tornado.drivers.opencl.graal;

import tornado.common.exceptions.TornadoInternalError;

import com.oracle.graal.api.code.CalleeSaveLayout;
import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.code.Register;
import com.oracle.graal.api.code.RegisterAttributes;
import com.oracle.graal.api.code.RegisterConfig;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.JavaType;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.PlatformKind;

public class OCLRegisterConfig implements RegisterConfig {

	@Override
	public Register getReturnRegister(Kind kind) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Register getFrameRegister() {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public CallingConvention getCallingConvention(Type type, JavaType returnType,
			JavaType[] parameterTypes, TargetDescription target, boolean stackOnly) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Register[] getCallingConventionRegisters(Type type, Kind kind) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Register[] getAllocatableRegisters() {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Register[] filterAllocatableRegisters(PlatformKind kind, Register[] registers) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Register[] getCallerSaveRegisters() {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public CalleeSaveLayout getCalleeSaveLayout() {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public RegisterAttributes[] getAttributesMap() {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public Register getRegisterForRole(int id) {
		TornadoInternalError.unimplemented();
		return null;
	}

	@Override
	public boolean areAllAllocatableRegistersCallerSaved() {
		TornadoInternalError.unimplemented();
		return false;
	}

	

}
