package com.mkthakral
import org.apache.commons.io.FileUtils

static String getOS(){
    String osName = System.getProperty("os.name").toLowerCase();
 
    if (osName.contains("linux")) {
        return ("unix");
    } else if (osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx")) {
        return ("macos");
    } else if (osName.contains("windows")) {
        return ("windows");
    } else if (osName.contains("sunos") || osName.contains("solaris")) {
        return ("solaris");
    } else if (osName.contains("freebsd")) {
        return ("freebsd");
    }
}

//Fix for "Filename too long" error
static String getCheckoutDir(String workspace, String alternateWorkspace){
    if (alternateWorkspace !=null && alternateWorkspace != '' && alternateWorkspace != '/') {
        return alternateWorkspace
    }else{
        return workspace
    }
}

//copy file from source to destination dir
static void copyFile(String fileName, String sourceDir, String destinationDir){
  printf "copyFile($fileName,$sourceDir,$destinationDir)"
  String sourceFilePath = sourceDir + fileName
  String destFilePath = destinationDir + fileName
  (new File(destinationDir)).mkdir()
  printf "Copying File:"
  printf "Source: " + sourceFilePath
  printf "Destination: " + destFilePath
  FileUtils.copyFile(new File(sourceFilePath), new File(destFilePath));
}

static void createTmpTestFileHybris(String hybrisInstallPath) {
   //Remove Tmp file delete command
   def file = new File(hybrisInstallPath + "/hybris/bin/platform/resources/ant/testing.xml")
   file.text = file.text.replace('<delete file=\"${HYBRIS_TEMP_DIR}/testing_additional_tests.txt\"/>',"")
   //Create Temp File
   String tempFilePath = hybrisInstallPath + "/hybris/temp/hybris/testing_additional_tests.txt"
   File tempFile = new File(tempFilePath)
   printf "Creating temp file: $tempFilePath"
   tempFile.createNewFile()
   printf "Created temp file: $tempFilePath"
}

static void cleanWorkspaceReportDIR(String directory){
   printf "Start: clearWorkspaceReports(...)"
   printf "Removing Workspace Report Dir: " + directory
   File dir = new File(directory)
   dir.deleteDir()
   printf "Clearing Workspace Report Dir: " + directory
   dir.mkdirs()
   printf "End: clearWorkspaceReports(...)"
}