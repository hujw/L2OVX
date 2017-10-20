var CONF = {
    image: {
        width: 50,
        height: 40
    }
};

var width = 800, height = 600;
width = window.innerWidth;
height = window.innerHeight;
var dist = 200, charge = -300;
var sw_position_content;

var tip = d3.tip()
  .attr('class', 'd3-tip')
  .offset([-10, 0])
  .html(function(d) {
    //return "<strong>DPID:</strong> <span style='color:red'>(" + d.x + "," + d.y + ") " + d.dpid + "</span>";
	//sw_position_content += ("(" + d.x + "," + d.y + ") " + d.dpid);
	//console.log(sw_position_content);
	return "<strong>DPID:</strong> <span style='color:red'>" + d.dpid + "</span>";
  })
$(".d3-tip").css({"position":"absolute", "z-index":"1"});

var color = d3.scale.category20();
var topo = new Topology();

var elem = {
    force: d3.layout.force()
		.gravity(0.05)
        .size([width, height])
        .charge(charge)
        .linkDistance(dist)
        .on("tick", tick),
    svg: d3.select("body").append("svg")
        .attr("id", "topology")
        //.attr("width", "100%")
        //.attr("height", "100%")
		.attr("width", width)
        .attr("height", height)
		.call(tip),
    console: d3.select("body").append("div")
        .attr("id", "console"),
        //.attr("width", width)
	btn:  d3.select("body").append("div")	
		
};

function tick() {
	elem.link
		.attr("x1", function(d) { return d.source.x; })
		.attr("y1", function(d) { return d.source.y; })
		.attr("x2", function(d) { return d.target.x; })
		.attr("y2", function(d) { return d.target.y; });

	elem.node
		.attr("transform", function(d) { 
			//console.log(d+": ");
			return "translate(" + d.x + "," + d.y + ")"; 
		});
		
	elem.port
		.attr("transform", function(d) {
			var p = topo.get_port_point(d);
			if ( p == null ) {
				return;
			} else {
				return "translate(" + p.x + "," + p.y + ")";
			}
		});
}

function mouseover() {
	d3.select(this).select("circle").transition()
		.duration(750)
		.attr("r", 26);

	d3.select(this).select("text").transition()
		.duration(750)
		.attr("x", 13)
			.style("stroke-width", ".5px")
			.style("font", "17.5px serif")
			.style("opacity", 1);
}

function mouseout() {
	d3.select(this).select("circle").transition()
		.duration(750)
		.attr("r", 16);
}

// Define funtions of the 'elem' object 
// Function 'drag'
elem.drag = elem.force.drag().on("dragstart", dragstart);	
function dragstart(d) {
	d3.select(this).classed("fixed", d.fixed = true);
}

// Function 'update'
elem.update = function () {
	if (this.node != null) this.node.remove();
	if (this.link != null) this.link.remove();
	if (this.port != null) this.port.remove();

	this.node = elem.svg.selectAll(".node");
	this.link = elem.svg.selectAll(".link");
	this.port = elem.svg.selectAll(".port");
	
	this.force
		.nodes(topo.switches)
		.links(topo.links)
	//.start();

	// Define links
	this.link = this.link.data(topo.links);
	this.link.enter().append("line")
		.attr("class", "link")
		.style("stroke-width", function(d) { return '3px'; })
		.style("stroke", function(d) { 
			//return d.active;
			switch (d.active) { 
				case "up": 
					return "green";
				case "down":
					return "red";
				case "backup":
					return "grey";
			}
		});
	this.link.exit().remove();
	
	// Define nodes
    this.node = this.node.data(topo.switches);
    var nodeEnter = this.node.enter().append("g")
        .attr("class", "node")
        .on("dblclick", function(d) { 
			//d3.select(this).classed("fixed", d.fixed = false); 
			//openPopup(d.dpid);
			tasks[currTask](d.dpid);
			//console.log(nodeconfig.nodegrid.records);
		})
		.on('mouseover', tip.show)
		.on('mouseout', tip.hide)
		.on("click", function(d) {
			//console.log("Click: " + d.dpid + ", " + d.x);
		})
        .call(this.drag);
		
    nodeEnter.append("image")
        .attr("xlink:href", function(d) {
			switch (d.active) {
				case "up":
					return "./router.svg";
				case "down":
					return "./router_cr.gif";
			}
		})
        .attr("x", -CONF.image.width/2)
        .attr("y", -CONF.image.height/2)
        .attr("width", CONF.image.width)
        .attr("height", CONF.image.height);
		
    nodeEnter.append("text")
        .attr("dx", -CONF.image.width/2)
        .attr("dy", CONF.image.height-10)
        .text(function(d) { return d.dpid });
		
	this.node.exit().remove();
	
	// Define ports
	var ports = topo.get_ports();
    this.port.remove();
    this.port = this.svg.selectAll(".port").data(ports);
    var portEnter = this.port.enter().append("g")
        .attr("class", "port");
    portEnter.append("rect")
        .attr("width", 15)
		.attr("height", 15);
    portEnter.append("text")
        .attr("dx", 3)
        .attr("dy", 12)
        .text(function(d) { return d.port });
		
	// Start
	this.force.start();
};

