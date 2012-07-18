package com.imaginea.jspy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javassist.CannotCompileException;
import javassist.NotFoundException;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.tools.jdi.SocketAttachingConnector;

/**
 * 
 * [com.sun.jdi ArrayReference ClassObjectReference ObjectReference
 * PrimitiveValue StringReference VoidValue VMDisconnectedException]
 * [com.sun.jdi.request MethodEntryRequest EventRequest MethodExitRequest
 * StepRequest] [com.sun.jdi.event MethodEntryEvent MethodExitEvent VMDeathEvent
 * VMDisconnectEvent VMStartEvent ExceptionEvent StepEvent] [com.sun.tools.jdi
 * SocketAttachingConnector]))
 * 
 * */

@SuppressWarnings("restriction")
public class Spy {

	enum PROCSTATE {
		TRACED, SUSPENDED, HALT, TERMINATED, LISTENING, UNKNOWN
	};

	private String[] INCLUDES = new String[] { "com.sun.identity" };
	private String[] COMMON_EXCLUDES = new String[] { "", "", "", "" };
	// private String[] EXCLUDED_METHODS = new String[]{};
	private String HOST = "localhost";
	private String PORT = "8686";
	private String state;

	private EventRequestManager eventManager;
	private VirtualMachine vm;
	BlockingQueue<Event> eQ = null;
	ClassGenFacility clgen = new ClassGenFacility();

	private static void logProcessState(PROCSTATE cstate, PROCSTATE transitive) {
		System.out.println("VM state changed from [" + cstate.name() + "] to ["
				+ transitive.name() + "]");
	}

	/**
	 * start spy. The method stops any running instance of spy and launches a
	 * fresh configuration
	 * 
	 * @throws IllegalConnectorArgumentsException
	 * @throws IOException
	 *             throws exceptions all the way up
	 * */
	public void start() throws IOException, IllegalConnectorArgumentsException {
		stop();
		makeRecorder();
	}

	private void stop() {
		try {
			vm.dispose();
		} catch (VMDisconnectedException e) {

		}
	}

	private void makeRecorder() throws IOException,
			IllegalConnectorArgumentsException {
		makeRecorder(buildConfig(HOST, PORT, INCLUDES, COMMON_EXCLUDES));
	}

