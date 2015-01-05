OVX
===

This branch would like to fix that OVX cannot reroute when one or more links are broken in topology. Currently, it can work properly when the link is down at the node which fires the ping request. But, if the link is down on the other node, OVX will not reroute correctly. 
