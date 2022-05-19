package com.mkthakral

import com.mkthakral.utility.PropertyFileReader
import org.apache.commons.io.FileUtils

def WORKSPACE
def DEPLOY_BRANCH
def latestCodeBuild
def latestConfigBuild
def archivingOn
def tempDeployables     //temp file having code and config artifacts together to zip. It will be deleted once zipping is complete.
String artifactArchiveDir

def buildArtifact(Map params)
{
    initializeProperties(params)
    findLatestBuildNumber()
    combinetLatestArtifacts()
    zipFinalDeployablePackage()
    sendEmail(params)
}

def initializeProperties(params)
{
    println("**********  Setting Global Properties *********")
    def propertyReader = new PropertyFileReader()
    def properties = propertyReader.readPropertyFile("common")
    archivingOn = properties['archive.artifact']
    artifactArchiveDir = properties['archive.dir']
    WORKSPACE = params.workspace
    DEPLOY_BRANCH = params.branch

    tempDeployables = WORKSPACE + "/temp_deployable"
}



def findLatestBuildNumber()
{
    File codeArtifactFolder = new File(WORKSPACE + "/artifacts/hybriscode/")
    File configArtifactFolder = new File(WORKSPACE + "/artifacts/configs/")

    if (codeArtifactFolder.exists()) {
        if (codeArtifactFolder.isDirectory()) {
            File[] codeBuildDirs = codeArtifactFolder.listFiles()
            ArrayList<Integer> fileNames = new ArrayList<>()
            for (codeBuildDir in codeBuildDirs) {
                if (codeBuildDir.getName().isInteger()) {
                    fileNames.add(Integer.parseInt(codeBuildDir.getName()))
                } else {
                    throw new RuntimeException("Code artifact dir name should be numeric, found : " + codeBuildDir.getName())
                }
            }
            latestCodeBuild = Collections.max(fileNames)
            println("Deploying code artifacts from build number : " + latestCodeBuild)
        }
    }
    else
    {
        println("codeArtifactFolder does not exists : "+codeArtifactFolder)
    }

    if (configArtifactFolder.exists()) {
        if (configArtifactFolder.isDirectory()) {
            File[] configBuildDirs = configArtifactFolder.listFiles()
            ArrayList<Integer> fileNames = new ArrayList<>()
            for (configBuildDir in configBuildDirs) {
                if (configBuildDir.getName().isInteger()) {
                    fileNames.add(Integer.parseInt(configBuildDir.getName()))
                } else {
                    throw new RuntimeException("Config artifact dir name should be numeric, found : " + configBuildDir.getName())
                }
            }
            latestConfigBuild = Collections.max(fileNames)
            println("Deploying config artifacts from build number : " + latestConfigBuild)
        }
    }

    else
    {
        println("configArtifactFolder does not exists : "+configArtifactFolder)
    }
}

def combinetLatestArtifacts()
{
    def srcCodeDir = WORKSPACE + "/artifacts/hybriscode/" + latestCodeBuild
    def srcConfigDir = WORKSPACE + "/artifacts/configs/" + latestConfigBuild
    println("Deploying code artifacts from dir: "+srcCodeDir)
    println("Deploying config artifacts from dir: "+srcConfigDir)

    println("Copying code artifacts from " + srcCodeDir + " to "+tempDeployables)
    println("Copying config artifacts from " + srcConfigDir + " to "+tempDeployables)

    //delete if already exists to remove existing artifacts, if any
    if (new File(tempDeployables).exists())
    {
        println("Deleting Temp deployable dir before overriding")
        FileUtils.deleteDirectory(new File(tempDeployables))
    }
    copyDeploymentFilesToTemp(srcCodeDir)
    copyDeploymentFilesToTemp(srcConfigDir)

}

private void copyDeploymentFilesToTemp(String srcDir){

    File sourceDir = new File(srcDir);

    //String destination = DESTINATION_PARENT_DIR;
    File destDir = new File(tempDeployables)
    try {
        FileUtils.copyDirectory(sourceDir, destDir);
    } catch (IOException e) {
        e.printStackTrace();
    }
}

//This is the final Deployable zip generated having config.zip and hybrisServer-*.zips together
def zipFinalDeployablePackage()
{
    today = new Date().format("yyyyMMdd_HHmmss")

    // Main folder for final artifact
    File deployable = new File(WORKSPACE + "/deployable")
    if (deployable.exists())
    {
        FileUtils.deleteDirectory(deployable)
    }

    //The main single deployable zip file that has to be transfered, unzipped twice and then deployed
    String finalDeployableArtifact = WORKSPACE + "/deployable/artifact_" + DEPLOY_BRANCH + ".zip"

    try {
        zip zipFile: "$finalDeployableArtifact", archive: true, overwrite: false, dir:"$tempDeployables"
        archiveArtifacts allowEmptyArchive: true, artifacts: 'deployable/*.zip', fingerprint: true, followSymlinks: false, onlyIfSuccessful: true

        if (archivingOn != null && archivingOn && artifactArchiveDir != null)
        {
            println("Archiving artifacts in "+ artifactArchiveDir)
            if (new File(artifactArchiveDir + "/artifact_" + DEPLOY_BRANCH + ".zip").exists())
            {
                deleteDirinArchiveFolder()
                new File(artifactArchiveDir + "/artifact_" + DEPLOY_BRANCH + ".zip").delete()
            }
            FileUtils.copyFileToDirectory(new File(finalDeployableArtifact),new File(artifactArchiveDir))
        }
        //Deleting the temp dir.
        println("Deleting temp directory after creating final artifact: "+tempDeployables)
        FileUtils.deleteDirectory(new File(tempDeployables))
    }
    catch (Exception ex)
    {
        println(ex)
        println("#####  Unable to create deployable artifact.  ####")
    }
}

def sendEmail(params){
    printf "Sending Deploy Success email to: " + params.emailReceipients
     emailext (
      subject: "Deploy Complete | ${env.targetEnvironment} | ${env.JOB_NAME} | ${env.BUILD_NUMBER}",
      body: '${JELLY_SCRIPT,template="static-analysis"}',
      mimeType: 'text/html',
      attachLog: true,
      recipientProviders: [[$class: 'DevelopersRecipientProvider']],
      to: params.emailReceipients
    )
}


def deleteDirinArchiveFolder() {
    File archiveDir = new File(WORKSPACE + "/deployment_archive/")
    if (archiveDir.exists() && archiveDir.isDirectory()) {
        File[] allArchivedArtifacts = archiveDir.listFiles()
        for (File archive : allArchivedArtifacts) {
            if (archive.isDirectory()) {
                println("Deleting ----->  " + archive)
                archive.deleteDir()
            }
        }
    }
}