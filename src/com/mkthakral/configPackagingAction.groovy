package com.mkthakral

import com.mkthakral.utility.PropertyFileReader
import org.apache.commons.io.FileUtils
import com.mkthakral.utility

def HYBRIS_INSTALL_PATH
def WORKSPACE
String DEPLOY_BRANCH
def targetEnvironment
def checkoutDir


def deployConfigPackage(Map params)
{
    initializeProperties(params)
    //createConfigArtifact()
    zipAllConfigDir()
}

def initializeProperties(params)
{
    def propertyReader = new PropertyFileReader()
    def properties = propertyReader.readPropertyFile("common")
    targetEnvironment = params.targetEnvironment

    HYBRIS_INSTALL_PATH =  properties['HYBRIS_INSTALL_PATH']
    WORKSPACE = params.workspace
    DEPLOY_BRANCH = params.branch
    String alternateCheckoutDir = properties['ALTERNATE_WORKSPACE_DIR']

    checkoutDir = utility.getCheckoutDir(WORKSPACE, alternateCheckoutDir)
    println("**********  Setting Global Properties *********")
    //Echo Statements
    printf "checkoutDir: " + checkoutDir
    println("HYBRIS_INSTALL_PATH = "+ HYBRIS_INSTALL_PATH)
    println("Target Environment : "+targetEnvironment)
}

private void createConfigArtifact()
{
    today = new Date().format("yyyyMMdd_HHmmss")
    def dirToBeZipped

    if ((targetEnvironment != null)  &&  (targetEnvironment != "local"))
    {
        String targetEnv = targetEnvironment.toString()
        if (targetEnv.equalsIgnoreCase("prod"))
        {
            String configFileSuffix = "prod-cluster1"
            println("Zipping directory : " + checkoutDir + "/config-"+configFileSuffix)
            dirToBeZipped = checkoutDir + "/config-"+configFileSuffix
        }
        else if (targetEnv.equalsIgnoreCase("dr"))
        {
            String configFileSuffix = "dr-cluster1"
            println("Zipping directory : " + checkoutDir + "/config-"+configFileSuffix)
            dirToBeZipped = checkoutDir + "/config-"+configFileSuffix
        }
        else if (targetEnv.equalsIgnoreCase("stage"))
        {
            String configFileSuffix = "stage-cluster1"
            println("Zipping directory : " + checkoutDir + "/config-"+configFileSuffix)
            dirToBeZipped = checkoutDir + "/config-"+configFileSuffix
        }
        else if (targetEnv.equalsIgnoreCase("test"))
        {
            String configFileSuffix = "test-cluster1"
            println("Zipping directory : " + checkoutDir + "/config-"+configFileSuffix)
            dirToBeZipped = checkoutDir + "/config-"+configFileSuffix
        }
        else {
            env = targetEnvironment.toString().toLowerCase()
            println("Zipping directory : " + checkoutDir + "/config-" + env)
            dirToBeZipped = checkoutDir + "/config-" + env
        }
    }
    else
    {
        println("Zipping directory : " + checkoutDir + "/config")
        dirToBeZipped = checkoutDir + "/config"
    }
    def configArtifact = WORKSPACE+"/artifacts/configs/${BUILD_NUMBER}/config-"  + targetEnvironment + "-" + DEPLOY_BRANCH + "-" + today + ".zip"
    try {
        zip zipFile: "$configArtifact", archive: true, overwrite: true, dir:"$dirToBeZipped"
    }
    catch (final IOException e)
    {
        println("No previous version of config artifacts exists. Creating new config artifact ")
        zip zipFile: "$configArtifact", archive: true, overwrite: false, dir:"$dirToBeZipped"
    }

}



def zipAllConfigDir()
{
    today = new Date().format("yyyyMMdd_HHmmss")
    def dirToBeZipped = WORKSPACE+"/tempConfigs"
    final File tempConfigs = new File(dirToBeZipped)
    if (tempConfigs.exists())
    {
        FileUtils.deleteDirectory(tempConfigs)
    }
    final File checkoutDirectory = new File(checkoutDir)
    if (checkoutDirectory.exists() && checkoutDirectory.isDirectory())
    {
        final File[] allCheckoutDirFiles = checkoutDirectory.listFiles()
        for (checkoutDirFile in allCheckoutDirFiles)
        {
            if (checkoutDirFile.isDirectory())
            {
                String checkoutDirFileName = checkoutDirFile.getName()
                if (checkoutDirFileName.startsWith("config"))
                {
                    println("File name : "+checkoutDirFileName)
                    FileUtils.copyDirectoryToDirectory(checkoutDirFile,tempConfigs)
                }
            }
        }
        def configArtifact = WORKSPACE+"/artifacts/configs/${BUILD_NUMBER}/config-" + DEPLOY_BRANCH + ".zip"
        try {
            zip zipFile: "$configArtifact", archive: true, overwrite: true, dir:"$dirToBeZipped"
        }
        catch (final IOException e)
        {
            zip zipFile: "$configArtifact", archive: true, overwrite: false, dir:"$dirToBeZipped"
        }
    }
    else
    {
        println("########## Skipping Config artifact build. Reason: Checkout Dir does not exists  ##########")
    }
    try {
        println("Deleting Temp Config directory... " + dirToBeZipped)
        FileUtils.deleteDirectory(tempConfigs)
    }
    catch (Exception e)
    {
        println(" Temp Config directory... " + dirToBeZipped + "  does not exists. Skipping its deletion")
    }
}