// Class L2OVXRequest
function L2OVXRequest (method, params) {
	this.id = 1;
	this.jsonrpc = "2.0";
	this.method = method;
	this.params = params;
}

// Class Topology
function Topology () {
    this.switches = [];
    this.links = [];
	this.switch_index = {};
}

Topology.prototype.initialize = function (switches, links) {
	this.switches = [];
    this.links = [];
	this.switch_index = {};
	
    this.add_switches(switches);
    this.add_links(links);
};

Topology.prototype.add_switches = function (switches) {
    for (var i = 0; i < switches.length; i++) {
		var sw = {
			dpid: switches[i].dpid,
			active: "down"
		}
        this.switches.push(sw);
		console.log("add switch: " + JSON.stringify(sw.dpid));
    }
	this.refresh_switch_index();
};

Topology.prototype.add_links = function (links) {
    for (var i = 0; i < links.length; i++) {
        var src_dpid = links[i].src.dpid;
        var dst_dpid = links[i].dst.dpid;
        var src_index = this.switch_index[src_dpid];
        var dst_index = this.switch_index[dst_dpid];
		var linkId = links[i].linkId;
        var link = {
            source: src_index,
            target: dst_index,
            port: {
                src: links[i].src,
                dst: links[i].dst
            },
			id: linkId,
			value: 1,
			active: "down"
        }
    this.links.push(link);
    }
};

Topology.prototype.get_ports = function () {
    var ports = [];
    var pushed = {};
    for (var i = 0; i < this.links.length; i++) {
        function _push(p, dir) {
            key = p.dpid + ":" + p.port;
            if (key in pushed) {
                return 0;
            }

            pushed[key] = true;
            p.link_idx = i;
            p.link_dir = dir;
            return ports.push(p);
        }
        _push(this.links[i].port.src, "source");
        _push(this.links[i].port.dst, "target");
    }

    return ports;
};

Topology.prototype.get_port_point = function (d) {
    var weight = 0.88;

    var link = this.links[d.link_idx];
	if ( typeof(link) == "undefined" ) return null;
	
    var x1 = link.source.x;
    var y1 = link.source.y;
    var x2 = link.target.x;
    var y2 = link.target.y;

    if (d.link_dir == "target") weight = 1.0 - weight;

    var x = x1 * weight + x2 * (1.0 - weight);
    var y = y1 * weight + y2 * (1.0 - weight);

    return {x: x, y: y};
};

Topology.prototype.refresh_switch_index = function(){
    this.switch_index = {};
    for (var i = 0; i < this.switches.length; i++) {
        this.switch_index[this.switches[i].dpid] = i;
    }
};

Topology.prototype.update_active = function(active_switches, active_links, active_backuplinks) {
	for (var i=0; i<this.switches.length; i++) {
		// set the active of switch to be not alive
		this.switches[i].active = "down";
		for (var j=0; j<active_switches.length; j++) {
			if (this.switches[i].dpid == active_switches[j].dpid) {
				// means this switch is alive
				this.switches[i].active = "up";
				break;
			}
		}
	}
	
	for (var i=0; i<this.links.length; i++) {
		this.links[i].active = "down";
		for (var j=0; j<active_links.length; j++) {
			if (this.links[i].port.src.dpid == active_links[j].src.dpid &&
					this.links[i].port.dst.dpid == active_links[j].dst.dpid) {
				this.links[i].active = "up";
				break;				
			}
		}
		
		for (var j=0; j<active_backuplinks.length; j++) {
			if (this.links[i].port.src.dpid == active_backuplinks[j].src.dpid &&
					this.links[i].port.dst.dpid == active_backuplinks[j].dst.dpid) {
				this.links[i].active = "backup";
				break;				
			}
		}
	}
}

