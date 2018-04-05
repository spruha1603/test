#rasta generate-docs ignore

from robot.api import logger
from robot.libraries.BuiltIn import BuiltIn


class RastaException(Exception):
    """
    We introduce our own exception which python methods can raise
    to enable a user-configurable behaviour of continuing the
    execution of subsequent keywords within a testcase.
    By default, execution will stop.
    """

    def __init__(self, message):
        val = BuiltIn().get_variable_value('${RASTA_CONTINUE_ON_FAILURE}')

        # val = 'false'
        if str(val).lower() == 'true':
            logger.info("setting ROBOT_CONTINUE_ON_FAILURE")
            self.ROBOT_CONTINUE_ON_FAILURE = True
        else:
            self.ROBOT_CONTINUE_ON_FAILURE = False
