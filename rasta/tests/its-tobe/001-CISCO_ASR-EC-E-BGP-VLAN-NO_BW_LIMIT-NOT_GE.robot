*** Settings ***

Library    rasta.RASTA
Library    OperatingSystem

Resource   rasta.robot

Variables   001-config.yaml

*** Test Cases ***

Prepare Tests
    Load Testbed
#    Sync NSO device config

Task: Configure "PE Endpoint"
    Step "1": Action Healthcheck
    Step "2": Create PE_Endpoint in "prepare" state
    Step "3": Action Healthcheck
    Step "4": ActionChecks "PreConfigChecks-1-for-PE-Endpoint"
    Step "5": Action Healthcheck
    Step "6": ChangeToStateP
    Step "7": Action Healthcheck

Task: Activate "PE_Endpoint" BGP with Rollback
    Step "1": ActionChecks "PreConfigChecks-2-for-PE-Endpoint"
    Step "2": Action Healthcheck
    Step "3": ChangeToStateActivate
    Step "4": Action Healthcheck
    Step "5": ActionChecks "PostConfigChecks-1-for-PE-Endpoint"
    Step "x6": Action Healthcheck
    Step "7": ChangeToStateP 
    Step "x8": Action Healthcheck

Task: Activate "PE_Endpoint" BGP
    Step "1": ActionChecks "PreConfigChecks-2-for-PE-Endpoint"
    Step "2": Action Healthcheck
    Step "3": ChangeToStateActivate
    Step "4": Action Healthcheck
    Step "5": ActionChecks "PostConfigChecks-1-for-PE-Endpoint"
    Step "x6": Action Healthcheck
    Step "7": ChangeToStateReady
    Step "x8": Action Healthcheck

Task: Delete ITS_Service_Unit - Last PE Endpoint
    Step "1": ActionChecks "PreDeleteChecks-1-for-PE-Endpoint"
    Step "2": Action Healthcheck
    Step "3": Delete PE_Endpoint
    Step "4": Action Healthcheck

*** Keywords ***
Load Testbed
    [Tags]  setup
    use testbed "${testbed}"
#    connect to device "${nso}"

Sync NSO device config
    Set NSO cli style to "${cli_style}"
    execute command "devices sync-from" on device "${nso}"

Step "${step}": Action Healthcheck
    [Documentation]   Executes a check on NSO via REST
    [Tags]  #check
        via NSO REST API configure device "${nso}" at URI "${HealthCheckuri}" using "POST" with "json" payload ""

Step "${step}": Create PE_Endpoint in "prepare" state
    [Documentation]   Creates a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${itsrfsuri}" using "PATCH" with "json" payload "${createInStatePreparePayload}"

Step "${step}": ChangeToStateP
    [Documentation]   Modifies a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${modifyToStateuri}" using "PATCH" with "json" payload "${modifyToStatePPayload}"

Step "${step}": ChangeToStateActivate
    [Documentation]   Modifies a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${modifyToStateuri}" using "PATCH" with "json" payload "${modifyToStateActivatePayload}"

Step "${step}": ChangeToStateReady
    [Documentation]   Modifies a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${modifyToStateuri}" using "PATCH" with "json" payload "${modifyToStateReadyPayload}"

Step "${step}": ActionChecks "${action}"
    [Documentation]   Executes a check on NSO via REST
    [Tags]  #check
        via NSO REST API configure device "${nso}" at URI "${ServiceChecksuri}${action}" using "POST" with "json" payload ""

Step "${step}": Delete PE_Endpoint
    [Documentation]   Delete a service instance on NSO via REST
    [Tags]  #service
        via NSO REST API configure device "${nso}" at URI "${itsServiceUnituri}" using "DELETE" with "json" payload ""