package com.mkthakral

import com.mkthakral.utility.PropertyFileReader
import com.mkthakral.utility.CommandExecutor
import org.apache.commons.io.FileUtils

def WORKSPACE
String HYBRIS_INSTALL_PATH
String DEPLOY_BRANCH
String ANT_INSTANCE_NAME

def buildHybrisCodePackage(Map params)
{
    initializeProperties(params)

    runAntProduction()

    reallocateCodeArtifacts()

}

def initializeProperties(params)
{
    println("#################  Initializing Code Build Packaging Properties  ################")
    def propertyReader = new PropertyFileReader()
    def properties = propertyReader.readPropertyFile("common")

    HYBRIS_INSTALL_PATH =  properties['HYBRIS_INSTALL_PATH']
    ANT_INSTANCE_NAME = properties['LOCAL_ANT_INSTANCE_NAME']
    WORKSPACE = params.workspace
    DEPLOY_BRANCH = params.branch
}


def runAntProduction()
{
    boolean includeTomcat = params.includeTomcat
    boolean tomcatLegacyDeployment = params.tomcatLegacyDeployment

    def command = ""
    def commandExecutor = new CommandExecutor()
    String changePath =  ("cd " +HYBRIS_INSTALL_PATH+"/hybris/bin/platform")
    command = command + changePath

    switch(commandExecutor.getOS()){
        case "windows" :
            command = command + "\n" + "setantenv.bat"

            break;
        default :
            command = command + "\n" + ". ./setantenv.sh"
    }

    commandExecutor.execute(command)

    String antBuildCommand = "ant production -Dproduction.legacy.mode=false"
    if((includeTomcat != null) && includeTomcat)
    {
        antBuildCommand = antBuildCommand + " -Dproduction.include.tomcat=true"
    }
    if ((tomcatLegacyDeployment != null) && tomcatLegacyDeployment)
    {
        antBuildCommand = antBuildCommand + " -Dtomcat.legacy.deployment=true"
    }
    dir(HYBRIS_INSTALL_PATH + "/hybris/bin/platform")
    {
        withAnt(installation: ANT_INSTANCE_NAME)
        {
             commandExecutor.execute(antBuildCommand)
        }
    }

}


def reallocateCodeArtifacts()
{
    String ZIP_SOURCE_DIR = HYBRIS_INSTALL_PATH + "/hybris/temp/hybris/hybrisServer"
    String allExtensionsZip = ZIP_SOURCE_DIR + "/hybrisServer-AllExtensions.zip"
    String allLicenceZip = ZIP_SOURCE_DIR + "/hybrisServer-Licence.zip"
    String allPlatformZip = ZIP_SOURCE_DIR + "/hybrisServer-Platform.zip"

    renameBuildPackages(allExtensionsZip,"hybrisServer-AllExtensions")
    renameBuildPackages(allLicenceZip,"hybrisServer-Licence")
    renameBuildPackages(allPlatformZip,"hybrisServer-Platform")
}


def renameBuildPackages(String srcFileName,String destFileName)
{
    today = new Date().format("yyyyMMdd_HHmmss")

    String artifactName = destFileName + "-" + DEPLOY_BRANCH + ".zip"
    String artifactPath = WORKSPACE + "/artifacts/hybriscode/" + BUILD_NUMBER + "/" + artifactName

    println("Generating artifact  :"+artifactName+ "  in : " + artifactPath)

    //printf("Generating artifact for Build No:%d Deployment Branch: %s", BUILD_NUMBER,DEPLOY_BRANCH)
    File srcFile = new File(srcFileName)
    if (srcFile.exists()) {
        FileUtils.copyFile(srcFile, new File(artifactPath))
    }
    else
    {
        println ("Artifact not exists for build number:"+BUILD_NUMBER+" while deploying branch :"+DEPLOY_BRANCH)
    }
}