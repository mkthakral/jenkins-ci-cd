package com.mkthakral

import com.mkthakral.utility.PropertyFileReader
import org.apache.commons.io.FileUtils
import com.mkthakral.utility.CommandExecutor

def WORKSPACE
String TARGET_ENVIRONMENT
def TARGET_CLUSTER
def TARGET_ARTIFACT_REPO_DIR
def TARGET_HYBRIS_DIR
boolean runAntInitialize
String ANT_INSTANCE_NAME
String DOMAIN_NAME
String DOMAIN_PROTOCOL

def deploy(Map params)
{
    initializeProperties(params)
    cleanTargetRepoDir()
    copyArtifactsToTargetEnv()
    unzipArtifactsOnTargetEnv()
    renameConfigFile()
    startHybrisServer()
}


def initializeProperties(params)
{
    println("**********  Setting Global Properties *********")

    WORKSPACE = params.workspace
    TARGET_ENVIRONMENT = params.targetEnvironment
    TARGET_CLUSTER = params.targetCluster
    runAntInitialize = params.runAntInitialize

    if ((TARGET_ENVIRONMENT == null) || TARGET_ENVIRONMENT.toString().isEmpty())
    {
        throw new RuntimeException("ERROR : targetEnvironment not entered.")
    }
    def propertyReader = new PropertyFileReader()
    def properties = propertyReader.readPropertyFile(TARGET_ENVIRONMENT.toString())
    TARGET_ARTIFACT_REPO_DIR = properties['TARGET_ARTIFACT_REPO_DIR']
    TARGET_HYBRIS_DIR = properties['TARGET_HYBRIS_DIR']
    DOMAIN_NAME = properties['DOMAIN_NAME']
    DOMAIN_PROTOCOL = properties['DOMAIN_PROTOCOL']

    commonProperties = propertyReader.readPropertyFile("common")
    ANT_INSTANCE_NAME = commonProperties['LOCAL_ANT_INSTANCE_NAME']
}

def cleanTargetRepoDir()
{
    File artifactDestinationDir = new File(TARGET_ARTIFACT_REPO_DIR)
    //destinationDirDeleted = artifactDestinationDir.deleteDir()
    if (artifactDestinationDir.exists()) {
        println("Removing target repo dir : "+ artifactDestinationDir)
        FileUtils.deleteDirectory(artifactDestinationDir)
    }
    else {
        println("Repo dir does not exists in target environment. Skipping the deletion")
    }
}


def copyArtifactsToTargetEnv()
{
    println("******************************************************************* \n****************** COPYING ARTIFACTS TO "+TARGET_ENVIRONMENT+"  ******************\n*******************************************************************")

    //TODO: Change code to transfer on remote server, instead of current setup of copy-paste within same system(Jenkins server)
    File artifactSourceDir = new File(WORKSPACE + "/deployable")
    File artifactDestinationDir = new File(TARGET_ARTIFACT_REPO_DIR)
    if (artifactSourceDir.exists() && (artifactSourceDir.listFiles().size() > 0))
    {
        FileUtils.copyDirectory(artifactSourceDir, artifactDestinationDir);
    }
    else if (!(artifactSourceDir.exists())){
        println("Artifact source dir does not exist. Please check for dir:  "+artifactSourceDir)
        throw new RuntimeException("Artifact does not exist on Jenkins workspace. Please check Jenkins workspace for dir:  "+artifactSourceDir)
    }
    else if ((artifactSourceDir.listFiles().size() == 0))
    {
        println("No artifact file found in : "+ artifactSourceDir)
        throw new RuntimeException("No artifact file found in Jenkins workspace artifact dir : "+ artifactSourceDir)
    }
    else {
        println("ERROR : Cannot copy artifact file to target environment.")
        throw new RuntimeException("ERROR : Cannot copy artifact file to target environment.")
    }
}


def unzipArtifactsOnTargetEnv()
{
    File[] artifactsList = new File(TARGET_ARTIFACT_REPO_DIR).listFiles()
    String finalArtifactName
    boolean parentArtifactExtractionSuccess
    //extracting the parent artifact copied from Jenkins server. Name follows the pattern : artifact_master-20210106_110156.zip
    //This parent artifact zip file contains multiple zip files.
    for (artifact in artifactsList)
    {
        println("FileName : "+artifact.getName())
        if ( artifact.getName().startsWith("artifact_")) {
            finalArtifactName = artifact.getName()
            String artifactPathName = TARGET_ARTIFACT_REPO_DIR + "/" + finalArtifactName
            println("Extracting parent artifact file : " + artifactPathName + " at " + TARGET_ARTIFACT_REPO_DIR)
            unzip zipFile: "$artifactPathName", dir: "$TARGET_ARTIFACT_REPO_DIR"
            parentArtifactExtractionSuccess = true
        }
        else {
            println("Parent artifact not found. Check the target repo dir for file name starting with : artifact_*")
            throw new RuntimeException("Parent artifact not found. Check the target repo dir for file name starting with : artifact_*")
        }
    }

    //Checking if parent zip was extracted or not
    if (parentArtifactExtractionSuccess)
    {

        //Deleting the parent zip file, after extracting its content.
        new File(TARGET_ARTIFACT_REPO_DIR + "/" + finalArtifactName).delete()
        println("Deleting the parent artifact zip file : "+ TARGET_ARTIFACT_REPO_DIR + "/" + finalArtifactName)

        //List of zip files after deleting parent artifact file
        File[] newArtifactsList = new File(TARGET_ARTIFACT_REPO_DIR).listFiles()

        println("******************************************************************* \n****************** EXTRACTING ARTIFACTS TO "+TARGET_ENVIRONMENT+"  ******************\n*******************************************************************")

        //Unzipping the children zip files, eg: configs and allextensions.
        for (artifact in newArtifactsList) {
            String artifactPathName = TARGET_ARTIFACT_REPO_DIR + "/" + artifact.getName()
            String destinationPath = TARGET_HYBRIS_DIR

            if (artifact.getName().contains("config"))     {
                destinationPath = TARGET_HYBRIS_DIR + "/hybris/"
            }
            println("Extracting artifact file : " + artifactPathName + " at " + destinationPath)
            unzip zipFile: "$artifactPathName", dir: "$destinationPath"
        }
    }
    else {
        println("SKIPPING DEPLOYMENT. Reason : Artifact copied from Jenkins server, either not found or not extracted in : " + TARGET_ARTIFACT_REPO_DIR)
        throw new RuntimeException("Artifact copied from Jenkins server, either not found or not extracted in " + TARGET_ARTIFACT_REPO_DIR)
    }
}


