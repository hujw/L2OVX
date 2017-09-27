'use strict';

var currTask = 0;
/* // title div with label and button
var header = d3.select("body").append("div")
				.attr("class", "well");
header.append("h3").text("Get Flows Info from L2OVX");

var taskLabel = header.append("label")
					.attr("id", "taskLabel")
					.html("&nbsp;");

var taskButton = header.append("button")
					.attr("class", "btn btn-primary")
					.style("margin-bottom", "20px")
					.style("width", "100%")
					.style("text-align", "left")
					.text("Get All Switches")
					.on("click", function() {
					  this.blur();
					  // execute the task
					  tasks[currTask]("");
					  
					  // next task
					  //currTask = ++currTask % tasks.length;
					})
 */
// container for array of tables
var tableDiv = d3.select("body").append("div")
					.attr("id", "tableDiv1");
					
var taskButton = tableDiv.append("button")
					.attr("class", "btn btn-primary")
					.style("margin-bottom", "20px")
					.style("width", "100%")
					.style("text-align", "left")
					.text("Get flows from all switches")
					.on("click", function() {
					  this.blur();
					  // execute the task
					  tasks[currTask]("");
					  
					  // next task
					  //currTask = ++currTask % tasks.length;
					})

// initial data
var tableData;
var initialData = [
  { table: "Flows in all switches", rows: [] },
//  { table: "Another Table", rows: [
//      { id: "1", dpid: "dp1", match: "Row1", actions: "DataT2R1" },
//      { id: "2", dpid: "dp2", match: "Row2", actions: "DataT2R2" },
//      { id: "3", dpid: "dp3", match: "Row3", actions: "DataT2R3" }
//    ]
//  },
//  { table: "3rd Table", rows: [] },
//  { table: "4th Table", rows: [] }
]

// tasks
function task0(selected_dpid = "") {	
  // delete first row of table
  //tableData[0].rows.shift();
  // remove table 4
  //tableData.splice(3, 1);
  
  // load initial tables
  tableData = JSON.parse(JSON.stringify(initialData));
  
  var params = '{}';
  if (selected_dpid != "") {
//	  var dpidValue = trim(selected_dpid);
	  tableData[0].table = selected_dpid;
//	  params = '{"dpid":'+parseInt(dpidValue, 16)+'}';
	  params = '{"dpid":"' + selected_dpid + '"}';
  }

  $.ajax({
	  url: "http://211.79.63.108:8080/status",
	  type: 'POST',
	  dataType: 'json',
	  //data: '{"jsonrpc": "2.0", "method": "getPhysicalFlowtable", "params": {}, "id": 1}',
	  data: '{"jsonrpc": "2.0", "method": "getPhysicalFlowtable", "params": '+ params +', "id": 1}',
	  success: function(d) {
		var switches = d.result;
		var index = 0;
		for (var dpid in switches) {
			var match = "";
			var actions = "";
			
			if (switches[dpid].length > 0) {
				for (var f in switches[dpid]) {
					match = match.concat(JSON.stringify(switches[dpid][f].match,replacer) + '\n');
					actions = actions.concat(JSON.stringify(switches[dpid][f].actionsList,replacer) + '\n');
				}	
				tableData[0].rows.push({ 
					id: index, 
					dipd: dpid, 
					match: match, 
					actions: actions 
				});
				index++;
				
			} else {
				tableData[0].rows.push({ 
					id: index, 
					dipd: dpid, 
					match: match, 
					actions: actions 
				});
				index++;
			}
			
		}
		update(tableData); 
	  }
  });

  //taskLabel.text("Step 1: Initial tables loaded");
  //taskButton.text("Next step: ");
}

function replacer(key, value) {
	var filters = ["wildcards", "dl_vlan_pcp"];
  if (filters.includes(key)) {
    return undefined;
  }
  return value;
}

// task list (array of functions)
var tasks = [task0];


// function in charge of the array of tables
function update(data) {

  // select all divs in the table div, and then apply new data 
  var divs = tableDiv.selectAll("div")
      // after .data() is executed below, divs becomes a d3 update selection
      .data(data, function(d) { return d.table; } // "key" function to disable default by-index evaluation
      ); 

  // use the exit method of the d3 update selection to remove any deleted table div and contents (which would be absent in the data array just applied)
  divs.exit().remove();

  // use the enter metod of the d3 update selection to add new ("entering") items present in the data array just applied
  var divsEnter = divs.enter().append("div")
      .attr("id", function(d) { return d.table + "Div"; })
      .attr("class", "well")
	  .style("width", "100%");

  // add title in new div(s)
  divsEnter.append("h5").text(function(d) { return d.table; });

  // add table in new div(s)
  var tableEnter = divsEnter.append("table")
      .attr("id", function(d) { return d.table; })
      .attr("class", "table table-condensed table-striped table-bordered");

  // append table head in new table(s)
  tableEnter.append("thead")
    .append("tr")
      .selectAll("th")
      .data(["No.", "DPID", "Match", "Actions"]) // table column headers (here constant, but could be made dynamic)
    .enter().append("th")
      .text(function(d) { return d; })

  // append table body in new table(s)
  tableEnter.append("tbody");

  // select all tr elements in the divs update selection
  var tr = divs.select("table").select("tbody").selectAll("tr")
      // after the .data() is executed below, tr becomes a d3 update selection
      .data(
        function(d) { return d.rows; }, // return inherited data item
        function(d) { return d.id; }    // "key" function to disable default by-index evaluation
      ); 

  // use the exit method of the update selection to remove table rows without associated data
  tr.exit().remove();

  // use the enter method to add table rows corresponding to new data
  tr.enter().append("tr");

  // bind data to table cells
  var td = tr.selectAll("td")
      // after the .data() is executed below, the td becomes a d3 update selection
      .data(function(d) { return d3.values(d); });   // return inherited data item

  // use the enter method to add td elements 
  td.enter().append("td")               // add the table cell
      .text(function(d) { return d; })  // add text to the table cell
}