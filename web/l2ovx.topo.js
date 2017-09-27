var CONF = {
    image: {
        width: 50,
        height: 40
    }
};

var width = 800, height = 600;
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

var color = d3.scale.category20();
// basic Topology instance
var topo = new Topology();
// current Topology instance
var active_topo = new Topology();

var elem = {
    force: d3.layout.force()
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
        .attr("id", "console")
        .attr("width", width)
};

function tick() {
	elem.link
		.attr("x1", function(d) { return d.source.x; })
		.attr("y1", function(d) { return d.source.y; })
		.attr("x2", function(d) { return d.target.x; })
		.attr("y2", function(d) { return d.target.y; });

	elem.node
		.attr("transform", function(d) { 
			return "translate(" + d.x + "," + d.y + ")"; 
		});
		
	elem.port
		.attr("transform", function(d) {
			var p = topo.get_port_point(d);
			return "translate(" + p.x + "," + p.y + ")";
		});
}

elem.drag = elem.force.drag().on("dragstart", dragstart);	
function dragstart(d) {
	d3.select(this).classed("fixed", d.fixed = true);
	//d3.select(this).attr("transform", function(d) {return "translate(" + (d.x+100) + "," + (d.y+100) + ")"; });
}

elem.update = function () {

	elem.node.remove();
	elem.link.remove();
	elem.port.remove();

	elem.node = elem.svg.selectAll(".node");
	elem.link = elem.svg.selectAll(".link");
	elem.port = elem.svg.selectAll(".port");
	
	this.force
		.nodes(topo.switches)
		.links(topo.links)
	.start();

	this.link = this.link.data(topo.links);
	this.link.exit().remove();
	this.link.enter().append("line")
		.attr("class", "link")
		//.style("stroke-width", function(d) { return Math.sqrt(d.value); })
		.style("stroke-width", function(d) { return '3px'; })
		.style("stroke", function(d) { 
			//return d.active;
			if (d.active) { return "green" }
			else { return "red" }
		});

//	this.node = this.node.data(topo.switches);
//	var nodeEnter = this.node.enter().append("g")
//		.attr("class", "node")
//		.style("fill", function(d) { return color(d.group); })
//		.style("opacity", 0.9)
//		.on("mouseover", mouseover)
//		.on("mouseout", mouseout)
//		.call(this.drag);
//	nodeEnter.append("circle")
//		.attr("r", 16);
//	nodeEnter.append("svg:text")
//		.attr("class", "nodetext")
//		.attr("dx", 12)
//		.attr("dy", ".35em")
//		.text(function(d) { return d.dpid });
	
    this.node = this.node.data(topo.switches);
	this.node.exit().remove();
    var nodeEnter = this.node.enter().append("g")
        .attr("class", "node")
        .on("dblclick", function(d) { d3.select(this).classed("fixed", d.fixed = false); })
		.on('mouseover', tip.show)
		.on('mouseout', tip.hide)
		.on("click", function(d) {
			//var dpidValue = trim(d.dpid);
			//d3.select(this).attr("transform", "translate(200, 0)");
			console.log("Click: " + d.dpid + ", " + d.x);
			tasks[currTask](d.dpid);
		})
        .call(this.drag);
		
    nodeEnter.append("image")
        .attr("xlink:href", function(d) {
			if (d.active) { return "./router.svg" }
			else { return "./router_cr.gif" }
		})
        .attr("x", -CONF.image.width/2)
        .attr("y", -CONF.image.height/2)
        .attr("width", CONF.image.width)
        .attr("height", CONF.image.height);
    nodeEnter.append("text")
        .attr("dx", -CONF.image.width/2)
        .attr("dy", CONF.image.height-10)
        .text(function(d) { return d.dpid });
	
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
};

// Class Topology
function Topology () {
    this.switches = [];
    this.links = [];
	this.switch_index = {};
}

Topology.prototype.initialize = function (data) {
    this.add_switches(data.switches);
    this.add_links(data.links);
};

Topology.prototype.add_switches = function (switches) {
    for (var i = 0; i < switches.length; i++) {
		var sw = {
			dpid: switches[i].dpid,
			active: false
		}
        this.switches.push(sw);
		console.log("add switch: " + JSON.stringify(sw.dpid));
    }
	this.refresh_switch_index();
};

