
var currTenant = 2;
var tenantDiv = d3.select("body").append("div")
					.attr("id", "tenantList");
/*					
var taskButton = tenantDiv.append("button")
					.attr("class", "btn btn-primary")
					.style("margin-bottom", "20px")
					.style("text-align", "left")
					.text(currTenant)
					.on("click", function() {
						this.blur();
						getTenantTopo(currTenant);
					});
*/


var tenantList = [1, 2];
tenantButtonList(tenantList);

function tenantButtonList(list) {
	var taskButton;
	for (var i = 0; i < list.length; i++) {
		taskButton = tenantDiv.append("button")
					.attr("class", "btn btn-primary")
					.attr("id", list[i])
					.style("margin-bottom", "20px")
					.style("text-align", "left")
					.text(list[i])
					.on("click", function() {
						this.blur();
						currTenant = i;
						getTenantTopo();
					});
	}
}
					
// tasks
function getTenantTopo() {	
  
  var req = new L2OVXRequest("getVirtualSwitchMapping", {"tenantId": currTenant});

		d3.xhr("http://211.79.63.108:8080/status")
			.post(
				JSON.stringify(req),
				
				function(error, d) {
					var rawdata = "";
					var graph = "";
					
					try {
						rawdata = JSON.parse(d.response);
						graph = rawdata.result;
						
						topo = new Topology();
						topo.initialize({switches: graph.switches, links: graph.primarylinks});
						elem.update();	
						
					} catch (error) {
						console.log(error);
					}
					
				}
			);  
}

function createRefreshButton() {
    return $('<button/>', {
		class: 'btn btn-primary',
        text: 'Refresh Data',
        id: 'btn_refresh',
        click: ClickRefresh
    });
}