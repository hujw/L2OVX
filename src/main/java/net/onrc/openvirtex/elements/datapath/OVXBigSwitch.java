/*******************************************************************************
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package net.onrc.openvirtex.elements.datapath;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.onrc.openvirtex.api.service.handlers.TenantHandler;
import net.onrc.openvirtex.elements.OVXMap;
import net.onrc.openvirtex.elements.link.PhysicalLink;
import net.onrc.openvirtex.elements.network.PhysicalNetwork;
import net.onrc.openvirtex.elements.port.OVXPort;
import net.onrc.openvirtex.elements.port.PhysicalPort;
import net.onrc.openvirtex.exceptions.IndexOutOfBoundException;
import net.onrc.openvirtex.exceptions.LinkMappingException;
import net.onrc.openvirtex.exceptions.RoutingAlgorithmException;
import net.onrc.openvirtex.messages.OVXFlowMod;
import net.onrc.openvirtex.messages.OVXPacketOut;
import net.onrc.openvirtex.messages.actions.OVXActionStripVirtualLan;
import net.onrc.openvirtex.messages.actions.OVXActionVirtualLanIdentifier;
import net.onrc.openvirtex.messages.statistics.OVXFlowStatisticsReply;
import net.onrc.openvirtex.packet.Ethernet;
import net.onrc.openvirtex.routing.RoutingAlgorithms;
import net.onrc.openvirtex.routing.RoutingAlgorithms.RoutingType;
import net.onrc.openvirtex.routing.SwitchRoute;
import net.onrc.openvirtex.util.BitSetIndex;
import net.onrc.openvirtex.util.BitSetIndex.IndexType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.U8;

/**
 * The Class OVXBigSwitch.
 *
 */
public class OVXBigSwitch extends OVXSwitch {


    private static Logger log = LogManager.getLogger(OVXBigSwitch.class
            .getName());
    private RoutingAlgorithms alg;
    private final BitSetIndex routeCounter;
    // The calculated routes
    private final ConcurrentHashMap<OVXPort, ConcurrentHashMap<OVXPort, SwitchRoute>> routeMap;

    public OVXBigSwitch(final long switchId, final int tenantId) {
        super(switchId, tenantId);
        try {
            this.alg = new RoutingAlgorithms("spf", (byte) 1);
        } catch (RoutingAlgorithmException e) {
            log.error("Routing algorithm not set for big-switch "
                    + this.getSwitchName());
        }
        this.routeMap = new ConcurrentHashMap<OVXPort, ConcurrentHashMap<OVXPort, SwitchRoute>>();

        this.routeCounter = new BitSetIndex(IndexType.ROUTE_ID);
    }

    /**
     * Gets the routing algorithm used by the big switch.
     *
     * @return the routing algorithm
     */
    public RoutingAlgorithms getAlg() {
        return this.alg;
    }

    /**
     * Sets the routing algorithm used by the big switch.
     *
     * @param alg
     *            the routing algorithm
     */
    public void setAlg(final RoutingAlgorithms alg) {
        this.alg = alg;
    }

    /**
     * Get the route between source and destination ports.
     *
     * @param srcPort
     *            the ingress port on the big switch
     * @param dstPort
     *            the egress port on the big switch
     * @return The route
     */
    public SwitchRoute getRoute(final OVXPort srcPort, final OVXPort dstPort, 
    		boolean... reload) {
    	boolean isReload =  reload.length > 0 ? reload[0] : false;
        return this.alg.getRoutable().getRoute(this, srcPort, dstPort, isReload);
    }

    /**
     * Fetches all routes associated with a specific port, assuming that the
     * routes are duplex. Does not account for the use of backups for now...
     *
     * @param port
     *            the switch port
     * @return all routes associated to the switch port
     */
    public Set<SwitchRoute> getRoutebyPort(final OVXPort port) {
        final Set<SwitchRoute> routes = new HashSet<SwitchRoute>();
        for (final OVXPort vport : this.portMap.values()) {
            if (vport.equals(port)) {
                continue;
            }
            final SwitchRoute rt = this.getRoute(port, vport);
            if (rt != null) {
                final SwitchRoute revrt = this.getRoute(vport, port);
                routes.add(rt);
                routes.add(revrt);
            }
        }
        return routes;
    }

