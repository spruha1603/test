

import re

from ats import topology
from ats.datastructures import AttrDict
from unicon import Unicon
from rasta_exception import RastaException
from robot.api.deco import keyword

from robot.libraries.BuiltIn import BuiltIn
from robot.api import logger

import traceback


class RastaNSO(object):
    """
    The RastaNSO library implements keywords which are specific to NSO.
    NOTE: Please do not import this library directly, but through "Library      rasta.RASTA" which
    inherits/imports this library as well as all other core RASTA libraries.

    """
    ROBOT_LIBRARY_SCOPE = "GLOBAL"

    def __init__(self):
        self.latest_rollback = None

    @keyword('add device "${device}" from NSO "${nso}" to testbed with credentials "${username}"/"${password}"')
    def add_device_from_nso_to_testbed(self, device, nso, username, password):
        """
        This keyword adds the device ${device} which is present as device in NSO to the unicon
        testbed so you can interact with it using the unicon methods
        (like  'execute command "foo" on device "r1").

        It can be used for dynamic environments, like when you add netsim devices as part
        of a test setup or when you work on an NFV project where NSO might
        instantiate devices as part of the service model and you want to perform
        some actions on those devices. As those devices' attributes (like ip address or
        connection protocol) is not known and therefore can't be defined in the testbed.yaml
        file, we provide this method.

        This function extracts some part of the NSO device configuration from NSO using cli
        (address, port, cli protocol)

        Prerequisites: a testbed must be loaded (use tesbed "testbed.yaml"), and a connection to
        NSO needs to be established (connect to device "nso")

        """

        n = self.testbed.devices[nso]
        cli_style = n.state_machine.current_cli_style

        r = n.execute('show configuration devices device {} device-type cli protocol'.format(device), style='j')
        protocol = r.split()[1].split(';')[0]
        r = n.execute('show configuration devices device {} address'.format(device), style='j')
        address = r.split()[1].split(';')[0]
        r = n.execute('show configuration devices device {} port'.format(device), style='j')
        if re.match('^port', r):
            port = r.split()[1].split(';')[0]
        else:
            port = None
        r = n.execute('show configuration devices device {} device-type cli ned-id'.format(device), style='j')
        ned = r.split()[1].split(';')[0]

        # restore cli style to what was set before
        n.cli_style(cli_style)

        ned2os = {
            'cisco-ios': 'ios',
            'cisco-ios-xr': 'iosxr',
        }
        ned2type = {
            'cisco-ios': 'router',
            'cisco-ios-xr': 'router',
        }

        dev = topology.Device(device)
        try:
            dev.os = ned2os[ned]
            dev.type = ned2type[ned]
        except KeyError:
            print("\nERROR: device ned {} not defined in ned2os/ned2type dictionary".format(ned))
            traceback.print_exc()
            raise RastaException

        dev.tacacs.username = username
        dev.passwords.tacacs = password
        dev.passwords.enable = password
        dev.passwords.line = password
        dev.connections.defaults = AttrDict()
        dev.connections.defaults['class'] = Unicon
        dev.connections.cli = AttrDict()
        #dev.connections.cli.protocol = protocol
        #dev.connections.cli.ip = "%s" % address
        #if port is not None:
        #    dev.connections.cli.port = port

        dev.connections.cli.command = "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no " + username + "@" + str(address) + " -p " + str(port)

        self.testbed.add_device(dev)

    @keyword('add devices "${devices}" from NSO "${nso}" to testbed with credentials "${username}"/"${password}"')
    def add_devices_from_nso_to_testbed(self, devices, nso, username, password):
        """
        This keyword adds a list of devices (separated by semicolon, ex "router1;router2;router3") which are present
        as devices in NSO to the unicon testbed so you can interact with them using the unicon methods.
        Please refer to the keyword 'add device '${devices}" from NSO "${nso}" to testbed with credentials "${username}"/"${password}"'
        for details.
        """
        device_list = [d.strip() for d in devices.split(';')]
        for device in device_list:
            self.add_device_from_nso_to_testbed(device, nso, username, password)

    @keyword(u'retrieve latest NSO rollback number from "${nso}"')
    def nso_retrieve_rollback_number(self, nso):
        """
        This keyword retrieves the last rollback number from NSO's rollback history,
        returns it and also stores it internally within the library so you can
        roll back to this revision using the 'rollback NSO "nso" to rollback retrieved'
        later-on.

        This keyword can be used as part of a "Test Setup" keyword to automatically collect
        the number for later rollback in "Test Teardown".
        """
        device = self.testbed.devices[nso]
        cli_style = device.state_machine.current_cli_style
        if re.match(r"[Cc]", cli_style):
            command = "show configuration commit list"
        else:
            command = "show commit list"
        output = device.execute(command, timeout=self.timeout)
        for line in output.splitlines():
            m = re.match(r"\d+\s+(\d+)", line)
            if m is not None:
                self.latest_rollback = int(m.group(1))
                print("Latest rollback number set to %s" % self.latest_rollback)
                break
        else:
            raise RastaException("Can't retrieve rollback number")

        return self.latest_rollback

    @keyword(u'rollback NSO "${nso}" to rollback "${number}"')
    def rollback_nso_to_rollback_number(self, nso, number):
        """
        This keyword rolls back the NSO ${nso} to the rollback number provided as argument.
        Please also see "retrieve latest NSO rollback number from.." and "rollback NSO .. to rollback retrieved"
        """
        device = self.testbed.devices[nso]
        cli_style = device.state_machine.current_cli_style
        if re.match(r"[Cc]", cli_style):
            command = "rollback configuration " + str(number)
        else:
            command = "rollback " + str(number)
        output = device.configure(command, timeout=self.timeout)
        # configure command currently doesn't catch all errors, so do this manually here
        if re.match(r"Error: invalid rollback number", output) is None:
            raise RastaException("Error: invalid rollback number")

    @keyword(u'rollback NSO "${nso}" to rollback retrieved')
    def rollback_nso_to_rollback_retrieved(self, nso):
        """
        This keyword rolls back the NSO ${nso} to the rollback number retrieved earlier.

        This keyword can be used as part of a "Test Teardown" keyword to automatically rollback
        NSO.
        """

        if self.latest_rollback is None:
            raise RastaException("Error: latest rollback number wasn't collected")
        else:
            number = self.latest_rollback + 1

        device = self.testbed.devices[nso]
        cli_style = device.state_machine.current_cli_style
        if re.match(r"[Cc]", cli_style):
            command = "rollback configuration " + str(number)
        else:
            command = "rollback " + str(number)
        output = device.configure(command, timeout=self.timeout)
        # configure command currently doesn't catch all errors, so do this manually here
        if re.match(r"Error: invalid rollback number", str(output)) is not None:
            raise RastaException("Note: No changes were made since then")
