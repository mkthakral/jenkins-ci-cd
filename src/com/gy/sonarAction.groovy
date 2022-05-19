package com.gy
import org.apache.commons.io.FileUtils
import java.nio.file.Path
import java.nio.file.Paths
import com.gy.utility
import com.gy.utility.CommandExecutor
import com.gy.QualityGates
import java.util.*

def environment
def branch
def workspace
def libraryDir
String sharedLibraryResources
def commandExecutor
String projectNameAndKey
String sonarHostName

//Files
String fileJacocoAgentJar
String fileJacocoCLIJar
String fileSonarAntTaskJar
String sonarXML

//Local Property File
def propFileContent
def propertyFile
String HYBRIS_INSTALL_PATH
String HYBRIS_PLATFORM_HOME
String JACOCO_EXEC_URI
String enableQualityGate
def ANT_INSTALLATION

def runSonar(Map params) {
   intializeVariables(params)
   copySonarProperties()
   //JacocoAgentJar
   utility.copyFile(fileJacocoAgentJar, sharedLibraryResources, HYBRIS_INSTALL_PATH + "/hybris/bin/platform/resources/ant/sonar/lib/")
   //JacocoCLIJar
   utility.copyFile(fileJacocoCLIJar, sharedLibraryResources, HYBRIS_INSTALL_PATH + "/hybris/bin/platform/lib/")
   //SonarqubeAntTaskJar
   utility.copyFile(fileSonarAntTaskJar, sharedLibraryResources, HYBRIS_PLATFORM_HOME + "/resources/ant/sonar/lib/")
   //sonar xml
   utility.copyFile(sonarXML, HYBRIS_INSTALL_PATH + "/hybris/bin/platform/resources/ant/", HYBRIS_INSTALL_PATH + "/hybris/config/customize/platform/resources/ant/")
   //update sonar xml
   updateSonarXML(HYBRIS_INSTALL_PATH + "/hybris/bin/platform/resources/ant/" + sonarXML);
   //run sonar
   executeSonarCommands()
   //Quality Gate for Sonar
   runQualityGate()
}

//Quality Gate for Sonar
def runQualityGate(){
   if(enableQualityGate == "true"){
      printf "Start Running: Quality Gate for Sonar"
      QualityGates qualityGatesObj = new QualityGates()
      Map<String,String> qualityGateProps = new HashMap<String,String>();
      qualityGateProps.put("target", "Sonar")
      qualityGateProps.put("sonarHostName", sonarHostName)
      qualityGateProps.put("projectNameAndKey", projectNameAndKey)
      qualityGatesObj.executeQualityGate(qualityGateProps)
      printf "End Running: Quality Gate for Sonar"
   }else{
      printf "Quality Gate for Sonar is disabled. Skipping..."
   }
   
}

def updateSonarXML(String sonarXMLFile){
    def file = new File(sonarXMLFile)
    file.text = file.text.replace('<property name="platformhome" location="../.." />', '<property name="platformhome" location="' + HYBRIS_PLATFORM_HOME + '" />')
    file.text = file.text.replace("sonar-ant-task-2.1.jar", fileSonarAntTaskJar)
    file.text = file.text.replace("sonar.binaries","sonar.java.binaries")
    file.text = file.text.replace("sonar.libraries","sonar.java.libraries")
}

def executeSonarCommands(){
    //Create tmp file
    utility.createTmpTestFileHybris(HYBRIS_INSTALL_PATH)
    dir(HYBRIS_INSTALL_PATH + "/hybris/bin/platform") {
      withAnt(installation: ANT_INSTALLATION) {
         if(env.Sonar_Initialize == "NOT_INITIALIZED"){
             commandExecutor.execute("ant customize")
             if(commandExecutor.getOS() == "windows")
                  commandExecutor.execute("setantenv.bat")
             else
                  commandExecutor.execute(". ./setantenv.sh")    
             commandExecutor.execute("ant yunitinit")
         }
         //commandExecutor.execute('ant alltests -Dtestclasses.extensions=\"gyacceleratorcore,gyacceleratorfacades,gyacceleratorfulfilmentprocess,gyservices,gywebservices\"')
         //Converting jacoco.exec -> jacoco.xml
         commandExecutor.execute("java -jar lib/" + fileJacocoCLIJar + " report " + HYBRIS_INSTALL_PATH + JACOCO_EXEC_URI + "/jacoco.exec" + " --classfiles " + HYBRIS_INSTALL_PATH + "/hybris/bin/custom/gywebservices/classes/ --classfiles " + HYBRIS_INSTALL_PATH + "/hybris/bin/custom/gyaccelerator/gyacceleratorcore/classes --classfiles " + HYBRIS_INSTALL_PATH + "/hybris/bin/custom/gyaccelerator/gyacceleratorfacades/classes --classfiles " + HYBRIS_INSTALL_PATH + "/hybris/bin/custom/gyaccelerator/gyacceleratorfulfilmentprocess/classes --classfiles " + HYBRIS_INSTALL_PATH + "/hybris/bin/custom/gyservices/classes --xml " + HYBRIS_INSTALL_PATH + "/hybris/bin/hybris/log/junit/"  + "jacoco.xml")
         commandExecutor.execute('ant sonar')
      }
   }
}

