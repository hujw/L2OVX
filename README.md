# L2OVX
===

## A modification of OVX with VLAN

Currently, OVX uses replacing IP and MAC to identify different tenants. In our environment, we need a service such as VPLS/L2VPN. So we modify the current OVX to let it can support separating tenants by VLANs.

## Build and Install
We have already build this project so everyone can run it directly as using the original OVX. 

$ cd OVX

$ sh scripts/ovx.sh

If you have changed the code, using "mvn compile" and "mvn package" to rebuild the codes.

## Try to use
We provide the commands that using to test OVX with VLAN feature. The network topology (i.e., internet2.py) is the same as described in tutorial of OVX website (http://ovx.onlab.us/getting-started/tutorial/). Please create this topology and connect to OVX first. Then, you can follow the below commands to try our modification.

$ cd OVX/util

$ python ovxctl.py -n createNetwork tcp:localhost:10000 10.0.0.0 16

$ python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:05:00,00:00:00:00:00:00:06:00,00:00:00:00:00:00:0A:00

$ python ovxctl.py -n createPort 1 00:00:00:00:00:00:05:00 1

$ python ovxctl.py -n createPort 1 00:00:00:00:00:00:06:00 2

$ python ovxctl.py -n createPort 1 00:00:00:00:00:00:0A:00 3

$ python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:01 1 00:00:00:00:05:01

$ python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:01 2 00:00:00:00:06:02

$ python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:01 3 00:00:00:00:0A:03

$ python ovxctl.py -n startNetwork 1

In mininet terminal

mininet> h_IAD_1 ping h_EWR_2


Then, dumping the flow entries to check if separating tenants by VLANs instead of IP and MAC.

mininet> dpctl dump-flows
