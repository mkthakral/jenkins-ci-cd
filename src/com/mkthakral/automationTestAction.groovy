package com.mkthakral
import com.mkthakral.utility.CommandExecutor
import com.mkthakral.utility.PropertyFileReader
import org.apache.commons.io.FileUtils

def HYBRIS_HOME
def JAVA_HOME
def TEST_ENVIRONMENT
def WORKSPACE
def REPORTS_DIR

def runTests(Map params)
{
    initializeProperties(params)
    cloneTestRepository(params)
    deleteLastReport()
    runAutomationTest(params)
    copyReportToCommonDir()
}

def initializeProperties(params)
{
    println("#################  Initializing Automation Test Properties  ################")
    TEST_ENVIRONMENT = params.testEnvironment
    def propertyReader = new PropertyFileReader()
    def properties = propertyReader.readPropertyFile("common")
    HYBRIS_HOME =  properties['HYBRIS_INSTALL_PATH']
    JAVA_HOME = properties['JAVA_HOME']
    WORKSPACE = params.workspace
    REPORTS_DIR = properties['REPORTS_DIR']
}

def deleteLastReport()
{
    println("################   REMOVING EXISTING REPORTS  ################")
    File dir = new File(WORKSPACE + "/reports/")
    dir.deleteDir()
}


def cloneTestRepository(params)
{

    //Variables
    def branch = params.branch
    def repoURL = params.repoURL
    def credentialsId = params.credentialsId
    //Echo Statements
    printf "checkoutDir: " + workspace
    printf "branch: " + branch
    printf "repoURL: " + repoURL

    //Checkout Repository
    dir(WORKSPACE) {
        checkout([
                $class: 'GitSCM',
                branches: [[name:  branch ]],
                userRemoteConfigs: [[ credentialsId: credentialsId,  name: 'automationTestRepo', url: repoURL ]]
        ])
    }
}

def runAutomationTest(params)
{
    String command = "mvn clean test"
    String testEnvironment
    
    if ((TEST_ENVIRONMENT == null) || (TEST_ENVIRONMENT.toString().equalsIgnoreCase("local")))
    {
        println("Test environment passed is either NULL or local, so setting Test environment to default: TEST")
        testEnvironment = "TEST"
    }
    else
    {
        testEnvironment = TEST_ENVIRONMENT.toString().toUpperCase()
    }

    command = command + " -Denv="+testEnvironment

    if (params.testTags != null)
    {
        command = command + " -Dtags=" + params.testTags
    }
    if (params.threads != null)
    {
        command = command + " -Dthreads=" + params.threads
    }
    if (params.browsers != null)
    {
        command = command + " -Dbrowser=" + params.browsers
    }
    println("################   START AUTOMATION TESTING  ################")
    println("Running ant test command : " + command)
    dir(WORKSPACE)
    {
        withMaven(maven: 'LocalMaven')
        {
            CommandExecutor commandExecutor = new CommandExecutor()
            commandExecutor.execute(command)
        }
    }
}

def copyReportToCommonDir()
{
    File originalReport = new File(WORKSPACE+"/reports")
    final File latestFile

    if (originalReport.exists() && originalReport.isDirectory())
    {
        File[] reportFiles = originalReport.listFiles()
        if (reportFiles.size() > 0)
        {
            latestFile = reportFiles[0]
        }
    }

    try {
        if (latestFile != null) {
            FileUtils.copyFileToDirectory(latestFile, new File(WORKSPACE + REPORTS_DIR + "/"))
        }
        else {
            println("Sanity test report file does not exists in : "+WORKSPACE+"/reports")
        }
    }
    catch (Exception exception)
    {
        println(exception)
        println("Unable to copy sanity file to common reports directory")
    }
}