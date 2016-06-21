package tornado.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tornado.api.Event;
import tornado.common.CallStack;
import tornado.common.DeviceMapping;
import tornado.common.DeviceObjectState;
import tornado.common.SchedulableTask;
import tornado.common.TornadoInstalledCode;
import tornado.common.TornadoLogger;
import tornado.common.enums.Access;
import tornado.common.exceptions.TornadoInternalError;
import static tornado.common.exceptions.TornadoInternalError.*;
import tornado.runtime.api.GlobalObjectState;
import tornado.runtime.graph.ExecutionContext;
import static tornado.runtime.graph.GraphAssembler.*;

public class TornadoVM extends TornadoLogger {

	private final ExecutionContext graphContext;
	private final List<Object> objects;
	private final GlobalObjectState[] globalStates;
	private final CallStack[] stacks;
	private final List<Event>[] events;
	private final List<DeviceMapping> contexts;
	private final TornadoInstalledCode[] installedCode;
	
	private final byte[] code;
	private final ByteBuffer buffer;
	
	private double totalTime;
	private long invocations;
	
	@SuppressWarnings("unchecked")
	public TornadoVM(ExecutionContext graphContext, byte[] code, int limit){
		this.graphContext = graphContext;
		this.code = code;
		
		totalTime = 0;
		invocations = 0;
		
		buffer = ByteBuffer.wrap(code);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.limit(limit);
		
		debug("loading tornado vm...");
		
		guarantee(buffer.get() == SETUP,"invalid code");
		contexts = graphContext.getDevices();
		buffer.getInt();
		stacks = new CallStack[buffer.getInt()];
		events = new List[buffer.getInt()];
		
		installedCode = new TornadoInstalledCode[stacks.length];
		
		for(int i=0;i<events.length;i++){
			events[i] = new ArrayList<Event>();
		}
		
		debug("found %d contexts",contexts.size());
		debug("created %d stacks",stacks.length);
		debug("created %d event lists",events.length);
		
	
		objects = graphContext.getObjects();
		globalStates = new GlobalObjectState[objects.size()];
		debug("fetching %d object states...",globalStates.length);
		for(int i=0;i<objects.size();i++){
			final Object object = objects.get(i);
			globalStates[i] = TornadoRuntime.runtime.resolveObject(object);
			debug("\tobject[%d]: [0x%x] %s %s",i,object.hashCode(),object,globalStates[i]);
		}
		
		byte op = buffer.get();
		while(op != BEGIN){
			guarantee(op == CONTEXT,"invalid code: 0x%x",op);
			final int deviceIndex = buffer.getInt();
			debug("loading context %s",contexts.get(deviceIndex));
			final long t0 = System.nanoTime();
			contexts.get(deviceIndex).ensureLoaded();
			final long t1 = System.nanoTime();
			debug("loaded in %.9f s",(t1-t0)*1e-9);
			op = buffer.get();
		}
		
		debug("vm ready to go");
		buffer.mark();
	}
	
	private final DeviceObjectState resolveObjectState(int index, int device){
		return globalStates[index].getDeviceState(contexts.get(device));
	}
	
	private final static CallStack resolveStack(int index, int numArgs, CallStack[] stacks, DeviceMapping device){
		if(stacks[index] == null){
			stacks[index] = device.createStack(numArgs);
		}
			
		return stacks[index];
	}
	
