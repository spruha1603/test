# What is RASTA

RASTA is a Test Automation Framework that is used to perform system, integration and acceptance testing of AS Automation Software projects.
The initial motivation for creating RASTA is to provide a common way to automatically execute system and acceptance testing for NSO projects.

RASTA is based on Robotframework and enhances is through keyword and python libraries targeted for the use cases we are involved in.
 

This repository contains the following info:

**Documentation** (still very much work in progress) in  [docs/](docs/), please refer to the [docs/README.md](docs/README.md) for a first overview and installation instructions. We also try to capture guidance on creating test cases, working with robot/etc. in this space.

**Examples** leveraging the RASTA libraries are constantly being added in [examples/](examples/) and can serve as a first start to see RASTA and Robot in action. We recommend checking those out first.

**Libraries**: The main benefit RASTA delivers are the Robot keyword and associated python libraries that AS engineers have been creating to implement common test routines. They can be augmented by project-specific libraries, and if they are of wider use, they should be merged with the RASTA libraries so they can be made available for a wider audience.
The directory [lib](lib/) contains both python ([lib/py](lib/py)) as well as Robot keyword ([lib/robot](lib/robot)) libs. Lib documentation is included in [lib/doc](lib/doc).  
Please ```source env.sh``` to set your PYTHONPATH include them.  

The libs are still being worked on and refined, so some of the keywords might and will change. Please reach out to <rasta-admins@cisco.com> or join our RASTA Architecture Development spark room through <https://eurl.io/#Sy2-N5WPW>  

**Installation files** are included in [environments/](environments/) and [ansible/](ansible/), please refer to the README.md files in those directories

**Playground**: As the name implies this directory contains some sample code folks are playing around with. Those files don't necessarily work, but we would invite you to play around there for others' benefits.



  

 
