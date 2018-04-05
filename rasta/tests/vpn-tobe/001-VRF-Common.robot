*** Settings ***

Library    rasta.RASTA
Library    OperatingSystem

Resource   rasta.robot

Variables   001-config.yaml

*** Test Cases ***

Prepare Tests
    Load Testbed
#    Sync NSO device config

PreTask: Create VPN
    Step "1": Create VPN

Task: Configure "ACCESS" PE Network Elements (PER ACCESS)
    Step "1": Create Access in "prepare" state
    Step "2": ActionChecks "PreConfigChecks-1-for-Access"
    Step "3": ActionHealth Access
    Step "4": ChangeToStatePE Access
    Step "5": ActionHealth Access
    Step "6": ActionChecks "PostConfigChecks-1-for-Access"

# FOR FUTURE USE
#Task: Configure "ACCESS" CPE Network Elements (only for managed CPEs)  (ALL ACCESS AT ONCE)
#    Step "1": VPN / Site / Access Update 
#    Step "2": ActionChecks "PostConfigChecks-2-for-Access"

Task: Activate "ACCESS" (PER ACCESS)
    Step "1": ActionChecks "PreConfigChecks-2-for-Access"
    Step "2": ActionHealth Access
    Step "3": ChangeToStateReady Access
    Step "4": ActionHealth Access

#Task: Delete VPN_SITE (LAST "ACCESS")
#    Step "1": ActionChecks "PreDeleteChecks-1-for-Access"
#    Step "2": ActionHealth Access
#    Step "3": Delete VPN_SITE
#    Step "4": ActionHealth Access

#PostTask: Delete VPN
#       Step "1": Delete VPN

*** Keywords ***
Load Testbed
    [Tags]  setup
    use testbed "${testbed}"
#    connect to device "${nso}"

Sync NSO device config
    Set NSO cli style to "${cli_style}"
    execute command "devices sync-from" on device "${nso}"

Step "${step}": ActionHealth Access
    [Documentation]   Executes a check on NSO via REST
    [Tags]  #check
        via NSO REST API configure device "${nso}" at URI "${HealthCheckuri}" using "POST" with "json" payload ""

Step "${step}": Create VPN
    [Documentation]   Creates a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${VPNuri}" using "PATCH" with "json" payload "${createVPNPayload}"

Step "${step}": Create Access in "prepare" state
    [Documentation]   Creates a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${VPN_SITEuri}" using "PATCH" with "json" payload "${createVPNSiteInStatePreparePayload}"

Step "${step}": ChangeToStatePE Access
    [Documentation]   Modifies a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${modifyToStateuri}" using "PATCH" with "json" payload "${modifyToStatePEPayload}"

Step "${step}": ChangeToStateReady Access
    [Documentation]   Modifies a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${modifyToStateuri}" using "PATCH" with "json" payload "${modifyToStateReadyPayload}"

Step "${step}": ActionChecks "${action}"
    [Documentation]   Executes a check on NSO via REST
    [Tags]  #check
        via NSO REST API configure device "${nso}" at URI "${ServiceChecksuri}${action}" using "POST" with "json" payload ""

Step "${step}": Delete VPN_SITE
    [Documentation]   Delete a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${MPLS_VPN_SITEurl}" using "DELETE" with "json" payload ""

Step "${step}": Delete VPN
    [Documentation]   Delete a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${VPN_NAMEuri}" using "DELETE" with "json" payload ""