def intializeVariables(Map params){
   //Variables
   branch = env.branch
   workspace = env.workspace
   workspace = workspace.replace("\\","/")
   libraryDir = params.library
   projectNameAndKey = params.projectNamePrefix + env.branch + params.projectNameSuffix

   //Objects
    commandExecutor = new CommandExecutor()
   
   //Local Property File
   propFileContent = libraryResource 'common.properties'
   propertyFile = readProperties text: propFileContent
   HYBRIS_INSTALL_PATH = propertyFile['HYBRIS_INSTALL_PATH']
   HYBRIS_PLATFORM_HOME = HYBRIS_INSTALL_PATH + '/hybris/bin/platform'
   JACOCO_EXEC_URI = propertyFile['javaoptions.jacoco.exec.report.filepath']
   enableQualityGate = propertyFile['qg-sonar-enable']
   ANT_INSTALLATION = propertyFile['LOCAL_ANT_INSTANCE_NAME']


   //Custom variable
   sharedLibraryResources = workspace + "/../" + env.JOB_NAME + "@libs" + "/" + libraryDir + "/resources/"

   //Files
   fileJacocoAgentJar="jacocoagent.jar"
   fileJacocoCLIJar="org.jacoco.cli-0.8.3-nodeps.jar"
   fileSonarAntTaskJar="sonarqube-ant-task-2.7.0.1612.jar"
   sonarXML="sonar.xml"
}

//append sonar properties in local.properties
def copySonarProperties(){
  //Get Sonar Login ID
   String sonarLoginID
   
   withCredentials([string(credentialsId: 'SONAR_LOGIN_ID', variable: 'loginID')]) {
     sonarLoginID = loginID
   }

  //Sonar Property File
  def sonarPropFileContent = libraryResource 'sonar.properties'
  def sonarPropertyFile = readProperties text: sonarPropFileContent
  sonarHostName = sonarPropertyFile['sonar.host.url']
 
  File fileLocalProperties = new File(HYBRIS_INSTALL_PATH + "/hybris/config/local.properties")
  fileLocalProperties.append("\n")
  fileLocalProperties.append("#Sonar Properties")
  fileLocalProperties.append("\n")
  fileLocalProperties.append("sonar.projectName=" + projectNameAndKey + "\n")
  fileLocalProperties.append("sonar.projectKey=" + projectNameAndKey + "\n")
  fileLocalProperties.append("sonar.host.url=" + sonarHostName + "\n")
  fileLocalProperties.append("sonar.login=" + sonarLoginID + "\n")
  fileLocalProperties.append("sonar.language=" + sonarPropertyFile['sonar.language'] + "\n")
  fileLocalProperties.append("sonar.verbose=" + sonarPropertyFile['sonar.verbose'] + "\n")
  fileLocalProperties.append("sonar.sourceEncoding=" + sonarPropertyFile['sonar.sourceEncoding'] + "\n")
  fileLocalProperties.append("sonar.java.coveragePlugin=" + sonarPropertyFile['sonar.java.coveragePlugin'] + "\n")
  fileLocalProperties.append("sonar.java.binaries=" + sonarPropertyFile['sonar.java.binaries'] + "\n")
  fileLocalProperties.append("sonar.jacoco.reportPath=" + HYBRIS_INSTALL_PATH + "/hybris/bin/hybris/log/junit/jacoco.exec" + "\n")
  fileLocalProperties.append("sonar.coverage.jacoco.xmlReportPaths=" + HYBRIS_INSTALL_PATH + "/hybris/bin/hybris/log/junit/jacoco.xml" + "\n")
  fileLocalProperties.append("sonar.extensions=" + sonarPropertyFile['sonar.extensions'] + "\n")
  fileLocalProperties.append("sonar.dynamicAnalysis=" + sonarPropertyFile['sonar.dynamicAnalysis'] + "\n")
  fileLocalProperties.append("sonar.inclusions=" + sonarPropertyFile['sonar.inclusions'] + "\n")
  fileLocalProperties.append("sonar.exclusions=" + sonarPropertyFile['sonar.exclusions'] + "\n")
  fileLocalProperties.append("sonar.coverage.exclusions=" + sonarPropertyFile['sonar.coverage.exclusions'] + "\n")
  fileLocalProperties.append("sonar.excludedExtensions=" + sonarPropertyFile['sonar.excludedExtensions'] + "\n")
  fileLocalProperties.append("sonar.web.file.suffixes=" + sonarPropertyFile['sonar.web.file.suffixes'] + "\n")
  fileLocalProperties.append("sonar.junit.reportsPath=" + workspace + "/report/sonar" + "\n")
  fileLocalProperties.append("sonar.junit.reportPath=" + workspace + "/report/sonar" + "\n")
  fileLocalProperties.append("standalone.javaoptions=" + "-javaagent:" + HYBRIS_INSTALL_PATH + "/hybris/bin/platform/resources/ant/sonar/lib/jacocoagent.jar=destfile=" + HYBRIS_INSTALL_PATH + "/hybris/bin/hybris/log/junit/jacoco.exec" + ",append=true,excludes=com.google.*:com.sun.*:de.hybris.*:org.mockito.*:org.junit.*:org.apache.*,includes=com.mkthakral.*" + "\n")
  fileLocalProperties.append("sonar.projectBaseDir=" + HYBRIS_INSTALL_PATH + "/hybris/bin/custom" + "\n")
  fileLocalProperties.append("sonar.buildbreaker.skip=" + sonarPropertyFile['sonar.buildbreaker.skip'] + "\n")
}