	public void execute(){
		final long t0 = System.nanoTime();
		
		List<Object> constants = graphContext.getConstants();
		List<SchedulableTask> tasks = graphContext.getTasks();
		
		buffer.reset();
		
		Event lastEvent = null;
		Set<Event> unboundEvents = new HashSet<Event>();
		
		for(List<Event> waitList : events){
			waitList.clear();
		}
		
		while(buffer.hasRemaining()){
			final byte op = buffer.get();
		
		if(op == ALLOCATE){
			final int objectIndex = buffer.getInt();
			final int contextIndex = buffer.getInt();
			final DeviceMapping device = contexts.get(contextIndex);
			final Object object = objects.get(objectIndex);
			debug("vm: ALLOCATE [0x%x] %s on %s",object.hashCode(),object,device);
			final DeviceObjectState objectState = resolveObjectState(objectIndex,contextIndex);
		
			lastEvent = device.ensureAllocated(object, objectState);
			
		} else if (op == COPY_IN){
			final int objectIndex = buffer.getInt();
			final int contextIndex = buffer.getInt();
			final int eventList = buffer.getInt();
			final DeviceMapping device = contexts.get(contextIndex);
			final Object object = objects.get(objectIndex);
			debug("vm: COPY_IN [0x%x] %s on %s [event list=%d]",object.hashCode(),object,device, eventList);
			final DeviceObjectState objectState = resolveObjectState(objectIndex,contextIndex);
			debug("vm: state=%s",objectState);
			
			
			lastEvent = device.ensurePresent(object, objectState);
			
			
			
		} else if (op == STREAM_IN){
			final int objectIndex = buffer.getInt();
			final int contextIndex = buffer.getInt();
			final int eventList = buffer.getInt();
			final DeviceMapping device = contexts.get(contextIndex);
			final Object object = objects.get(objectIndex);
			debug("vm: STREAM_IN [0x%x] %s on %s [event list=%d]",object.hashCode(),object,device,eventList);
			final DeviceObjectState objectState = resolveObjectState(objectIndex,contextIndex);
			debug("vm: state=%s",objectState);
			
			lastEvent = device.streamIn(object, objectState);
			
		}else if (op == STREAM_OUT){
			final int objectIndex = buffer.getInt();
			final int contextIndex = buffer.getInt();
			final int eventList = buffer.getInt();
			final DeviceMapping device = contexts.get(contextIndex);
			final Object object = objects.get(objectIndex);
			debug("vm: STREAM_OUT [0x%x] %s on %s [event list=%d]",object.hashCode(),object,device,eventList);
			final DeviceObjectState objectState = resolveObjectState(objectIndex,contextIndex);
			
			lastEvent = device.streamOut(object, objectState, events[eventList]);
		} else if (op == LAUNCH){
			final int gtid = buffer.getInt();
			final int contextIndex = buffer.getInt();
			final int taskIndex = buffer.getInt();
			final int numArgs = buffer.getInt();
			final int eventList = buffer.getInt();
			debug("vm: LAUNCH %s on %s [event list=%d]",tasks.get(taskIndex).getName(),contexts.get(contextIndex),eventList);
			final DeviceMapping device = contexts.get(contextIndex);
			final CallStack stack = resolveStack(gtid,numArgs,stacks,device);
			final List<Event> waitList = events[eventList];
			final SchedulableTask task = tasks.get(taskIndex);
			
			if(installedCode[taskIndex] == null){
				final long compileStart = System.nanoTime();
				task.mapTo(device);
				installedCode[taskIndex] = device.installCode(task);
				final long compileEnd = System.nanoTime();
				debug("vm: compiled in %.9f s",(compileEnd-compileStart)*1e-9);
			}
			final TornadoInstalledCode code = installedCode[taskIndex];
			
			final Access[] accesses = task.getArgumentsAccess();
			stack.reset();
			for(int i=0;i<numArgs;i++){
				final byte argType = buffer.get();
				final int argIndex = buffer.getInt();
				if(argType == CONSTANT_ARG){
					stack.push(constants.get(argIndex));
				} else if (argType == REFERENCE_ARG){
					final DeviceObjectState objectState = resolveObjectState(argIndex,contextIndex);
					TornadoInternalError.guarantee(objectState.isValid(), "object is not valid: %s %s",objects.get(argIndex),objectState);
					stack.push(objects.get(argIndex),objectState);
					if(accesses[i] == Access.WRITE || accesses[i] == Access.READ_WRITE){
						objectState.setContents(true);
					}
				} else {
					TornadoInternalError.shouldNotReachHere();
				}
			}
			
			lastEvent = code.launch(stack, task.meta(),waitList);
			unboundEvents.add(lastEvent);	
//			lastEvent = new EmptyEvent();
		} else if (op == ADD_DEP){
			final int eventList = buffer.getInt();
			TornadoInternalError.guarantee(lastEvent != null, "lastEvent is null");
			if(!(lastEvent instanceof EmptyEvent)){
			debug("vm: ADD_DEP %s to event list %d",lastEvent,eventList);
			events[eventList].add(lastEvent);
			if(unboundEvents.contains(lastEvent)){
				unboundEvents.remove(lastEvent);
			}
			}
		} else if (op == BARRIER){
			final int eventList = buffer.getInt();
			debug("vm: BARRIER event list %d",eventList);
			for(Event event : unboundEvents){
				if(!(event instanceof EmptyEvent)){
					event.waitOn();
				}
			}
			
		} else if (op == END){
			debug("vm: END");
			break;
		} else {
			debug("vm: invalid op 0x%x(%d)",op,op);
			TornadoInternalError.shouldNotReachHere();
		}
		
		if(lastEvent != null){
			lastEvent.waitOn();
		debug("vm: last event=%s",lastEvent);
		}
		}
		
		
		
		final long t1 = System.nanoTime();
		final double elapsed = (t1 - t0) * 1e-9;
		totalTime += elapsed;
		invocations ++;
		
		debug("vm: complete elapsed=%.9f s (%d iterations, %.9f s mean)",elapsed,invocations,(totalTime/invocations));
	}

	public void dumpTimes() {
		System.out.printf("vm: complete %d iterations, %.9f s mean\n",invocations,(totalTime/invocations));
	}
	
}
