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
package net.onrc.openvirtex.messages.actions;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.onrc.openvirtex.elements.address.IPMapper;
import net.onrc.openvirtex.elements.datapath.OVXBigSwitch;
import net.onrc.openvirtex.elements.datapath.OVXSwitch;
import net.onrc.openvirtex.elements.network.OVXNetwork;
import net.onrc.openvirtex.elements.link.OVXLink;
import net.onrc.openvirtex.elements.link.OVXLinkUtils;
import net.onrc.openvirtex.elements.port.OVXPort;
import net.onrc.openvirtex.elements.port.PhysicalPort;
import net.onrc.openvirtex.exceptions.ActionVirtualizationDenied;
import net.onrc.openvirtex.exceptions.DroppedMessageException;
import net.onrc.openvirtex.exceptions.MappingException;
import net.onrc.openvirtex.exceptions.NetworkMappingException;
import net.onrc.openvirtex.exceptions.IndexOutOfBoundException;
import net.onrc.openvirtex.messages.OVXFlowMod;
import net.onrc.openvirtex.messages.OVXPacketIn;
import net.onrc.openvirtex.messages.OVXPacketOut;
import net.onrc.openvirtex.packet.Ethernet;
import net.onrc.openvirtex.protocol.OVXMatch;
import net.onrc.openvirtex.routing.SwitchRoute;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.U16;