function update_active_topology(index) {
	
	if (index == 0) {
		d3.xhr("http://211.79.63.108:8080/status")
			.post(
				JSON.stringify({"jsonrpc": "2.0", "method": "getPhysicalTopology", "params": {}, "id": 1}),
				
				//function(err, rawData){
				//	var data = JSON.parse(rawData);
				//	console.log("got response", data);
				//}
				function(error, d) {
					var rawdata = "";
					var graph = "";
					try {
						rawdata = JSON.parse(d.response);
						graph =rawdata.result;
						if ( typeof(graph.switches) == "undefined" || 
							graph.links == "undefined" ) return;
						topo.update_active(graph.switches, graph.links, {});
					
						elem.update();
					} catch (error) {
						console.log(error);
					}
				}
			);
	}
	
	if (index >= 1) {
		var req = new L2OVXRequest("getVirtualSwitchMapping", {"tenantId": index});
		
		d3.xhr("http://211.79.63.108:8080/status")
			.post(
				//JSON.stringify({"jsonrpc": "2.0", "method": "getVirtualSwitchMapping", "params": {"tenantId": 1}, "id": 1}),
				JSON.stringify(req),
				
				function(error, d) {
					var rawdata = "";
					var graph = "";
					
					try {
						rawdata = JSON.parse(d.response);
						graph = rawdata.result;
						
						var switches = {};
						var links = {};
						var backuplinks = {};
									
						if (typeof(graph) != "undefined") {
							// refill the switches and link set
							if (typeof(graph.switches) != "undefined")
								switches = graph.switches;
							if (typeof(graph.primarylinks) != "undefined") {
								links = graph.primarylinks;
								backuplinks = graph.backuplinks;
							}
						}
						
						var all_links = links.concat(backuplinks);
						topo.links = [];
						topo.add_links(all_links);
						topo.update_active(switches, links, backuplinks);
						elem.update();	
						
						/*
						if ( typeof(graph.switches) == "undefined" || 
							graph.primarylinks == "undefined" ) return;
						
						// concat "primary" and "backup" links
						var all_links = graph.primarylinks.concat(graph.backuplinks);
						// refill the all links including backuplink if the topology has.
						topo.links = [];
						topo.add_links(all_links);
						
						// only update the primaryLinks
						topo.update_active(graph.switches, graph.primarylinks);
						elem.update();*/	
					} catch (error) {
						console.log("Tenant (id: " + index + ") does not exist or start up!");
					}
					
				}
			);
	}
	
}

function initialize_topology(index) {
	if (index == 0) {
		// Read from data with JSON format
		d3.json("/i2.topo.json", function(error, basic_graph) {
			if (error) return console.warn(error);
			
			if ( typeof(basic_graph.switches) == "undefined" || 
							basic_graph.links == "undefined" ) return;
			
			topo.initialize(basic_graph.switches, basic_graph.links);
			elem.update();
		});
	}

	if (index >= 1) {
		var req = new L2OVXRequest("getVirtualSwitchMapping", {"tenantId": index});
		
		d3.xhr("http://211.79.63.108:8080/status")
			.post(
				//JSON.stringify({"jsonrpc": "2.0", "method": "getVirtualSwitchMapping", "params": {"tenantId": 1}, "id": 1}),
				JSON.stringify(req),
				
				function(error, d) {
					var rawdata = "";
					var graph = "";
					
					try {
						rawdata = JSON.parse(d.response);
						graph = rawdata.result;
						var switches = {};
						var links = {};
									
						if (typeof(graph) != "undefined") {
							// refill the switches and link set
							if (typeof(graph.switches) != "undefined")
								switches = graph.switches;
							if (typeof(graph.primarylinks) != "undefined") {
								// concat "primary" and "backup" links
								links = graph.primarylinks.concat(graph.backuplinks);
							}
						}
						
						topo.initialize(switches, links);
						elem.update();
					} catch (error) {
						console.log("Tenant (id: " + index + ") does not exist or start up!");
					}
					
				}
			);
	}
}

function main() {
	/*elem.node = elem.svg.selectAll(".node");
	elem.link = elem.svg.selectAll(".link");
	elem.port = elem.svg.selectAll(".port");
	
	index = 0;
	
	initialize_topology(index);
	setInterval(function() { update_active_topology(index) }, 5000);*/
	
	
	elem.svg.append("input")
		.attr("type", "button")
		.attr("value", "Add")
		.attr("onclick", "showPositon()");
}

function showPositon() {
	alert(elem.node.length);
}

main();

// widget configuration
var nodeconfig = {
    nodelayout: {
        name: 'node-layout',
        padding: 0,
        panels: [
            { type: 'main', minSize: 350, resizable: true }
        ]
    },

    nodegrid: { 
        name: 'node-grid',
        style: 'border: 0px; border-left: 1px solid silver',
        columns: [
            //{ field: 'dpid', caption: 'DPID', size: '80px', attr: 'align="center"' },
            { field: 'match', caption: 'Match', size: '50%' },
			{ field: 'actions', caption: 'Action', size: '50%' },
			
        ],
        records: [
            //{ recid: 1, dpid: 'Open', match: 'Short title for the record', actions: 2 },
            //{ recid: 2, dpid: 'Open', match: 'Short title for the record', actions: 3 },
            //{ recid: 3, dpid: 'Closed', match: 'Short title for the record', actions: 1 }
        ]
    }
};

function openPopup(dpid) {
    w2popup.open({
        title   : 'Flows (' + dpid + ')',
        width   : 800,
        height  : 600,
        showMax : true,
        body    : '<div id="popupmain" style="position: absolute; left: 0px; top: 0px; right: 0px; bottom: 0px;"></div>',
        onOpen  : function (event) {
            event.onComplete = function () {
                $('#popupmain').w2render('node-layout');
                w2ui['node-layout'].content('main', w2ui['node-grid']);
            }
        },       
    });
}

$(function () {
    // initialization in memory
	$('#node-layout').w2layout(nodeconfig.nodelayout);
    $().w2grid(nodeconfig.nodegrid);
});