def removeExistingConfigDir()
{
    String configDirName = "config"
    //Check if config dir already exists or not. If exists, delete it.
    println("Removing the existing config directory.")
    FileUtils.deleteDirectory(new File(TARGET_HYBRIS_DIR + "/hybris/config"))
}


def renameConfigFile()
{
    removeExistingConfigDir()
    String configDirName = "config"

    if (TARGET_ENVIRONMENT != null)
    {
        if (TARGET_ENVIRONMENT.toString().toLowerCase().equals("local"))
        {
            configDirName = configDirName + "-" + "macos"
        }
        else {
            configDirName = configDirName + "-" + TARGET_ENVIRONMENT.toString().toLowerCase()
        }
    }
    if (TARGET_CLUSTER != null)
    {
        configDirName = configDirName + "-" + TARGET_CLUSTER.toString().toLowerCase()
    }

    println("Renaming the config folder : "+TARGET_HYBRIS_DIR + "/hybris/" + configDirName + "    to 'config'")
    new File(TARGET_HYBRIS_DIR + "/hybris/" + configDirName).renameTo(TARGET_HYBRIS_DIR + "/hybris/" + "config")
}

def startHybrisServer()
{
    String targetEnv = TARGET_ENVIRONMENT.toLowerCase()
    println("Starting Deployment on "+ targetEnv)
    if (targetEnv.contains("dev") && runAntInitialize)
    {
        println("Running ant initialization on "+ targetEnv)
        //TODO: Init requires code change in hybris. Hence, ant init currently not executed.
        //runAntInitOnDev()
    }
    else if (runAntInitialize)
    {
        throw new RuntimeException("Initialization not allowed on : " + TARGET_ENVIRONMENT)
    }

    String hybrisHome = TARGET_HYBRIS_DIR + "/hybris"
    println("Target Hybris Home : "+ hybrisHome)
    def command = ""
    def commandExecutor = new CommandExecutor()
    String changePath =  ("cd " + hybrisHome + "/bin/platform")
    command = command + changePath

    switch(commandExecutor.getOS()){
        case "windows" :
            command = command + "\n" + "set BUILD_ID=dontKillMe"
            command = command + "\n" + "set JENKINS_NODE_COOKIE=dontKillMe"
            command = command + "\n" + "START /B hybrisserver.bat"
            break;
        default :
            command = command + "\n" + "export BUILD_ID=dontKillMe"
            command = command + "\n" + "export JENKINS_NODE_COOKIE=dontKillMe"
            command = command + "\n" + ". ./hybrisserver.sh &"
    }
    commandExecutor.execute(command)

    while(true){
        if(isHybrisServerRunning()){
            println("******************************************************************* \n******************  "+ TARGET_ENVIRONMENT + " HYBRIS SERVER STARTED  ******************\n*******************************************************************")
            break
        }else{
            printf "Waiting for Hybris Server startup. Continue Loop"
            sleep(240000) {
                println("Sleeping")
                false // keep on sleeping
            }
        }
    }
}


private boolean isHybrisServerRunning(){
    hybrisStartTrialCount++
    printf "isHybrisServerRunning() - Checking Hybris server starup for $hybrisStartTrialCount time"
    String homePageURL = DOMAIN_PROTOCOL + "://" + DOMAIN_NAME
    println( " DOMAIN_HOME_PAGE_URL :" + homePageURL)
    def response = ["curl", "-k", "-s", "-o", "/dev/null", "-I", "-w", "%{http_code}", homePageURL].execute().text
    println("Curl response = "+response)
    if(response == "200"){
        printf "Hybris Server is running."
        return true
    }else{
        printf "Hybris server not started yet."
        return false
    }
}


def runAntInitOnDev()
{
    removeExistingConfigDir()

    String hybrisHome = TARGET_HYBRIS_DIR + "/hybris"
    println("Target Hybris Home : "+ hybrisHome)
    def command = ""
    def commandExecutor = new CommandExecutor()
    String changePath =  ("cd " +hybrisHome+"/bin/platform")
    command = command + changePath

    switch(commandExecutor.getOS()){
        case "windows" :
            command = command + "\n" + "setantenv.bat"

            break;
        default :
            command = command + "\n" + ". ./setantenv.sh"
    }

    command = command + "\n" + "ant customize"
    println("EXECUTING COMMAND : "+ command)
    commandExecutor.execute(command)

    String antCommand = "ant clean all initialize"
    dir(TARGET_HYBRIS_DIR + "/hybris/bin/platform")
            {
                withAnt(installation: ANT_INSTANCE_NAME)
                        {
                            commandExecutor.execute(antCommand)
                        }
            }
}