// TODO: handle case where input port is null: ODL does this
public class OVXActionOutput extends OFActionOutput implements
        VirtualizableAction {
    Logger log = LogManager.getLogger(OVXActionOutput.class.getName());

    @Override
    public void virtualize(final OVXSwitch sw,
            final List<OFAction> approvedActions, final OVXMatch match)
            throws ActionVirtualizationDenied, DroppedMessageException {
        final OVXPort inPort = sw.getPort(match.getInputPort());

        // TODO: handle TABLE output port here

        final LinkedList<OVXPort> outPortList = this.fillPortList(
                match.getInputPort(), this.getPort(), sw);
        final OVXNetwork vnet;
        try {
            vnet = sw.getMap().getVirtualNetwork(sw.getTenantId());
        } catch (NetworkMappingException e) {
            log.warn("{}: skipping processing of OFAction", e);
            return;
        }
        
        if (match.isFlowMod()) {
            /*
             * FlowMod management Iterate through the output port list. Two main
             * scenarios: - OVXSwitch is BigSwitch and inPort & outPort belongs
             * to different physical switches - Other cases, e.g. SingleSwitch
             * and BigSwitch with inPort & outPort belonging to the same
             * physical switch
             */
            // Retrieve the flowMod from the virtual flow map
            final OVXFlowMod fm;
            try {
                fm = sw.getFlowMod(match.getCookie());
            } catch (MappingException e) {
                log.warn("FlowMod not found in our FlowTable");
                return;
            }
            fm.setCookie(match.getCookie());
            // TODO: Check if the FM has been retrieved
            // Set match on FlowMod message
            fm.setMatch(match);
            
            for (final OVXPort outPort : outPortList) {
                Integer linkId = 0;
                Integer flowId = 0;

                /*
                 * OVXSwitch is BigSwitch and inPort & outPort belongs to
                 * different physical switches
                 */
                if (sw instanceof OVXBigSwitch
                        && inPort.getPhysicalPort().getParentSwitch() != outPort
                                .getPhysicalPort().getParentSwitch()) {
                    // Retrieve the route between the two OVXPorts
                    final OVXBigSwitch bigSwitch = (OVXBigSwitch) outPort
                            .getParentSwitch();
                    final SwitchRoute route = bigSwitch.getRoute(inPort,
                            outPort);
                    if (route == null) {
                        this.log.error(
                                "Cannot retrieve the bigswitch internal route between ports {} {}, dropping message",
                                inPort, outPort);
                        throw new DroppedMessageException(
                                "No such internal route");
                    }

                    // If the inPort belongs to an OVXLink, add rewrite actions
                    // to unset the packet link fields
                    if (inPort.isLink()) {
                    	log.info("This inPort {} on sw {} is a link port", 
                    			inPort.getPortNumber(), inPort.getParentSwitch().getName());
                        final OVXPort dstPort = vnet.getNeighborPort(inPort);
                        final OVXLink link = inPort.getLink().getOutLink();
                        if (link != null
                                && (!match.getWildcardObj().isWildcarded(
                                        Flag.DL_DST) || !match.getWildcardObj()
                                        .isWildcarded(Flag.DL_SRC))) {
                            flowId = vnet.getFlowManager().getFlowId(
                                    match.getDataLayerSource(),
                                    match.getDataLayerDestination());
                            OVXLinkUtils lUtils = new OVXLinkUtils(
                                    sw.getTenantId(), link.getLinkId(), flowId);
                            approvedActions.addAll(lUtils.unsetLinkFields());
                        } else {
                            this.log.error(
                                    "Cannot retrieve the virtual link between ports {} {}, dropping message",
                                    dstPort, inPort);
                            return;
                        }
                    }

                    route.generateRouteFMs(fm.clone());

                    // add the output action with the physical outPort (srcPort
                    // of the route)
                    if (inPort.getPhysicalPortNumber() != route
                            .getPathSrcPort().getPortNumber()) {
                        approvedActions.add(new OFActionOutput(route
                                .getPathSrcPort().getPortNumber()));        
                    } else {
                        approvedActions.add(new OFActionOutput(
                                OFPort.OFPP_IN_PORT.getValue()));
                    }
                    route.register();
                } else {
                    /*
                     * SingleSwitch and BigSwitch with inPort & outPort
                     * belonging to the same physical switch
                     */
                    if (inPort.isEdge()) {
                        if (outPort.isEdge()) {
                            // TODO: this is logically incorrect, i have to do
                            // this because we always add the rewriting actions
                            // in the flowMod. Change it.
                            approvedActions.addAll(IPMapper
                                    .prependUnRewriteActions(sw, match, outPort
                                            .getPhysicalPort()));
                        } else {
                            /*
                             * If inPort is edge and outPort is link: - retrieve
                             * link - generate the link's FMs - add actions to
                             * current FM to write packet fields related to the
                             * link
                             */
                            final OVXLink link = outPort.getLink().getOutLink();
                            linkId = link.getLinkId();
                            try {
                                flowId = vnet.getFlowManager().storeFlowValues(
                                        match.getDataLayerSource(),
                                        match.getDataLayerDestination());
                                link.generateLinkFMs(fm.clone(), flowId);
                                approvedActions.addAll(new OVXLinkUtils(sw
                                        .getTenantId(), linkId, flowId)
                                        .setLinkFields());
                            } catch (IndexOutOfBoundException e) {
                                log.error(
                                        "Too many host to generate the flow pairs in this virtual network {}. "
                                                + "Dropping flow-mod {} ",
                                        sw.getTenantId(), fm);
                                throw new DroppedMessageException();
                            }
                        }
                    } else {
                        if (outPort.isEdge()) {
                            /*
                             * If inPort belongs to a link and outPort is edge:
                             * - retrieve link - add actions to current FM to
                             * restore original IPs - add actions to current FM
                             * to restore packet fields related to the link
                             */
                            approvedActions.addAll(IPMapper
                                    .prependUnRewriteActions(sw, match, outPort
                                            .getPhysicalPort()));
                            // rewrite the OFMatch with the values of the link
                            final OVXPort dstPort = vnet
                                    .getNeighborPort(inPort);
                            final OVXLink link = dstPort.getLink().getOutLink();
                            if (link != null) {
                                flowId = vnet.getFlowManager().getFlowId(
                                        match.getDataLayerSource(),
                                        match.getDataLayerDestination());
                                OVXLinkUtils lUtils = new OVXLinkUtils(
                                        sw.getTenantId(), link.getLinkId(),
                                        flowId);
                                approvedActions
                                        .addAll(lUtils.unsetLinkFields());
                            } else {
                                // TODO: substitute all the return with
                                // exceptions
                                this.log.error(
                                        "Cannot retrieve the virtual link between ports {} {}, dropping message",
                                        dstPort, inPort);
                                return;
                            }
                        } else {
                            final OVXLink link = outPort.getLink().getOutLink();
                            linkId = link.getLinkId();
                            try {
                                flowId = vnet.getFlowManager().storeFlowValues(
                                        match.getDataLayerSource(),
                                        match.getDataLayerDestination());
                                link.generateLinkFMs(fm.clone(), flowId);
                                approvedActions.addAll(new OVXLinkUtils(sw
                                        .getTenantId(), linkId, flowId)
                                        .setLinkFields());
                            } catch (IndexOutOfBoundException e) {
                                log.error(
                                        "Too many host to generate the flow pairs in this virtual network {}. "
                                                + "Dropping flow-mod {} ",
                                        sw.getTenantId(), fm);
                                throw new DroppedMessageException();
                            }
                        }
                    }
                    if (inPort.getPhysicalPortNumber() != outPort
                            .getPhysicalPortNumber()) {
                        approvedActions.add(new OFActionOutput(outPort
                                .getPhysicalPortNumber()));
                    } else {
                        approvedActions.add(new OFActionOutput(
                                OFPort.OFPP_IN_PORT.getValue()));
                    }
                }
                // TODO: Check if I need to do the unrewrite here for the single
                // switch
            }
        } else if (match.isPacketOut()) {
            /*
             * PacketOut management. Iterate through the output port list. Three
             * possible scenarios: - outPort belongs to a link: send a packetIn
             * coming from the virtual link end point to the controller -
             * outPort is an edge port: two different sub-cases: - inPort &
             * outPort belongs to the same physical switch, e.g. rewrite outPort
             * - inPort & outPort belongs to different switches (bigSwitch):
             * send a packetOut to the physical port @ the end of the BS route
             */

            // TODO check how to delete the packetOut and if it's required
//            boolean throwException = true;

            for (final OVXPort outPort : outPortList) {
                /**
                 * If the outPort belongs to a virtual link, generate a packetIn
                 * coming from the end point of the link to the controller.
                 */
                if (outPort.isLink()) {
                	this.log.info("The outPort is a link port with match field {}", match);
                    final OVXPort dstPort = outPort.getLink().getOutLink()
                            .getDstPort();
                    dstPort.getParentSwitch().sendMsg(
                            new OVXPacketIn(match.getPktData(),
                                    dstPort.getPortNumber()), sw);
                    this.log.info(
                            "Generate a packetIn from OVX Port {}/{}, physicalPort {}/{}",
                            dstPort.getParentSwitch().getSwitchName(),
                            dstPort.getPortNumber(), dstPort.getPhysicalPort()
                                    .getParentSwitch().getSwitchName(),
                            dstPort.getPhysicalPortNumber());
                } else if (sw instanceof OVXBigSwitch) {
                    /**
                     * Big-switch management. Generate a packetOut to the
                     * physical outPort
                     */
                    // Only generate pkt_out if a route is configured between in
                    // and output port
                    if ((inPort == null)
                            || (((OVXBigSwitch) sw).getRoute(inPort, outPort) != null)) {
                        final PhysicalPort dstPort = outPort.getPhysicalPort();
                        
                        ConcurrentHashMap<PhysicalPort, Short> pairPortTag = 
            					(ConcurrentHashMap<PhysicalPort, Short>) 
            					sw.getMap().getPortTagPair(sw.getTenantId());
                        Short tag = pairPortTag.get(dstPort);
                        if ((tag != null) && (tag.shortValue() != Ethernet.VLAN_UNTAGGED)) {
                    		Ethernet newPkt = new Ethernet();
                    		newPkt.deserialize(match.getPktData(), 0,
                    				match.getPktData().length);
                    		
                    		newPkt.setVlanID(tag.shortValue()).setPriorityCode((byte)0);
                    		match.setPktData(newPkt.serialize());
                    		match.setDataLayerVirtualLan(tag.shortValue());
                        }
                        
                        dstPort.getParentSwitch().sendMsg(
                                new OVXPacketOut(match.getPktData(),
                                        OFPort.OFPP_NONE.getValue(),
                                        dstPort.getPortNumber()), sw);
                        this.log.debug("PacketOut {} for a bigSwitch port, "
                                + "generate a packet to Physical Port {}/{}",
                                match,
                                dstPort.getParentSwitch().getSwitchName(),
                                dstPort.getPortNumber());
                    }
                } else {
                    /**
                     * Else (e.g. the outPort is an edgePort in a single switch)
                     * modify the packet and send to the physical switch.
                     *
                     */
//                    throwException = false;
                    approvedActions.addAll(IPMapper
                            .prependUnRewriteActions(sw, match, outPort
                                    .getPhysicalPort()));
                    approvedActions.add(new OFActionOutput(outPort
                            .getPhysicalPortNumber()));
                    
                    this.log.info(
                            "Physical ports are on the same physical switch, rewrite only outPort to {}",
                            outPort.getPhysicalPortNumber());
                }
            }
//            if (throwException) {
//                throw new DroppedMessageException();
//            }
        }

    }

    private LinkedList<OVXPort> fillPortList(final Short inPort,
            final Short outPort, final OVXSwitch sw)
            throws DroppedMessageException {
        final LinkedList<OVXPort> outPortList = new LinkedList<OVXPort>();
        if (U16.f(outPort) < U16.f(OFPort.OFPP_MAX.getValue())) {
            if (sw.getPort(outPort) != null && sw.getPort(outPort).isActive()) {
                outPortList.add(sw.getPort(outPort));
            }
        } else if (U16.f(outPort) == U16.f(OFPort.OFPP_FLOOD.getValue())) {
            final Map<Short, OVXPort> ports = sw.getPorts();
            for (final OVXPort port : ports.values()) {
                if (port.getPortNumber() != inPort && port.isActive()) {
                    outPortList.add(port);
                }
            }
        } else if (U16.f(outPort) == U16.f(OFPort.OFPP_ALL.getValue())) {
            final Map<Short, OVXPort> ports = sw.getPorts();
            for (final OVXPort port : ports.values()) {
                if (port.isActive()) {
                    outPortList.add(port);
                }
            }
        } else {
            log.warn(
                    "Output port from controller currently not supported. Short = {}, Exadecimal = 0x{}",
                    U16.f(outPort),
                    Integer.toHexString(U16.f(outPort) & 0xffff));
        }

        if (outPortList.size() < 1) {
            throw new DroppedMessageException(
                    "No output ports defined; dropping");
        }
        return outPortList;
    }

}
