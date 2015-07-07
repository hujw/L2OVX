package net.onrc.openvirtex.api.service.handlers.tenant;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParamsType;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

import net.onrc.openvirtex.api.service.handlers.ApiHandler;
import net.onrc.openvirtex.api.service.handlers.HandlerUtils;
import net.onrc.openvirtex.api.service.handlers.TenantHandler;
import net.onrc.openvirtex.elements.Mappable;
import net.onrc.openvirtex.elements.OVXMap;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.elements.network.PhysicalNetwork;
import net.onrc.openvirtex.exceptions.InvalidTenantIdException;
import net.onrc.openvirtex.exceptions.MissingRequiredField;
import net.onrc.openvirtex.messages.statistics.OVXFlowStatisticsReply;

public class RemoveFlows extends ApiHandler<Map<String, Object>> {

	Logger log = LogManager.getLogger(RemoveFlows.class.getName());
			
	@Override
	public JSONRPC2Response process(Map<String, Object> params) {
		JSONRPC2Response resp = null;
		
		try {
			final Number tenantId = HandlerUtils.<Number>fetchField(
                    TenantHandler.TENANT, params, true, null);

            HandlerUtils.isValidTenantId(tenantId.intValue());

            final OVXMap map = OVXMap.getInstance();
            LinkedList<OVXFlowStatisticsReply> flows = new LinkedList<OVXFlowStatisticsReply>();

			for (PhysicalSwitch sw : PhysicalNetwork.getInstance()
					.getSwitches()) {
				flows = getSwitchFlowsByTenantId(sw.getSwitchId(), map,
						tenantId.intValue());

				for (OVXFlowStatisticsReply flow : flows) {
					sw.sendDeleteFlowMod(flow);
					this.log.info("Delete flow {} of tenant {}", flow,
							tenantId.intValue());
				}
			}
            resp = new JSONRPC2Response(0);
            
		} catch (final MissingRequiredField e) {
            resp = new JSONRPC2Response(new JSONRPC2Error(
                    JSONRPC2Error.INVALID_PARAMS.getCode(), this.cmdName()
                            + ": Unable to delete the tenant's flows : "
                            + e.getMessage()), 0);
        } catch (final InvalidTenantIdException e) {
            resp = new JSONRPC2Response(new JSONRPC2Error(
                    JSONRPC2Error.INVALID_PARAMS.getCode(), this.cmdName()
                            + ": Invalid tenant id : " + e.getMessage()), 0);
        }
		
        return resp;
	}

	@Override
	public JSONRPC2ParamsType getType() {
		return JSONRPC2ParamsType.OBJECT;
	}
	
	private LinkedList<OVXFlowStatisticsReply> getSwitchFlowsByTenantId(
			long dpid, Mappable map, Integer tid) {
        LinkedList<OVXFlowStatisticsReply> flows = new LinkedList<OVXFlowStatisticsReply>();
        final PhysicalSwitch sw = PhysicalNetwork.getInstance().getSwitch(dpid);
		if (sw.getFlowStats(tid) != null) {
			flows.addAll(sw.getFlowStats(tid));
		}
        return flows;
    }

}
