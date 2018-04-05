

from rasta_unicon import RastaUnicon
from rasta_nso import RastaNSO
from rasta_nso_rest import RastaNSORest
from rasta_browser import RastaBrowser
from rasta_ccdiff import RastaCiscoConfDiff

from robot.api.deco import keyword
from robot.api import logger
from robot.libraries.BuiltIn import BuiltIn
from rasta_exception import RastaException

class RASTA(RastaUnicon, RastaNSO, RastaNSORest, RastaBrowser, RastaCiscoConfDiff):
    """
    This is the overall RASTA Python class which is meant to keep state
    of objects which are to be shared across different test library classes.

    For now, the main purpose is to keep a global "testbed" data structure which
    is meant to contain device information like addresses/credentials/etc.
    """

    ROBOT_LIBRARY_SCOPE = "GLOBAL"

    testbed = None

    @keyword(u'Rasta continue on failure')
    def rasta_set_continue_on_failure(self, value=True):
        """By default, Robot/Rasta stops the execution of keywords in a testcase
        once a failure occurs. There are some RASTA use cases, where this
        behaviour is not desired, for example when a test involves the comparison
        of multiple devices, and the comparison of all devices should be done
        even if one of the comparison steps fails.

        ROBOT has a "Run Keyword And Continue On Failure" keyword to achieve this,
        but for the RASTA core lib keywords implemented in the python libs we are providing 
        a method to switch this behaviour on. The behaviour is enabled on a global level,
        so it applies for all subsequent keywords until it is disabled::

          Rasta Continue on Failure   
            or  
          Rasta Continue on Failure   True

        To disable, use::

          Rasta Continue on Failure   False

        This sample test suite shows the use of this::

           *** Test Cases ***
           Test 1
               Failing RASTA keyword
               Log    This line is not logged
     
           Test 2
               Rasta continue on failure
               Failing RASTA keyword
               Log    This line is logged as we are continuing on failure
           
           Test 3
               Failing RASTA keyword
               Log    This line is logged, continuing on failure is set on a global level

           Test 4
               Rasta continue on failure   False
               Failing RASTA keyword
               Log    This line is not logged

        To use this method in your own python library, you can do::

           from rasta_exception import RastaException
        
        and raise RastaException("errormessage") when you want to fail a
        testcase (instead of raising an AssertionError or a different 
        exception).
        """

        # In case you are wondering why we implemented this keyword in python
        # instead of using a keyword in a resource library: Documentation is much easier
        # for python-defined kewywords :-/

        if(str(value).lower() == 'true'):
            BuiltIn().set_global_variable("${RASTA_CONTINUE_ON_FAILURE}", "True")
        else:
            BuiltIn().set_global_variable("${RASTA_CONTINUE_ON_FAILURE}", "False")


    def Failing_rasta_keyword(self):
        """This keyword is used to raise a RastaException to test continue 
        on failure, it serves no other purpose """
        raise RastaException("this keyword just fails")