Topology.prototype.add_links = function (links) {
    for (var i = 0; i < links.length; i++) {
        //console.log("add link: " + JSON.stringify(links[i]));

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
			active: false
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

Topology.prototype.update_active = function(active_switches, active_links) {
	for (var i=0; i<this.switches.length; i++) {
		// set the active of switch to be not alive
		this.switches[i].active = false;
		for (var j=0; j<active_switches.length; j++) {
			if (this.switches[i].dpid == active_switches[j].dpid) {
				// means this switch is alive
				this.switches[i].active = true;
				break;
			}
		}
	}
	
	for (var i=0; i<this.links.length; i++) {
		this.links[i].active = false;
		for (var j=0; j<active_links.length; j++) {
			if (this.links[i].port.src.dpid == active_links[j].src.dpid &&
					this.links[i].port.dst.dpid == active_links[j].dst.dpid) {
				this.links[i].active = true;
				break;				
			}
		}
	}
}

function include(arr, obj) {
    for(var i=0; i<arr.length; i++) {
        if (arr[i] == obj) return true;
    }
}


function download(filename, text) {
    var pom = document.createElement('a');
    pom.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(text));
    pom.setAttribute('download', filename);

    if (document.createEvent) {
        var event = document.createEvent('MouseEvents');
        event.initEvent('click', true, true);
        pom.dispatchEvent(event);
    }
    else {
        pom.click();
    }
}

function update_active_topology(index) {
	
	if (index == 1) {
		d3.xhr("http://211.79.63.108:8080/status")
			.post(
				JSON.stringify({"jsonrpc": "2.0", "method": "getPhysicalTopology", "params": {}, "id": 1}),
				
				//function(err, rawData){
				//	var data = JSON.parse(rawData);
				//	console.log("got response", data);
				//}
				function(error, d) {
					var rawdata = JSON.parse(d.response);
					var graph = rawdata.result;
					//for (var k in graph.switches) {
					//	console.log(graph.switches[k].dpid);
					//}
					//topo.initialize({switches: graph.switches, links: graph.links});
					
					topo.update_active(graph.switches, graph.links);
					
					elem.update();
				}
			);
	}
	
	if (index == 2) {
		d3.xhr("http://211.79.63.108:8080/status")
			.post(
				JSON.stringify({"jsonrpc": "2.0", "method": "getVirtualSwitchMapping", "params": {"tenantId": 1}, "id": 1}),
				
				function(error, d) {
					var rawdata = "";
					var graph = "";
					
					try {
						rawdata = JSON.parse(d.response);
						graph = rawdata.result;
						topo.update_active(graph.switches, graph.primarylinks);
						elem.update();	
					} catch (error) {
						//console.log(error);
					}
					
				}
			);
	}
	
}

function initialize_topology(index) {
	if (index == 1) {
		// Read from data with JSON format
		d3.json("/i2.topo.json", function(error, basic_graph) {
			if (error) return console.warn(error);
			for (var k in basic_graph.switches) {
				console.log(basic_graph.switches[k].dpid);
			}
			topo.initialize({switches: basic_graph.switches, links: basic_graph.links});
			//elem.update();
		});
	}

	if (index == 2) {
		d3.xhr("http://211.79.63.108:8080/status")
			.post(
				JSON.stringify({"jsonrpc": "2.0", "method": "getVirtualSwitchMapping", "params": {"tenantId": 1}, "id": 1}),
				
				function(error, d) {
					var rawdata = "";
					var graph = "";
					
					try {
						rawdata = JSON.parse(d.response);
						graph = rawdata.result;
						// concat "primary" and "backup" links
						var all_links = graph.primarylinks.concat(graph.backuplinks);
						//console.log("links: " + JSON.stringify(all_links));
						topo.initialize({switches: graph.switches, links: all_links});
					} catch (error) {
						//console.log(error);
					}
					
				}
			);
	}
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

function main() {
	elem.node = elem.svg.selectAll(".node");
	elem.link = elem.svg.selectAll(".link");
	elem.port = elem.svg.selectAll(".port");
	
	index = 2;
	
	initialize_topology(index);
	setInterval(function() { update_active_topology(index) }, 1000);
}

main();