	private void makeRecorder(VirtualMachine vm) {
		try {
			EventSet eset = vm.eventQueue().remove(10000);
			EventIterator eitr = eset.eventIterator();

			eQ = new LinkedBlockingQueue<Event>();

			while (eitr.hasNext()) {
				eQ.add(eitr.nextEvent()); // now this can throw many
											// exceptions..why not offer?
			}
			eset.resume();
			logProcessState(PROCSTATE.TRACED, PROCSTATE.LISTENING);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// loads spy configuration and returns instance of vm targeted to debug
	// process
	private VirtualMachine buildConfig(String host, String port,
			String[] includes, String[] excludes) throws IOException,
			IllegalConnectorArgumentsException {
		this.vm = loadVirtualMachine(host, port);
		configEventRequests(includes, excludes);
		System.out.println("Config request succeeded!");

		return vm;
	}

	private VirtualMachine loadVirtualMachine(String host, String port)
			throws IOException, IllegalConnectorArgumentsException {
		VirtualMachine vm = attach(host, port);
		if (vm != null) {
			logProcessState(PROCSTATE.UNKNOWN, PROCSTATE.TRACED);
		}
		return vm;
	}

	private VirtualMachine attach(String host, String port) throws IOException,
			IllegalConnectorArgumentsException {
		SocketAttachingConnector conn = new SocketAttachingConnector();

		Map<String, Argument> argumentData = conn.defaultArguments();
		argumentData.get("hostname").setValue(host);
		argumentData.get("port").setValue(port);
		return conn.attach(argumentData);
	}

	private void configEventRequests(String[] includes, String[] excludes) {
		attachFilterPreference(includes, excludes);

	}

	private void attachFilterPreference(String[] includes, String[] excludes) {
		this.eventManager = this.vm.eventRequestManager();

		enableRequest(applyEntryFilters(includes, excludes));
		enableRequest(applyExitFilters(includes, excludes));
		enableRequest(this.eventManager
				.createExceptionRequest(null, true, true));
	}

	private EventRequest applyEntryFilters(String[] includes, String[] excludes) {
		MethodEntryRequest entryRequest = this.eventManager
				.createMethodEntryRequest();
		for (String str : includes) {
			entryRequest.addClassFilter(str);
		}

		for (String str : excludes) {
			entryRequest.addClassExclusionFilter(str);
		}
		return entryRequest;
	}

	private EventRequest applyExitFilters(String[] includes, String[] excludes) {
		MethodExitRequest exitRequest = this.eventManager
				.createMethodExitRequest();
		for (String str : includes) {
			exitRequest.addClassFilter(str);
		}

		for (String str : excludes) {
			exitRequest.addClassExclusionFilter(str);
		}

		return exitRequest;
	}

	private void enableRequest(EventRequest eventRequest) {
		eventRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
		eventRequest.enable();
	}

	// start collecting data

	private void collectData() throws Exception {
		if (eQ != null) {
			Arrays.sort(eQ.toArray()); //because queue doesn't guarantee order

			for (Iterator<Event> eitr = eQ.iterator(); eitr.hasNext();) {
				Event e = eitr.next();
				if (!(e instanceof VMDisconnectEvent)
						&& !((e instanceof VMStartEvent) || (e instanceof VMDeathEvent))) {
					makeMethod(e);
				}
			}
		}
	}

	private void makeMethod(Event e) throws Exception {
		Map<String, UnitType> genMap = new HashMap<String, UnitType>();
		if (e instanceof MethodEntryEvent){
			genMap.put("method", UnitType.METHOD);
			genMap.put("thread", UnitType.THREAD);
			genMap.put("class", UnitType.CLASS);
			genMap.put("args", UnitType.MULTI);
			
			preload("MethodEntryNode", genMap);
			
			parseMethodEntryEvent(e, this.getClass().getClassLoader().loadClass("MethodEntryNode"));
		}
		else if (e instanceof MethodExitEvent){
			parseMethodExitEvent(e);
		}
		else if (e instanceof ExceptionEvent){
			parseExceptionEvent(e);
		}
		else if (e instanceof StepEvent){
			parseStepEvent(e);
		}
		else {
			throw new Exception("Unknown Event");
		}

	}

	private Class<?> preload(String className, Map<String, UnitType> vars) {
		Class<?> clz = null;
		try {
			clz = clgen.generateClass(className, vars);
		} catch (NotFoundException e) {
			System.out.println("The reference used for type of field was not found! Make sure you used correct type.");
			e.printStackTrace();
		} catch (CannotCompileException e) {
			System.out.println("the class was generated but failed to compile! \n Perhaps we may retry..");
			e.printStackTrace();
		};
		return clz;
	}

	private void parseMethodEntryEvent(Event e, Class<?> clazz)
			throws IncompatibleThreadStateException, AbsentInformationException {
		Method mt = ((MethodEntryEvent) e).method();
		ThreadReference tr = ((MethodEntryEvent) e).thread();
		StackFrame sfr = tr.frame(0);

		// list maintains order..clojure 'map' orderly applies an anonymous
		// function fn[arg val] later on these two
		List<LocalVariable> args = mt.arguments();
		List<Value> argsVals = new ArrayList<Value>();
		//to derive this
		List<String> argsTypes = new ArrayList<String>();

		generateMEntryBindings(sfr, args, argsVals, argsTypes);
		
		tr.name();
		(mt.declaringType()).name();

		//dirty and wrong...this is under scanner
		MethodEntryNode enode = (MethodEntryNode) clazz.cast(new MethodEntryNode());
		

	}

	/**
	 * @param sfr
	 * @param args
	 * @param argsVals
	 * @param argsTypes
	 */
	private void generateMEntryBindings(StackFrame sfr,
			List<LocalVariable> args, List<Value> argsVals,
			List<String> argsTypes) {
		for (Iterator<LocalVariable> lvitr = args.iterator(); lvitr.hasNext();) {
			argsVals.add(sfr.getValue(lvitr.next())); // get stack frame for
														// local variable
		}

		// following is a decode of (doall (map (fn [arg val]
		// (if val
		// (-> val .type .name)
		// (.typeName arg)))
		// args
		// arg-values))

		// while map progressively applies a function f on collection a and b
		// (first element of a to first of b .. a progressively cumulative
		// pointer over both collections),
		// doall returns an immediate realization of this application (map
		// otherwise returns a lazy-seq (something which isn't available till
		// used first)
		// fn [a' b'] is an anonymous function..it translates as..
		// take two floating pointers arg and val [point arg to args collection,
		// val to args-values]
		// if val is available from args-values, take val.type.name else collect
		// this from args as arg.typeName

		//we'd not want to lose data
		//TODO this ain't good code...need to change this
		boolean isArgsLarger = args.size() > argsVals.size() ? true : false;
		if(isArgsLarger){
			//iterate on smaller
			for (int i = 0; i < argsVals.size(); i++) {
				collectMethodArgValues(args, argsVals, argsTypes, i);
			}	
		}else{
			for (int i = 0; i < args.size(); i++) {
				collectMethodArgValues(args, argsVals, argsTypes, i);
			}
		}
	}

	private void collectMethodArgValues(List<LocalVariable> args,
			List<Value> argsVals, List<String> argsTypes, int i) {
		if (argsVals.get(i) != null) {
			argsTypes.add(argsVals.get(i).type().name());
		}else{
			argsTypes.add(args.get(i).typeName());
		}
	}

	private void parseStepEvent(Event e) {
		// TODO Auto-generated method stub

	}

	private void parseExceptionEvent(Event e) {
		// TODO Auto-generated method stub

	}

	private void parseMethodExitEvent(Event e) {
		// TODO Auto-generated method stub

	}
}
