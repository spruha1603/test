
# Here we are demonstrating the Suite Setup/Teardown and Test Setup/Teardown mechanisms
# Robot provides

*** Variables ***

${nso}          ncs
${testbed}      testbed.yaml
${check_cmd}    show configuration devices authgroups | display set | match group[12]

*** Settings ***

Library    rasta.RASTA
Resource   rasta.robot

Suite Setup    Load testbed
Test Setup     Retrieve Rollback
Test Teardown  Execute Rollback

*** Test Cases ***

Test 1
    configure device "${nso}" with config "set devices authgroups group group1 default-map remote-name foo remote-password bar"

Test 2
    configure device "${nso}" with config "set devices authgroups group group2 default-map remote-name foo remote-password bar"


# for the final test we don't want to execute the default test setup and teardown
#
Check For correct rollback
    [Setup]        No operation
    [Teardown]     No operation
    execute command "${check_cmd}" on device "${nso}"
    store command "${check_cmd}" from device "${nso}" as "output"
    Should not Contain    ${output}     group1
    Should not Contain    ${output}     group2
     

*** Keywords ***

Load testbed
    use testbed "${testbed}"
    connect to device "${nso}"
    Set NSO cli style to "juniper"

Retrieve Rollback
    retrieve latest NSO rollback number from "${nso}"

Execute Rollback
    rollback NSO "${nso}" to rollback retrieved


