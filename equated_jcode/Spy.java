import java.io.IOException;
import java.util.Map;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.tools.jdi.SocketAttachingConnector;

/**
 * 
 * [com.sun.jdi ArrayReference ClassObjectReference
    ObjectReference PrimitiveValue StringReference VoidValue
    VMDisconnectedException]
   [com.sun.jdi.request MethodEntryRequest EventRequest
    MethodExitRequest StepRequest]
   [com.sun.jdi.event MethodEntryEvent MethodExitEvent
    VMDeathEvent VMDisconnectEvent VMStartEvent
    ExceptionEvent StepEvent]
   [com.sun.tools.jdi SocketAttachingConnector]))
 * 
 * */

@SuppressWarnings("restriction")
public class Spy {
	
	enum PROCSTATE {
		TRACED, SUSPENDED, HALT, TERMINATED, LISTENING, UNKNOWN
	};

	private String[] INCLUDES = new String[]{"com.sun.identity"};
	private String[] COMMON_EXCLUDES = new String[]{"","","",""};
	//private String[] EXCLUDED_METHODS = new String[]{};
	private String HOST = "localhost";
	private String PORT = "8686";
	private String state;
	
	private EventRequestManager eventManager;
	private VirtualMachine vm;

	private static void logProcessState(String state, String transitiveState) {
		System.out.println("VM state changed from ["+state+"] to ["+ transitiveState +"]");
	}
	
	/**
	 * start spy. The method stops any running instance of spy and launches a new configuration
	 * @throws IllegalConnectorArgumentsException 
	 * @throws IOException 
	 * throws exceptions all the way up..it rains bad in winter 
	 * */
	public void start() throws IOException, IllegalConnectorArgumentsException{
		stop();
		makeRecorder();
	}
	
	private void stop(){
		//TODO stop spy
	}
	
	private void makeRecorder() throws IOException, IllegalConnectorArgumentsException{
		makeRecorder(buildConfig(HOST, PORT, INCLUDES, COMMON_EXCLUDES));
	}
	
	private void makeRecorder(VirtualMachine vm){
		try {
			EventSet eset = vm.eventQueue().remove(10000);
			EventIterator eitr = eset.eventIterator();
			
			while(eitr.hasNext()){
				Event event = eitr.nextEvent();
				
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//loads spy configuration and returns instance of vm targeted to debug process
	private VirtualMachine buildConfig(String host, String port, String[] includes, String[] excludes) throws IOException, IllegalConnectorArgumentsException{
		this.vm = loadVirtualMachine(host, port);
		configEventRequests(includes, excludes);
		System.out.println("Config request succeeded!");
		
		return vm;
	}
	
	private VirtualMachine loadVirtualMachine(String host, String port) throws IOException, IllegalConnectorArgumentsException {
		VirtualMachine vm = attach(host, port);
		if(vm != null){
			logProcessState(PROCSTATE.UNKNOWN.name(), PROCSTATE.TRACED.name());
		}
		return vm;
	}


	private VirtualMachine attach(String host, String port) throws IOException, IllegalConnectorArgumentsException {
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
		enableRequest(this.eventManager.createExceptionRequest(null, true, true));
	}

	private EventRequest applyEntryFilters(String[] includes, String[] excludes) {
		MethodEntryRequest entryRequest = this.eventManager.createMethodEntryRequest();
		for(String str : includes){
			entryRequest.addClassFilter(str);
		}
		
		for(String str : excludes){
			entryRequest.addClassExclusionFilter(str);
		}
		return entryRequest;
	}

	private EventRequest applyExitFilters(String[] includes, String[] excludes) {
		MethodExitRequest exitRequest = this.eventManager.createMethodExitRequest();
		for(String str : includes){
			exitRequest.addClassFilter(str);
		}
		
		for(String str : excludes){
			exitRequest.addClassExclusionFilter(str);
		}
		
		return exitRequest;
	}

	private void enableRequest(EventRequest eventRequest) {
		eventRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
		eventRequest.enable();
	}
}