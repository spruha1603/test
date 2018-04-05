
import re

import time
import sys
import traceback
from datetime import timedelta

from ats import topology
from ats.async import pcall
from ats.datastructures import AttrDict
from unicon.plugins.generic import GenericSettings

from robot.api.deco import keyword
from robot.libraries.BuiltIn import BuiltIn
from robot.api import logger



class RastaUnicon(object):
    """ RASTA python library for Unicon keywords

    This class uses the pyATS 'unicon' library to interact with the CLI of devices.

    More info on the unicon library: http://wwwin-pyats.cisco.com/cisco-shared/unicon/latest/user_guide/index.html

    """
    ROBOT_LIBRARY_SCOPE = "GLOBAL"
    ROBOT_LIBRARY_DOC_FORMAT = 'reST'

    def __init__(self):
        self.command_output = {}
        self.last_command_output = None
        self.timeout = 60

    @keyword(u'use testbed "${testbed}"')
    def use_testbed(self, testbed=None):
        """ Load testbed YAML file and instantiate testbed object """
        self.testbed = topology.loader.load(testbed)

    @keyword(u'connect to all devices')
    def connect_to_all_devices(self):
        """ Connect to all devices via 'cli' """
        devices = self.testbed.devices
        for dev in devices:
            logger.info("Connect to device {}".format(dev))
            devices[dev].connect(mit=True, via='cli')

    @keyword(u'connect to device "${device}"')
    def connect_to_device(self, device=None):
        """ Connect to device via 'cli' connection as defined in testbed.yaml """
        devices = self.testbed.devices
        # on some devices, pyats/unicon makes some config changes
        # like changing exec timeout, logging, etc.
        # There is currently no generic way to disable this.
        devices[device].connect(mit=True, via='cli')

    @keyword(u'connect via "${via}" to device "${device}"')
    def connect_via_to_device(self, via, device):
        """ Connect to a device via non-default (default: 'cli') method. """
        devices = self.testbed.devices
        devices[device].connect(mit=True, via=via)

    @keyword(u'connect to devices "${devs}"')
    def connect_to_devices(self, devs=None):
        """ Connect to devices via 'cli'. Specify devices with semi-colon separated list, e.g. "R1;R2" """
        devs = devs.split(';')
        devices = self.testbed.devices
        for dev in devs:
            devices[dev].connect(mit=True, via='cli')

    @keyword('set command timeout to "${timeout}" seconds')
    def set_command_timeout(self, timeout=None):
        """ Set the default timeout for commands """
        self.timeout = timeout
        logger.info("Command execution timeout set to {} seconds".format(timeout))

    @keyword('set current device to "${device}"')
    def set_current_device(self, device=None):
        """ Set the current device. With the current device set you can use the 'execute "command"' keyword """
        self.current_device = device

    @keyword(u'execute "${command}"')
    def execute_command(self, command=None):
        """ Execute a command on the current device. Set the current device using the 'set current device to "device"' keyword"""
        if not hasattr(self, 'current_device'):
            raise AttributeError("Current device not set, use 'set current device to \"device\"' to set active device")
        device = self.current_device
        return self.execute_command_on_device(command, device)

    @keyword(u'execute command "${command}" on device "${device}"')
    def execute_command_on_device(self, command=None, device=None):
        """ Execute a CLI command on a specific device """
        devices = self.testbed.devices
        rtr = devices[device]
        logger.info("Executing command {} on device {}".format(command, device))
        output = rtr.execute(command, timeout=self.timeout)
        try:
            self.command_output[device][command] = output
        except KeyError:
            self.command_output[device] = {}
            self.command_output[device][command] = output
        return output

    @keyword('execute command "${command}" on devices "${devices}"')
    def execute_command_on_devices(self, command=None, devices=None):
        """ Execute the same command on multiple devices. Devices is a ';' separated list of devices, e.g. "R1;R2" """
        devices = devices.split(';')
        for device in devices:
            self.execute_command_on_device(command, device)

    @keyword('execute command "${command}" in parallel on devices "${devices}"')
    def execute_commands_in_parallel_on_device(self, command=None, devices=None):
        """ Execute a command in parallel on multiple devices. Devices is a ';' separated list of devices, e.g. "R1;R2" """
        devices = devices.split(';')
        res = pcall(self.execute_command_on_device, command=[command] * len(devices), device=devices)

    @keyword(u'configure device "${device}" with config "${config}"')
    def configure_device(self, device, config):
        """ Execute 'configure' service against the device CLI """
        logger.info("Configure command {} on device {}".format(config, device))
        devices = self.testbed.devices
        dev = devices[device]
        dev.configure(config)

    @keyword(u'disconnect from all devices')
    def disconnect(self):
        """ Disconnect the CLI sessions for all devices """
        devices = self.testbed.devices
        for dev in devices:
            devices[dev].disconnect()

    @keyword('check command "${command}" from device "${device}" for regex "${regex}"')
    def check_command_output(self, command, device, regex):
        """ Execute command on the device and check if the returned output matches the provided regex.
        If the regex does not match, raise an AssertionError """
        output = self.command_output[device][command]
        assert re.search(regex, output), "%s not found in output" % regex

    @keyword('store command "${command}" from device "${device}" as "${variable}"')
    def store_command_in_variable(self, command, device, variable):
        """ Store the output from the CLI command in a variable """
        output = self.command_output[device][command]
        BuiltIn().set_suite_variable('${%s}' % variable, output)

    @keyword('send control caret to device "${device}"')
    def send_control_caret(self, device):
        """ Send Ctrl-^ to the device """
        CTRL_CARET = "\x1e"
        dev = self.testbed.devices[device]
        dev.send(CTRL_CARET)

    @keyword('send command "${command}" to device "${device}"')
    def send_command_to_device(self, command=None, device=None):
        """ Send a command string (without RETURN) to the device """
        dev = self.testbed.devices[device]
        dev.send(command)

    @keyword('set cli style to "${style}" on device "${device}"')
    def set_cli_style_on_device(self, style=None, device=None):
        """ (For NSO) set the CLI style to 'cisco' or 'juniper' """
        dev = self.testbed.devices[device]
        dev.cli_style(style)

  
