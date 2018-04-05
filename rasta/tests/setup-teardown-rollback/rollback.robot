# Some test to play around with the rollback keywords provided through nso.py

*** Variables ***

${nso}      ncs

*** Settings ***

Library    rasta.RASTA
Resource   rasta.robot


*** Test Cases ***

Rollback-Test
    use testbed "testbed.yaml"
    connect to device "${nso}"
    Set NSO cli style to "juniper"
    retrieve latest NSO rollback number from "${nso}"
    configure device "${nso}" with config "set devices authgroups group foobar default-map remote-name foo remote-password bar"
    rollback NSO "${nso}" to rollback retrieved
    # The below keyword fails as the error thrown by NSO is caught by pyats/unicon and
    # triggers the command execution failing
    Run NSO command "show configuration authgroups group foobar" with expected result "syntax error: element does not exist"

