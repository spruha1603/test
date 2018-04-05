#!/bin/bash
#
# script to nuke the simulated environment and local ncs setup
ncs-netsim delete-network
ncs --stop
rm -rf ncs-run
