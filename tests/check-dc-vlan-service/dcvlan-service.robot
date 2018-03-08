# This test case brings up a service through REST (based on the setup in ../netsim-setup)
# and uses different ways to check for the results to demonstrate some approaches
#

*** Settings ***

# Libraries and resource files needed in this test
#
#   rasta.robot       Our core rasta keyword libraries and resource files
#   OperatingSystem   provides some useful keywords to interact with the OS (like to run a command
#                     or to create directories)
#
Resource   rasta.robot
Library    OperatingSystem

# Variable settings. We can use multiple ways to set variables, but a yaml file is
# preferred as it allows to easily create multiline variables including leading spaces/etc., list
# variables/etc.
Variables   config.yaml

*** Test Cases ***

Setup dcVlan service
    Set Global Variable   ${TEST_CASE}    dcvlan_create
    Init test result directory
    Load Topology and connect to devices
    Sync NSO device config
    Retrieve pre-service device configurations
    Create Service
    Retrieve post-service device configurations

Verify Service through Config Diff
    @{diff}=    use ciscoconfdiff to compare configs "${results_dir}/${TEST_CASE}/before/R10" and "${results_dir}/${TEST_CASE}/after/R10"
    Create File   ${results_dir}/${TEST_CASE}/configdiffs/test_result_1    @{diff}[0]
    Create File   ${results_dir}/${TEST_CASE}/configdiffs/test_result_2    @{diff}[1]
    compare latest ciscoconfdiff with reference diffs in files "${data_dir}/${TEST_CASE}/expected/test_result_1" and "${data_dir}/${TEST_CASE}/expected/test_result_2"


Teardown
    Delete Service


*** Keywords ***
Load Topology and connect to devices
    [Tags]  setup
    use testbed "${testbed}"
    connect to device "${nso}"
    connect to device "R10"


Init test result directory
    Create Directory    ${results_dir}/${TEST_CASE}
    Empty Directory     ${results_dir}/${TEST_CASE}
    Create Directory    ${results_dir}/${TEST_CASE}/configdiffs
    Create Directory    ${results_dir}/${TEST_CASE}/before
    Create Directory    ${results_dir}/${TEST_CASE}/after
    Create Directory    ${data_dir}/${TEST_CASE}/expected

Sync NSO device config
    Set NSO cli style to "${cli_style}"
    execute command "devices sync-from" on device "${nso}"

Retrieve pre-service device configurations
    Empty Directory     ${results_dir}/${TEST_CASE}/before
    Empty Directory     ${results_dir}/${TEST_CASE}/after
    execute command "show ver" on device "R10"
    execute command "show running-config | nomore" on device "R10"
    store command "show running-config | nomore" from device "R10" as "tmpval"
    Create File         ${results_dir}/${TEST_CASE}/before/R10     ${tmpval}

Create Service
    [Documentation]   Creates a service instance on NSO via REST
    [Tags]  create
    ${Payload}=   Get File   ${data_dir}/dcvlan_create.json
    via NSO REST API configure device "${nso}" at URI "${service_uri}" using "PATCH" with "json" payload "${Payload}"


Retrieve post-service device configurations
    execute command "show ver " on device "R10"
    execute command "show running-config | nomore " on device "R10"
    store command "show running-config | nomore " from device "R10" as "tmpval"
    Create File         ${results_dir}/${TEST_CASE}/after/R10     ${tmpval}

Delete Service
    [Documentation]   Delete a service instance on NSO via REST
    [Tags]  delete
    ${Payload}=   Get File   ${data_dir}/dcvlan_create.json
    via NSO REST API delete service on device "${nso}" with "json" payload "${Payload}"



