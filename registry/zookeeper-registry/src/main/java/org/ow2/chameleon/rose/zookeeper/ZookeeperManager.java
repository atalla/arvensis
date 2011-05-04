package org.ow2.chameleon.rose.zookeeper;

import static org.apache.zookeeper.CreateMode.EPHEMERAL;
import static org.osgi.service.remoteserviceadmin.EndpointListener.ENDPOINT_LISTENER_SCOPE;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Property;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.ow2.chameleon.json.JSONService;
import org.ow2.chameleon.rose.registry.ImportRegistryProvisioning;

/**
 * TODO Handle concurrency, logging
 * @author barjo
 */
@Component(name="RoSe.registry.zookeeper",propagation=true)
public class ZookeeperManager implements Watcher {
	public static final String SEPARATOR="/";
	
	@Property(name="connection",mandatory=true)
	private String connectString;
	
	@Property(name="timeout",mandatory=false)
	private int sessionTimeout;
	
	@Property(name=ENDPOINT_LISTENER_SCOPE,mandatory=false)
	private String filter;

	@Requires(optional=false)
	private ImportRegistryProvisioning registry;
	
	@Requires(optional=false)
	private JSONService json;
	
	private BundleContext context;
	
	
	public String frameworkid;
	
	private ZooKeeper keeper;
	
	private RoseLocalEndpointListener listener;
	private ZooRemoteEndpointWatcher provisioner;
	
	public ZookeeperManager(BundleContext pContext) {
		context=pContext;
	}
	
	
	/**
	 * On instance validation call-back (iPOJO).
	 */
	@SuppressWarnings("unused")
	@Validate
	private void start() {

		try {
			//connect the client.
			keeper = new ZooKeeper(connectString, sessionTimeout, this);
			
			//create a node for the framework id.
			createFrameworkNode();

		} catch (Exception e) {
			e.printStackTrace(); // TODO log ERROR
		}

	}
	
	/**
	 * On instance invalidation call-back (iPOJO).
	 */
	@SuppressWarnings("unused")
	@Invalidate
	private void stop(){
		System.out.println("Stopping");
		try {
			if(keeper!=null){
				destroyListenerAndProvider();
				keeper.close();
				
			}
		} catch (InterruptedException e) {
			e.printStackTrace(); // TODO log warning
		}
	}
	

	
	/*-----------------------------------*
	 *  Zookeeper Watcher method         *
	 *-----------------------------------*/

	/*
	 * (non-Javadoc)
	 * @see org.apache.zookeeper.Watcher#process(org.apache.zookeeper.WatchedEvent)
	 */
	public void process(WatchedEvent event) {
		switch (event.getState()) {
		case Expired: // TODO handle expired (i.e create a new connection)
		case Disconnected:
			System.out.println("Disconnected Call");
			// The client has been disconnected for some reason, destroy the
			// Listener and the provisioner
			destroyListenerAndProvider();
			break;
		case SyncConnected:
			System.out.println("Connected !");
			// we have been reconnected, recreate the framework node and the
			// Listener and the provisioner
			createFrameworkNode();
			createListenerAndProvisioner();
			break;
		default:
			break;
		}
	}
	


	/**
	 * Destroy the {@link RoseLocalEndpointListener} and the {@link ZooRemoteEndpointWatcher}.
	 */
	private void destroyListenerAndProvider(){
		provisioner.stop();
		listener.stop();
		provisioner = null;
		listener = null;
	}
	
	/**
	 * Create the {@link RoseLocalEndpointListener} and the {@link ZooRemoteEndpointWatcher}
	 */
	private void createListenerAndProvisioner(){
		provisioner = new ZooRemoteEndpointWatcher(this, registry);
		listener = new RoseLocalEndpointListener(this,filter,context);
	}
	
	/**
	 * Create the node associated to the framework id. destroy it if it was previously defined.
	 */
	private void createFrameworkNode(){
		try{
			Stat stat = keeper.exists("/"+frameworkid, false);
			if (stat==null){
				keeper.create("/"+frameworkid, new byte[0], Ids.OPEN_ACL_UNSAFE, EPHEMERAL);
			}else {
				//server crash, we just reconnect
				keeper.delete("/"+frameworkid, -1);
				keeper.create("/"+frameworkid, new byte[0], Ids.OPEN_ACL_UNSAFE, EPHEMERAL); //re create the node
			}
		}catch(Exception ke){
			ke.printStackTrace(); //TODO log warning
		}
	}
	
	
	public ZooKeeper getKeeper() {
		return keeper;
	}

	public JSONService getJson() {
		return json;
	}
	
	public static String computePath(EndpointDescription desc){
		return SEPARATOR+desc.getFrameworkUUID()+SEPARATOR+desc.getId();
	}
}
