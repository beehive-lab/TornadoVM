package tornado.drivers.opencl.mm;

import tornado.api.Event;
import tornado.common.CallStack;
import tornado.common.DeviceObjectState;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.drivers.opencl.OCLDeviceContext;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
public class OCLCallStack extends OCLByteBuffer implements CallStack {

    private final static int RESERVED_SLOTS = 4;

    private int numArgs;
    
    private boolean onDevice;

    public OCLCallStack(long offset, int numArgs, OCLDeviceContext device) {
        super(device, offset, (numArgs + RESERVED_SLOTS) << 3);
        this.numArgs = numArgs;
        
        // clear the buffer and set the mark at the beginning of the arguments
        buffer.clear();
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putInt(0);
        buffer.putInt(numArgs);
        buffer.mark();
        
        onDevice = false;
    }
    
    public boolean isOnDevice(){
    	return onDevice;
    }
    
   

    @Override
    public long getBufferOffset() {
        return 0;
    }

    @Override
	public void write() {
		super.write();
		onDevice = true;
	}

	public int getReservedSlots() {
        return RESERVED_SLOTS;
    }

//    public int getMaxArgs() {
//        return (int) ((bytes >> 3) - getReservedSlots());
//    }

    public int getSlotCount() {
        return (int) bytes >> 3;
    }

//    public void push(final Object[] args) {
//
//        // System.out.println("call stack: push args waiting...");
//
//
//        // long t0 = System.nanoTime();
//
////        buffer.putInt(args.length);
//
//        // long t1 = System.nanoTime();
//        for (int i = 0; i < args.length; i++) {
//            final Object arg = args[i];
//
//            if (arg == null) {
//                buffer.putLong(0);
//            } else if (RuntimeUtilities.isBoxedPrimitive(arg)
//                    || arg.getClass().isPrimitive()) {
//                Tornado.debug("arg[%d]: type=%s, value=%s", i, arg.getClass()
//                        .getName(), arg.toString());
//                PrimitiveSerialiser.put(buffer, arg, 8);
//            } else if (arg instanceof ObjectReference<?,?>) {
//                final ObjectReference<OCLDeviceContext,?> ref = (ObjectReference<OCLDeviceContext,?>) arg;
//                Tornado.debug("arg[%d]: %s", i, ref.toString());
//
//                final ObjectBuffer<?> argBuffer = ref.requestAccess(
//                        deviceContext, access[i]);
//                if (ref.hasOutstandingWrite()) {
//                    Tornado.debug(
//                            "arg[%d]: %s - waiting for outstanding write to complete",
//                            i, ref.toString());
//                    argTransfers.add(ref.getLastWrite().getEvent());
//                }
//
//                if (access[i] == Access.READ)
//                    objectReads.add(ref);
//                else
//                    objectWrites.add(ref);
//
//                Tornado.trace("arg[%d]: buffer @ 0x%x (0x%x)", i,
//                        argBuffer.toAbsoluteAddress(),
//                        argBuffer.toRelativeAddress());
//                buffer.putLong(argBuffer.toAbsoluteAddress());
//            }
//        }
//        // long t2 = System.nanoTime();
//
//        event = enqueueWriteAfterAll(argTransfers);
//
//        // argTransfers.add(enqueueWrite());
//        // event = deviceContext.enqueueMarker(argTransfers);
//
//        // long t3 = System.nanoTime();
//        // System.out.printf("pushArgs: %f, %f, %f\n",RuntimeUtilities.elapsedTimeInSeconds(t0,
//        // t1),RuntimeUtilities.elapsedTimeInSeconds(t1,
//        // t2),RuntimeUtilities.elapsedTimeInSeconds(t2, t3));
//    }

    public void reset() {
        for (int i = 0; i < 2; i++)
            buffer.putLong(i, 0);

        buffer.reset();
        onDevice=false;
    }

    public long getDeoptValue() {
        return buffer.getLong(0);
    }

    public long getReturnValue() {
        return buffer.getLong(8);
    }

    public int getArgCount() {
        return buffer.getInt(8);
    }

    public String toString() {
        return String
                .format("Call Stack: num args = %d, device = %s, size = %s @ 0x%x (0x%x)",
                        numArgs, deviceContext.getDevice().getName(),
                        RuntimeUtilities.humanReadableByteCount(bytes, true),
                        toAbsoluteAddress(), toRelativeAddress());
    }

    @Override
    public void dump() {
        super.dump(8);
    }

	@Override
	public void push(Object arg) {
		
		if (arg == null) {
			Tornado.debug("arg : (null)");
          buffer.putLong(0);
      } else if (RuntimeUtilities.isBoxedPrimitive(arg)
              || arg.getClass().isPrimitive()) {
          Tornado.debug("arg : type=%s, value=%s", arg.getClass()
                  .getName(), arg.toString());
          PrimitiveSerialiser.put(buffer, arg, 8);
      } else {
    	  shouldNotReachHere();
      }
		
	}

	@Override
	public void push(Object arg, DeviceObjectState state) {
		 
		 
		if (arg == null) {
			Tornado.debug("arg : (null)");
	          buffer.putLong(0);
	      } else {
	    	  Tornado.debug("arg : [0x%x] type=%s, value=%s, address=0x%x", arg.hashCode(),arg.getClass()
	                  .getSimpleName(), arg,state.getAddress());
	    	  buffer.putLong(state.getAddress());
	      }
		
	}

	@Override
	public void clearProfiling() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public long getInvokeCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getTimeTotal() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getTimeMean() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getTimeMin() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getTimeMax() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getTimeSD() {
		// TODO Auto-generated method stub
		return 0;
	}

}
