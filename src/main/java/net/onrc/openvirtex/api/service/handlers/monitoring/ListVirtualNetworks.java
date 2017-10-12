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
package net.onrc.openvirtex.api.service.handlers.monitoring;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.onrc.openvirtex.api.service.handlers.ApiHandler;
import net.onrc.openvirtex.elements.OVXMap;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitchSerializer;
import net.onrc.openvirtex.elements.network.OVXNetwork;
import net.onrc.openvirtex.elements.port.PhysicalPort;
import net.onrc.openvirtex.elements.port.PhysicalPortSerializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParamsType;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

/**
 * Gets a list of network/tenant ids.
 *
 * @return list of tenant ids
 */
public class ListVirtualNetworks extends ApiHandler<Object> {

    @Override
    public JSONRPC2Response process(final Object params) {
        JSONRPC2Response resp = null;
        
        final GsonBuilder gsonBuilder = new GsonBuilder();
        // gsonBuilder.setPrettyPrinting();
        gsonBuilder.excludeFieldsWithoutExposeAnnotation();
        gsonBuilder.registerTypeAdapter(PhysicalSwitch.class,
                new PhysicalSwitchSerializer());
        
        final Gson gson = gsonBuilder.create();
        
        final Map<Integer, OVXNetwork> nets = OVXMap.getInstance()
                .listVirtualNetworks();
        final List<Map<String, Object>> list = new LinkedList<Map<String, Object>>();
        
        for (OVXNetwork n: nets.values()) {
        	Map<String, Object> result = gson.fromJson(gson.toJson(n), Map.class);
        	Map<String, Object> map = new LinkedHashMap<String, Object>();
        	map.put("tenantId", n.getTenantId());
        	map.put("ctrl_ip", n.getControllerUrls());
        	map.put("booted", n.isBooted());
        	map.put("status", n.getStatus());
        	map.put("details", n.getStatusDetails());
        	list.add(map);
        }
//        final List<Integer> list = new ArrayList<Integer>(nets.keySet());
        resp = new JSONRPC2Response(list, 0);
        return resp;
    }

    @Override
    public JSONRPC2ParamsType getType() {
        return JSONRPC2ParamsType.NO_PARAMS;
    }

}
