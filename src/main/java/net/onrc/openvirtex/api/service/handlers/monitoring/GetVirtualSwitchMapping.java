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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParamsType;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

import net.onrc.openvirtex.api.service.handlers.ApiHandler;
import net.onrc.openvirtex.api.service.handlers.HandlerUtils;
import net.onrc.openvirtex.api.service.handlers.MonitoringHandler;
import net.onrc.openvirtex.elements.OVXMap;
import net.onrc.openvirtex.elements.datapath.OVXBigSwitch;
import net.onrc.openvirtex.elements.datapath.OVXSwitch;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitchSerializer;
import net.onrc.openvirtex.elements.link.PhysicalLink;
import net.onrc.openvirtex.elements.port.PhysicalPort;
import net.onrc.openvirtex.elements.port.PhysicalPortSerializer;
import net.onrc.openvirtex.exceptions.MissingRequiredField;
import net.onrc.openvirtex.exceptions.NetworkMappingException;
import net.onrc.openvirtex.exceptions.SwitchMappingException;

public class GetVirtualSwitchMapping extends ApiHandler<Map<String, Object>> {

    JSONRPC2Response resp = null;

    @Override
    public JSONRPC2Response process(Map<String, Object> params) {
        try {
        	final GsonBuilder gsonBuilder = new GsonBuilder();
            // gsonBuilder.setPrettyPrinting();
            gsonBuilder.excludeFieldsWithoutExposeAnnotation();
            gsonBuilder.registerTypeAdapter(PhysicalSwitch.class,
                    new PhysicalSwitchSerializer());
            gsonBuilder.registerTypeAdapter(PhysicalPort.class,
                    new PhysicalPortSerializer());
            
            final Gson gson = gsonBuilder.create();
        	
            Map<String, Object> res = new HashMap<String, Object>();
            Number tid = HandlerUtils.<Number>fetchField(
                    MonitoringHandler.TENANT, params, true, null);
            OVXMap map = OVXMap.getInstance();
            //LinkedList<String> list = new LinkedList<String>();
            LinkedList<Map> list = new LinkedList<Map>();
            HashMap<String, Object> subRes = new HashMap<String, Object>();
            for (OVXSwitch vsw : map.getVirtualNetwork(tid.intValue())
                    .getSwitches()) {
                subRes.clear();
                list.clear();
                if (vsw instanceof OVXBigSwitch) {
//                    List<String> l = new LinkedList<String>();
                	List<Map> l = new LinkedList<Map>();
                    for (PhysicalLink li : ((OVXBigSwitch) vsw).getAllPrimaryLinks()) {
//                        l.add(li.toString());
                        l.add(gson.fromJson(gson.toJson(li), Map.class));
                    }
                    subRes.put("primarylinks", l);
                    
                    // add the backup paths
//                    l = new LinkedList<String>();
                    l = new LinkedList<Map>();
                    for (PhysicalLink li : ((OVXBigSwitch) vsw).getAllBackupLinks()) {
                        //l.add(li.toString());
                    	l.add(gson.fromJson(gson.toJson(li), Map.class));
                    }
                    subRes.put("backuplinks", l);
                } else {
                    subRes.put("primarylinks", new LinkedList<>());
                    subRes.put("backuplinks", new LinkedList<>());
                }
                for (PhysicalSwitch psw : map.getPhysicalSwitches(vsw)) {
                    //list.add(psw.getSwitchName());
                	list.add(gson.fromJson(gson.toJson(psw), Map.class));
                }
                subRes.put("switches", list.clone());
                //res.put(vsw.getSwitchName(), subRes.clone());
            }
            //resp = new JSONRPC2Response(res, 0);
            resp = new JSONRPC2Response(subRes, 0);

        } catch (ClassCastException | MissingRequiredField
                | NullPointerException | NetworkMappingException
                | SwitchMappingException e) {
            resp = new JSONRPC2Response(new JSONRPC2Error(
                    JSONRPC2Error.INVALID_PARAMS.getCode(), this.cmdName()
                            + ": Unable to fetch virtual topology : "
                            + e.getMessage()), 0);
        }
        return resp;
    }

    @Override
    public JSONRPC2ParamsType getType() {
        return JSONRPC2ParamsType.OBJECT;
    }
}
