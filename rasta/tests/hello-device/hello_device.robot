*** Settings ***
Library   rasta.RASTA
Resource  rasta.robot

*** Test Cases ***
Connect
    use testbed "testbed.yaml"
    connect to device "R1"

Execute some commands
    execute command "show ip int brief" on device "R1"
    set current device to "R1"
    execute "show version"
    execute "show ip protocols"

