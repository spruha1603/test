*** Settings ***

Library    rasta.RASTA
Library    OperatingSystem

Resource   rasta.robot

Variables   201-config.yaml

*** Test Cases ***

Prepare Tests
    Load Testbed
#    Sync NSO device config

PreTask: Create VPN
    Step "1": Create VPN

Task: Configure L3NNI Network Elements
    Step "1": Create L3NNI in "prepare" state
    Step "2": ActionChecks "PreConfigChecks-1-for-L3NNI"
    Step "3": ActionHealth L3NNI
    Step "4": ChangeToStatePE L3NNI
    Step "5": ActionHealth L3NNI
    Step "6": ActionChecks "PostConfigChecks-1-for-L3NNI"

Task: Activate L3NNI (Not yet in FullStack Workflow)
    Step "1": ActionChecks "PostConfigChecks-2-for-L3NNI"
    Step "2": ActionHealth L3NNI
    Step "3": ChangeToStateReady L3NNI
    Step "4": ActionHealth L3NNI

# FOR FUTURE USE
#Task: Configure "ACCESS" CPE Network Elements (only for managed CPEs)  (ALL ACCESS AT ONCE)
#    Step "1": VPN / Site / Access Update 
#    Step "2": ActionChecks "PostConfigChecks-2-for-Access"

Task: Delete VPN_Aggregate (Last L3NNI + VPN_AGGREGATE)
    Step "1": ActionChecks "PreDeleteChecks-2-for-L3NNI"
    Step "2": ActionHealth L3NNI
    Step "3": Delete VPN_Aggregate
    Step "4": ActionHealth L3NNI

PostTask: Delete VPN
    Step "1": Delete VPN

*** Keywords ***
Load Testbed
    [Tags]  setup
    use testbed "${testbed}"
#    connect to device "${nso}"

Sync NSO device config
    Set NSO cli style to "${cli_style}"
    execute command "devices sync-from" on device "${nso}"

Step "${step}": ActionHealth L3NNI
    [Documentation]   Executes a check on NSO via REST
    [Tags]  #check
        via NSO REST API configure device "${nso}" at URI "${HealthCheckuri}" using "POST" with "json" payload ""

Step "${step}": Create VPN
    [Documentation]   Creates a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${VPNuri}" using "PATCH" with "json" payload "${createVPNPayload}"

Step "${step}": Create L3NNI in "prepare" state
    [Documentation]   Creates a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${VPN_Aggregateuri}" using "PATCH" with "json" payload "${createVPNAggregateInStatePreparePayload}"

Step "${step}": ChangeToStatePE L3NNI
    [Documentation]   Modifies a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${modifyToStateuri}" using "PATCH" with "json" payload "${modifyToStatePEPayload}"

Step "${step}": ChangeToStateReady L3NNI
    [Documentation]   Modifies a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${modifyToStateuri}" using "PATCH" with "json" payload "${modifyToStateReadyPayload}"

Step "${step}": ActionChecks "${action}"
    [Documentation]   Executes a check on NSO via REST
    [Tags]  #check
        via NSO REST API configure device "${nso}" at URI "${ServiceChecksuri}${action}" using "POST" with "json" payload ""

Step "${step}": Delete VPN_Aggregate
    [Documentation]   Delete a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${MPLS_VPN_Aggregateurl}" using "DELETE" with "json" payload ""

Step "${step}": Delete VPN
    [Documentation]   Delete a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${VPN_NAMEuri}" using "DELETE" with "json" payload ""