package com.mkthakral

import com.mkthakral.utility.CommandExecutor


def initializeJUnit(String HYBRIS_HOME,String JAVA_HOME)
{
    println("START : JUnit Init")
    def properties = readPropertyFile(environment)
    prepareCommand(HYBRIS_HOME,JAVA_HOME)
}


def readPropertyFile(String environment)
{
    def propFileContent = libraryResource 'local.properties'
    println(propFileContent)
    def properties = readProperties text: propFileContent
    return properties
}

def prepareCommand(String HYBRIS_HOME, String JAVA_HOME)
{
    def command = ""
    def commandExecutor = new CommandExecutor()
    String changePath =  ("cd " +HYBRIS_HOME+"/hybris/bin/platform")
    command = command + changePath

    switch(commandExecutor.getOS()){
        case "windows" :
            command = command + "\n" + "set JAVA_HOME = " + JAVA_HOME
            command = command + "\n" + "setantenv.bat"

            break;
        default :
            command = command + "\n" + "export JAVA_HOME = " + JAVA_HOME
            command = command + "\n" + "./setantenv.sh"
    }

    commandExecutor.execute(command)
    String antJunitInitCommand = "ant yunitinit -Dmaven.update.dbdrivers=false"
    println("Initializing JUnit Tenants with : " + antJunitInitCommand);

    dir(HYBRIS_HOME + "/hybris/bin/platform")
    {
        withAnt(installation: 'hybrisAnt')
        {
           commandExecutor.execute(antJunitInitCommand)
        }
    }
}