    /**
     * Fetches a bi-directional route based on the routeId.
     *
     * @param routeId
     *            the unique route ID
     * @return the set of routes
     */
    public Set<SwitchRoute> getRoutebyId(final Integer routeId) {
        final Set<SwitchRoute> routes = new HashSet<SwitchRoute>();

        for (ConcurrentHashMap<OVXPort, SwitchRoute> portMap : this.routeMap
                .values()) {

            for (SwitchRoute route : portMap.values()) {
                if (route.getRouteId() == routeId.intValue()) {
                    routes.add(route);
                }
            }
        }
        return routes;
    }

    /**
     * Gets map of all routes for big switch.
     *
     * @return map of all routes for big switch
     */
    public ConcurrentHashMap<OVXPort, ConcurrentHashMap<OVXPort, SwitchRoute>> getRouteMap() {
        return this.routeMap;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.onrc.openvirtex.elements.datapath.Switch#removePort(short)
     */
    @Override
    public boolean removePort(final Short portNumber) {
        if (!this.portMap.containsKey(portNumber)) {
            return false;
        } else {
            // TODO: Not removing the routes that have this port as a
            // destination. Do it!
            this.routeMap.remove(this.portMap.get(portNumber));

            for (ConcurrentHashMap<OVXPort, SwitchRoute> portMap : this.routeMap
                    .values()) {
                Iterator<Entry<OVXPort, SwitchRoute>> it = portMap.entrySet()
                        .iterator();
                while (it.hasNext()) {
                    Entry<OVXPort, SwitchRoute> entry = it.next();
                    if (entry.getKey().getPortNumber() == portNumber) {
                        it.remove();
                    }
                }
            }
        }
        return true; // ??
    }

    @Override
    public boolean boot() {
        /**
         * If the bigSwitch internal routing mechanism is not manual,
         * pre-compute all the paths between switch ports during boot. The path
         * is computed only if the ovxPorts belong to different physical
         * switches.
         */
    	LinkedList<SwitchRoute> routes = new LinkedList<SwitchRoute>();
        if (this.alg.getRoutingType() != RoutingType.NONE) {
            for (final OVXPort srcPort : this.portMap.values()) {
                for (final OVXPort dstPort : this.portMap.values()) {
                    if (srcPort.getPortNumber() != dstPort.getPortNumber()
                            && srcPort.getPhysicalPort().getParentSwitch() != dstPort
                                    .getPhysicalPort().getParentSwitch()) {
                    	SwitchRoute sr = this.getRoute(srcPort, dstPort);
                    	sr.register();
                    	routes.add(sr);
                    }
                }
            }
        }
        boolean re = super.boot();
        
        // Wait for connecting to the controller 
        try {
            Thread.sleep(100);
        } catch (final InterruptedException e1) {
            log.warn("Timeout failed: {}", e1);
        }
        
		for (SwitchRoute route : routes) {
			writeFlowMod(route);
		}
        
        return re;
    }

    /**
     * Unregister route identified by routeId. Release routeId index, remove
     * virtual/physical route mappings from map, and remove virtual port-pair to
     * route mapping from the switch.
     *
     * @param routeId
     *            unique route ID
     * @return True if successful, false if route doesn't exist.
     */
    public boolean unregisterRoute(final Integer routeId) {
        boolean result = false;
        for (ConcurrentHashMap<OVXPort, SwitchRoute> portMap : this.routeMap
                .values()) {
            for (SwitchRoute route : portMap.values()) {
                if (route.getRouteId() == routeId.intValue()) {
                    this.routeCounter.releaseIndex(routeId);
                    this.map.removeRoute(route);
                    // This operation has to be done twice for both directions.
                    // Set result to false if the route doesn't exist.
                    // TODO: clean up source ports if their mapping becomes
                    // empty
                    if (this.routeMap.get(route.getSrcPort()) == null
                            || this.routeMap.get(route.getSrcPort()).remove(
                                    route.getDstPort()) == null) {
                        return false;
                    } else {
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void unregister() {
        this.removeRoute();
        super.unregister();
    }

    /**
     * Tries to gracefully disable the parts of this big switch that map to the
     * specified physical switch. This method returns True if the shutdown of
     * the PhysicalSwitch does not compromise this switch's functions, e.g., a
     * backup path can be found through the switch.
     *
     * @param phySwitch
     *            the physical switch
     * @return True for success, false otherwise
     */
    public boolean tryRecovery(final PhysicalSwitch phySwitch) {
        // TODO actually do recovery.
        return false;
    }

    @Override
    public OVXPort getPort(final Short portNumber) {
        return this.portMap.get(portNumber);
    }

    @Override
    public String toString() {
        return "SWITCH:\n- switchId: " + this.switchId + "\n- switchName: "
                + this.switchName + "\n- isConnected: " + this.isConnected
                + "\n- tenantId: " + this.tenantId + "\n- missSendLenght: "
                + this.missSendLen + "\n- isActive: " + this.isActive
                + "\n- capabilities: "
                + this.capabilities.getOVXSwitchCapabilities();
    }

    @Override
    public void sendSouth(final OFMessage msg, final OVXPort inPort) {
        if (inPort == null) {
            /* TODO for some OFTypes, we can recover an inport. */
            return;
        }
        final PhysicalSwitch sw = inPort.getPhysicalPort().getParentSwitch();
        log.debug("Sending packet to sw {}: {}", sw.getName(), msg);
        sw.sendMsg(msg, this);
    }

    private SwitchRoute getSwitchRoute(OVXPort ingress, OVXPort egress) {
        Map<OVXPort, SwitchRoute> ingressMap = this.routeMap.get(ingress);
        if (ingressMap == null)
            return null;
        return ingressMap.get(egress);
    }
    
    /**
     * Adds a route between two edge ports of the big switch.
     *
     * @param ingress
     *            ingress port
     * @param egress
     *            egress port
     * @param path
     *            list of links
     * @param revpath
     *            the reverse path from egress to ingress
     * @param priority
     *            priority value
     * @param routeId
     *            unique route ID
     * @return the unique route ID of the new route
     * @throws IndexOutOfBoundException
     *             if insufficient space to store new route
     */
    public SwitchRoute createRoute(final OVXPort ingress, final OVXPort egress,
            final List<PhysicalLink> path, final List<PhysicalLink> revpath,
            byte priority, int routeId) throws IndexOutOfBoundException {
        /*
         * Check if the big-switch route exists. - If not, create both routes
         * (normal and reverse) - If yes, compare the priorities: - If the
         * existing path has a higher priority, keep it as primary, and put the
         * new in the backupMap - If the existing path has a lower priority, put
         * the new path as primary, and the old as backup
         */
        SwitchRoute rtEntry = this.getSwitchRoute(ingress, egress);
        SwitchRoute revRtEntry = this.getSwitchRoute(egress, ingress);
        
        if (rtEntry == null && revRtEntry == null) { 
            rtEntry = new SwitchRoute(this, ingress, egress, routeId, priority);
            revRtEntry = new SwitchRoute(this, egress, ingress, routeId, priority);
            this.map.addRoute(rtEntry, path);
            this.map.addRoute(revRtEntry, revpath);

            this.addToRouteMap(ingress, egress, rtEntry);
            this.addToRouteMap(egress, ingress, revRtEntry);
            
			for (final PhysicalLink edge : path) {
				PhysicalLink plink = PhysicalNetwork.getInstance().getLink(
						edge.getSrcPort(), edge.getDstPort());
				if (plink != null) {
					plink.increaseLinkCount();
					log.info("increase edge {}, count {}", plink,
							plink.getLinkCount());
				}
			}

			for (final PhysicalLink revedge : revpath) {
				PhysicalLink plink = PhysicalNetwork.getInstance().getLink(
						revedge.getSrcPort(), revedge.getDstPort());
				if (plink != null) {
					plink.increaseLinkCount();
					log.info("increase revedge {}, count {}", plink,
							plink.getLinkCount());
				}
			}

            log.info(
                    "Add route for big-switch {} between ports ({},{}) with priority: {} and path: {}",
                    this.switchName, ingress.getPortNumber(),
                    egress.getPortNumber(), U8.f(rtEntry.getPriority()),
                    path.toString());
            log.info(
                    "Add route for big-switch {} between ports ({},{}) with priority: {} and path: {}",
                    this.switchName, egress.getPortNumber(),
                    ingress.getPortNumber(), U8.f(revRtEntry.getPriority()),
                    revpath.toString());
        } else {
            this.routeCounter.releaseIndex(routeId);
            byte currentPriority = rtEntry.getPriority();
            if (U8.f(currentPriority) >= U8.f(priority)) {
                rtEntry.addBackupRoute(priority, path);
                revRtEntry.addBackupRoute(priority, revpath);
                log.info(
                        "Add backup route for big-switch {} between ports ({},{}) with priority: {} and path: {}",
                        this.switchName, ingress.getPortNumber(),
                        egress.getPortNumber(), U8.f(priority), path.toString());
                log.info(
                        "Add backup route for big-switch {} between ports ({},{}) with priority: {} and path: {}",
                        this.switchName, egress.getPortNumber(),
                        ingress.getPortNumber(), U8.f(priority),
                        revpath.toString());
            } else {
                rtEntry.replacePrimaryRoute(priority, path);
                revRtEntry.replacePrimaryRoute(priority, revpath);
                log.info(
                        "Replace primary route for big-switch {} between ports ({},{}) with priority: {} and path: {}",
                        this.switchName, ingress.getPortNumber(),
                        egress.getPortNumber(), U8.f(rtEntry.getPriority()),
                        path.toString());
                log.info(
                        "Replace primary route for big-switch {} between ports ({},{}) with priority: {} and path: {}",
                        this.switchName, egress.getPortNumber(),
                        ingress.getPortNumber(),
                        U8.f(revRtEntry.getPriority()), revpath.toString());
            }
        }
        return rtEntry;
    }

    public SwitchRoute createRoute(final OVXPort ingress, final OVXPort egress,
            final List<PhysicalLink> path, final List<PhysicalLink> revpath,
            byte priority) throws IndexOutOfBoundException {
        final int routeId = this.routeCounter.getNewIndex();
        return this.createRoute(ingress, egress, path, revpath, priority,
                routeId);
    }

    private void addToRouteMap(final OVXPort in, final OVXPort out,
            final SwitchRoute entry) {

        ConcurrentHashMap<OVXPort, SwitchRoute> rtmap = this.routeMap.get(in);

        if (rtmap == null) {
            rtmap = new ConcurrentHashMap<OVXPort, SwitchRoute>();
            this.routeMap.put(in, rtmap);
        }
        rtmap.put(out, entry);
    }

    @Override
    public int translate(final OFMessage ofm, final OVXPort inPort) {
        if (inPort == null) {
            // don't know the PhysicalSwitch, for now return original XID.
            return ofm.getXid();
        } else {
            // we know the PhysicalSwitch
            final PhysicalSwitch psw = inPort.getPhysicalPort()
                    .getParentSwitch();
            return psw.translate(ofm, this);
        }
    }

    public HashSet<PhysicalLink> getAllLinks() {
        HashSet<PhysicalLink> links = new HashSet<PhysicalLink>();
        for (OVXPort p1 : getPorts().values()) {
            for (OVXPort p2 : getPorts().values()) {
                if (!p1.equals(p2)) {
                    try {
                        links.addAll(getRouteMap().get(p1).get(p2).getLinks());
                        links.addAll(getRouteMap().get(p2).get(p1).getLinks());
                    } catch (NullPointerException npe) {
                        log.debug(
                                "No route defined on switch {} in virtual network {} between ports {} and {}",
                                this.getSwitchName(), this.getTenantId(),
                                p1.getPortNumber(), p2.getPortNumber());
                        continue;
                    }
                }
            }
        }
        return links;
    }

    @Override
    public Map<String, Object> getDBObject() {
        Map<String, Object> dbObject = new HashMap<String, Object>();
        dbObject.putAll(super.getDBObject());

        dbObject.put(TenantHandler.ALGORITHM, this.alg.getRoutingType()
                .getValue());
        dbObject.put(TenantHandler.BACKUPS, this.alg.getBackups());

        return dbObject;
    }
    
    // modify by hujw
    // Remove the information about the all route in OVXBigSwitch
    public void removeRoute() {
    	Iterator<Entry<OVXPort, ConcurrentHashMap<OVXPort, SwitchRoute>>> itr = this.routeMap
                .entrySet().iterator();
        while (itr.hasNext()) {
            Entry<OVXPort, ConcurrentHashMap<OVXPort, SwitchRoute>> el = itr
                    .next();
            ConcurrentHashMap<OVXPort, SwitchRoute> portmap = el.getValue();
            for (SwitchRoute route : portmap.values()) {
                this.routeCounter.releaseIndex(route.getRouteId());
                this.map.removeRoute(route);
                log.info("###[removeRoute] {}", route);
            }
            itr.remove();
        }
    }
    
    private OVXFlowMod createFlowMod() {
    	OVXFlowMod fm = new OVXFlowMod();
    	fm.setCommand(OFFlowMod.OFPFC_ADD);
        // Create a buffer id
        int bufferId = OVXPacketOut.BUFFER_ID_NONE;
        if (this.getFromBufferMap(bufferId) != null) {
            bufferId = this.getFromBufferMap(bufferId).getBufferId();
        }
        fm.setBufferId(bufferId);
        
        return fm;
    }
    
    private OFMatch createMatch() {
    	OFMatch match = new OFMatch();
        // Set match filter
        match.setWildcards(Wildcards.FULL
				.matchOn(Flag.IN_PORT)
				.matchOn(Flag.DL_VLAN));//.matchOn(Flag.DL_VLAN_PCP));
        
        return match;
    }
    
    private void writeFlowMod(SwitchRoute sr) {
        OVXPort srcPort = sr.getSrcPort();
        OVXPort dstPort = sr.getDstPort();
        long newc = ((OVXFlowTable)this.flowTable).getCookie();
        
        // Process the virtual flow
        OVXFlowMod ovxfm = createFlowMod();
        OFMatch ovxmatch = createMatch();
        ovxmatch.setInputPort(srcPort.getPortNumber());
        
        ovxfm.setCookie(newc);
        ovxfm.setMatch(ovxmatch);
        ovxfm.setActions(Arrays.asList((OFAction) new OFActionOutput(
        		dstPort.getPortNumber(), (short) 0xffff)));
        
        // Record the OVXFlowMod in the flowtable of OVXSwitch
//        srcPort.getParentSwitch().getFlowTable().handleFlowMods(ovxfm);
        ((OVXFlowTable)this.flowTable).addFlowMod(ovxfm, newc);
        log.info(srcPort.getParentSwitch().getFlowTable().getFlowTable().size());
        // end
        
        OVXFlowMod fm = null;
        OFMatch match = null;
        List<OFAction> approvedActions = null;
        OVXActionVirtualLanIdentifier vlanAct = null;
//        // Set inport 
//        match.setInputPort(srcPort.getPhysicalPort().getPortNumber());
//        fm.setMatch(match);
//        // Output to the first port of the route
//        approvedActions.add(new OFActionOutput(sr.getPathSrcPort().getPortNumber()));
//        
//        vlanAct = new OVXActionVirtualLanIdentifier();
//    	vlanAct.setVirtualLanIdentifier(this.tenantId.shortValue());
//    	approvedActions.add(0, vlanAct);
//    	
//    	fm.setActions(approvedActions);
//    	
//    	fm.setLengthU(OVXFlowMod.MINIMUM_LENGTH);
//        for (final OFAction act : approvedActions) {
//            fm.setLengthU(fm.getLengthU() + act.getLengthU());
//        }
//        // src edge node
//        srcPort.getPhysicalPort().getParentSwitch().sendMsg(fm, this);
//        log.info(fm);
        
        PhysicalPort inPort = null;
        PhysicalPort outPort = null;
        // src edge node --> core nodes --> dst edge node
    	// core nodes
		try {
			// Need to skip one port
			for (final PhysicalLink phyLink : OVXMap.getInstance().getRoute(sr)) {
				if (inPort == null) {
					fm = createFlowMod();
					
					match = createMatch();
					match.setInputPort(srcPort.getPhysicalPort().getPortNumber());
					if (srcPort.getPortTag() != Ethernet.VLAN_UNTAGGED) {
						match.setDataLayerVirtualLan(srcPort.getPortTag().shortValue());
						
					} else {
						// This means the "match" only consider the inport.
						// To do this because the Brocade switch does not support
						// the value of VLAN field set to 0xFFFF when the packet 
						// is untagged. 
						match.setWildcards(Wildcards.FULL
								.matchOn(Flag.IN_PORT));
						
					}
					fm.setMatch(match);
					
					approvedActions = new LinkedList<OFAction>();
			        approvedActions.add(new OFActionOutput(phyLink.getSrcPort().getPortNumber()));
			        
			        vlanAct = new OVXActionVirtualLanIdentifier();
			    	vlanAct.setVirtualLanIdentifier(this.tenantId.shortValue());
			    	approvedActions.add(0, vlanAct);
			    	fm.setActions(approvedActions);
			    	
			    	fm.setLengthU(OVXFlowMod.MINIMUM_LENGTH);
			        for (final OFAction act : approvedActions) {
			            fm.setLengthU(fm.getLengthU() + act.getLengthU());
			        }
			        fm.setCookie(newc);
			        
			        // Send OFFlowMod to switch
			        phyLink.getSrcPort().getParentSwitch().sendMsg(fm, this);
			        
				} else {
					// The src node of next link
					outPort = phyLink.getSrcPort();
					fm = createFlowMod();
					
					match = createMatch();
					match.setInputPort(inPort.getPortNumber())
							.setDataLayerVirtualLan(this.tenantId.shortValue());
					fm.setMatch(match);
					
	                fm.setLengthU(OFFlowMod.MINIMUM_LENGTH
	                        + OFActionOutput.MINIMUM_LENGTH);
	                fm.setActions(Arrays.asList((OFAction) new OFActionOutput(
	                        outPort.getPortNumber(), (short) 0xffff)));
			        fm.setCookie(newc);
			        
	                // The switch of the src port will be the same as "inPort" variable.
	                outPort.getParentSwitch().sendMsg(fm, this);
				}
				inPort = phyLink.getDstPort();
			}
			
		} catch (LinkMappingException e) {
			log.warn("Could not fetch route : {}", e);
		}
		
		// dst edge node
		fm = createFlowMod();
		match = createMatch();
		
		// Set inport (the last port of path)
        match.setInputPort(inPort.getPortNumber())
        		.setDataLayerVirtualLan(this.tenantId.shortValue());
        fm.setMatch(match);
        // Flow actions
        approvedActions = new LinkedList<OFAction>();
        // Output to the first port of the route
        approvedActions.add(new OFActionOutput(dstPort.getPhysicalPort().getPortNumber()));
        
        if (dstPort.getPortTag() != Ethernet.VLAN_UNTAGGED) {
        	vlanAct = new OVXActionVirtualLanIdentifier();
        	vlanAct.setVirtualLanIdentifier(dstPort.getPortTag().shortValue());
        	approvedActions.add(0, vlanAct);
        	
        } else {
        	OVXActionStripVirtualLan stripVlanAct = new OVXActionStripVirtualLan();
        	approvedActions.add(0, stripVlanAct);
        	
        }
    	
    	fm.setActions(approvedActions);
    	
    	fm.setLengthU(OVXFlowMod.MINIMUM_LENGTH);
        for (final OFAction act : approvedActions) {
            fm.setLengthU(fm.getLengthU() + act.getLengthU());
        }
        fm.setCookie(newc);
        
        // dst edge node
        dstPort.getPhysicalPort().getParentSwitch().sendMsg(fm, this);
		
//		log.info("Set vlan id {} from sw {} port {}", this.tenantId, srcPort.getPhysicalPort().getParentSwitch().getSwitchName(), srcPort.getPhysicalPortNumber());
//
//		Map<Short, OVXPort> m = this.getPorts();
//		m.values();
    }

}
