package com.gy
import com.gy.utility
import org.apache.commons.io.FileUtils
import com.gy.utility.CommandExecutor

//Copy & Update Hybris files
def copyAndUpdateFiles(Map params){
   copyHybrisFiles(params)
   cleanReportDir(params)
   copyWSDLFile(params)
   copyOtherFiles(params)
}

def copyHybrisFiles(Map params){
   //Property File
   def propFileContent = libraryResource 'common.properties'
   def propertyFile = readProperties text: propFileContent

   String alternateCheckoutDir = propertyFile['ALTERNATE_WORKSPACE_DIR']
   String workspace = env.workspace
   def checkoutDir = utility.getCheckoutDir(workspace, alternateCheckoutDir)
   def HYBRIS_INSTALL_PATH = propertyFile['HYBRIS_INSTALL_PATH']

   //Echo Statements
   printf "Copying selective files from: " + checkoutDir
   printf "Copying selective files to: " + HYBRIS_INSTALL_PATH
 
   //Remove Existing Config Folder
   String relativePathConfig = "/hybris/config"
   def configDirPath = HYBRIS_INSTALL_PATH + relativePathConfig
   printf "Deleting Directory/File: " + configDirPath
   def configDir = new File(configDirPath)
   configDir.deleteDir()

   //Remove Existing Custom Dir
   String relativePathCustom = "/hybris/bin/custom"
   def customDirPath = HYBRIS_INSTALL_PATH + relativePathCustom
   printf "Deleting Directory/File: " + customDirPath
   def customDir = new File(customDirPath)
   customDir.deleteDir()

   //Copy Config Dir 
   String sourceConfigDirPath = checkoutDir + "/" + propertyFile['CONFIG_DIR']
   printf "Copying config dir: FROM: " + sourceConfigDirPath
   printf "Copying config dir: TO: " + configDir
   FileUtils.copyDirectory(new File(sourceConfigDirPath), configDir);

   //Copy Custom Dir
   String sourceCustomDirPath = checkoutDir + "/custom"
    printf "Copying custom dir: FROM: " + sourceCustomDirPath
   printf "Copying custom dir: TO: " + customDir
   FileUtils.copyDirectory(new File(sourceCustomDirPath), customDir);

    //Delete package-lock.json
    File packageJSON = new File(customDirPath + "/webapp/package-lock.json"); 
    packageJSON.delete()
}

//Clear report dir which might contain old reports
def cleanReportDir(Map params){
   def propFileContent = libraryResource 'common.properties'
   def propertyFile = readProperties text: propFileContent
   String workspace = env.workspace
   utility.cleanWorkspaceReportDIR(workspace + propertyFile['REPORTS_DIR'])
}

//Copy WSDL files out of Hybris installation dir
def copyWSDLFile(Map params){
   def propFileContent = libraryResource 'common.properties'
   def propertyFile = readProperties text: propFileContent
   def HYBRIS_INSTALL_PATH = propertyFile['HYBRIS_INSTALL_PATH']
   String sourceWSDLDir = HYBRIS_INSTALL_PATH + "/hybris/bin/custom/gyservices/resources/wsdl"
   
   //Delete existing WSDL Dir, get variable value from Jenkins Variable
   def targetWSDLDir = new File("$HYBRIS_GYSERVICES")
   targetWSDLDir.deleteDir()

    //Echo Statements
   printf "Source WSDL Dir: " + sourceWSDLDir
   printf "Target WSDL Dir: " + targetWSDLDir

   //Copy new WSDL Dir
   printf "Copying WSDL from Source to target Dir"
   FileUtils.copyDirectory(new File(sourceWSDLDir), targetWSDLDir);
}

//Copy Other files
def copyOtherFiles(Map params){
   def propFileContent = libraryResource 'common.properties'
   def propertyFile = readProperties text: propFileContent
   String resourcesFilesTargetEnv = propertyFile['RESOURCE_FILES_FOR_TARGET_ENV']
   String[] fileList = resourcesFilesTargetEnv.split(",");
   String sharedLibraryResources = env.workspace + "/../" + env.JOB_NAME + "@libs" + "/" + params.library + "/resources/"
   
   def targetDirPath = env.workspace + "/targetEnvFiles/"
   def targetDir = new File(targetDirPath)

   printf "Deleting Target File Dir"
   targetDir.deleteDir()

   printf "Re-Creating Target File Dir"
   targetDir.mkdirs()

   printf "Copy file: Source Dir: $sharedLibraryResources"
   printf "Copy file: Target Dir: $targetDir"

   for(String fileName : fileList){
      printf "Copy file: File name: $fileName"
     
      utility.copyFile(fileName, sharedLibraryResources, targetDirPath)
   }
}

//Compile Hybris codebase
def compileHybris(Map params){
   //Property File
   def propFileContent = libraryResource 'common.properties'
   def propertyFile = readProperties text: propFileContent
   def HYBRIS_INSTALL_PATH = propertyFile['HYBRIS_INSTALL_PATH']
   def ANT_INSTALLATION = propertyFile['LOCAL_ANT_INSTANCE_NAME']
   def commandExecutor = new CommandExecutor()
   String osName = utility.getOS()
   switch(osName){
      case "unix" :
         ". ." + HYBRIS_INSTALL_PATH + "/hybris/bin/platform/setantenv.sh"
         break;
      case "windows":
         bat HYBRIS_INSTALL_PATH + "/hybris/bin/platform/setantenv.bat"
         def file = new File(HYBRIS_INSTALL_PATH + "/hybris/bin/custom/gyaccelerator/gyacceleratorstorefront/buildcallbacks.xml")
         file.text = file.text.replace('/bin/bash', propertyFile['BASH_INSTALL_PATH'])
         file.text = file.text.replace('<property name=\"gyWebappPath\" value=\"${HYBRIS_BIN_DIR}/custom/webapp\"/>', '<property name=\"gyWebappPath\" value=\"' + HYBRIS_INSTALL_PATH + "/hybris/bin/custom/webapp\"/>")
         dir(HYBRIS_INSTALL_PATH + "/hybris/bin/custom/webapp"){
		      commandExecutor.execute("npm cache clear --force")
		      commandExecutor.execute("npm cache verify")
		      commandExecutor.execute("npm install")
	      }
         break;   
   }

   dir(HYBRIS_INSTALL_PATH + "/hybris/bin/platform") {
      withAnt(installation: ANT_INSTALLATION) {
         commandExecutor.execute("ant customize")
         commandExecutor.execute("ant clean all")
      }
   }
}