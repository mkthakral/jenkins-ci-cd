package com.gy

import com.gy.utility.CommandExecutor


def runUnitTest(Map params)
{
    def environment = params.environment
    def runJUnitTenantInit = params.runJUnitTenantInit
    def createJUnitReport = params.createJUnitReport
    def excludedPackage = params.excludedPackage
    def includedPackage = params.includedPackage
    def includedExtensions = params.includedExtensions

    String receivedArguments = "Environment: "+environment+ "\n Run Junit Init :"+runJUnitTenantInit+ "\n Create JUnit Test Report: "+createJUnitReport +"\n Excluded Package: "+excludedPackage+"\n Included Package: "+includedPackage +"\n Included Extensions: "+includedExtensions
    println(receivedArguments)

    //properties from ${environment}.property
    def properties = readPropertyFile(environment)
    String HYBRIS_HOME =  properties['HYBRIS_INSTALL_PATH']
    String JAVA_HOME = properties['JAVA_HOME']
    String ANT_INSTANCE_NAME = properties['LOCAL_ANT_INSTANCE_NAME']

    println("Setting HYBRIS_HOME to : "+HYBRIS_HOME)
    println("Setting JAVA_HOME to : "+JAVA_HOME)
    println("Setting ANT_INSTANCE_NAME to : "+ANT_INSTANCE_NAME)

    if (runJUnitTenantInit)
    {
        def unitTestInit = new unitTestInit()
        unitTestInit.initializeJUnit(HYBRIS_HOME,JAVA_HOME)
    }
    else
    {
        println("Skipping JUnit Init")
    }

    prepareCommand(HYBRIS_HOME,JAVA_HOME, ANT_INSTANCE_NAME,excludedPackage, includedPackage,includedExtensions)


    if ((createJUnitReport != null) && createJUnitReport)
    {
        def testResult = new junitTestResult()
        testResult.getUnitTestRsult(HYBRIS_HOME)
    }
    else
    {
        println("Skipping Jacoco Unit Test Report creation")
    }

}


def readPropertyFile(String environment)
{
    def propFileContent = libraryResource environment + '.properties'
    println(propFileContent)
    def properties = readProperties text: propFileContent
    return properties
}

def prepareCommand(String HYBRIS_HOME, String JAVA_HOME, String ANT_INSTANCE_NAME, String junitPackageExcluded, String junitPackageIncluded, String includedExtensions)
{
    //Setting up the ant environment
    println("Hybris Home : "+ HYBRIS_HOME)
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

    //Running the ant command
    String antCommand = "ant unittests"
    if ((junitPackageExcluded != null) && !(junitPackageExcluded.isEmpty()))
    {
        antCommand = antCommand + " -Dtestclasses.packages.excluded=\""+junitPackageExcluded+"\""
    }
    if ((junitPackageIncluded != null) && (!(junitPackageIncluded.isEmpty())))
    {
        antCommand = antCommand + " -Dtestclasses.packages=\""+junitPackageIncluded + "\""
    }
    else if ((includedExtensions != null) && (!(includedExtensions.isEmpty())))
    {
        antCommand = antCommand + " -Dextensions=\""+includedExtensions+ "\""
    }

    dir(HYBRIS_HOME + "/hybris/bin/platform")
    {
        withAnt(installation: ANT_INSTANCE_NAME) {
            commandExecutor.execute(antCommand)
        }
    }

}
