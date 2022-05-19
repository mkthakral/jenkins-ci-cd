package com.gy

import com.gy.utility.PropertyFileReader
import com.gy.utility.CommandExecutor
import org.apache.commons.io.FileUtils
import com.gy.utility

def properties
def workspace

String HYBRIS_HOME
String JAVA_HOME
String ANT_INSTANCE_NAME
String sharedLibraryResources
String antTestTarget
String jacocExecReportPath
String fileJacocoAgentJar
String jacocoPluginExclusionPattern
String jacocoPluginClassPattern
String jacocoPluginSourcePattern

def makeReport(Map params)
{
    def runJUnitTenantInit = params.runJUnitTenantInit
    def runAntAll = params.runAntAll
    initializeConstants(params)
    println("Creating temp file")
    utility.createTmpTestFileHybris(HYBRIS_HOME)
    copyJavaOptionsToLocalProp()
    copyJacocoAgentJar(fileJacocoAgentJar,sharedLibraryResources, HYBRIS_HOME + "/hybris/bin/platform/resources/ant/sonar/lib/")
    updateLocalPropertyDBUrl(params.dbHostURL, params.dbName)
    if ((runJUnitTenantInit != null) && runJUnitTenantInit)
    {
        println("###################  Starting JUnit Tenant Initialization ###################")
        initializeTestTenants()
    }
    else {
        println("###################  Skipping JUnit Tenant Initialization : runJUnitTenantInit = false  ###########")
    }

    if ((runAntAll != null) && runAntAll)
    {
        println("###################  Starting Ant all ###################")
        runAntAllTarget()
    }
    runTest(params)
    readJUnitReport()
    createJacocoReport()
}


def initializeConstants(Map params)
{
    workspace = params.workspace

    def propertyFileReader = new PropertyFileReader()
    properties = propertyFileReader.readPropertyFile("common")

    HYBRIS_HOME =  properties['HYBRIS_INSTALL_PATH']
    ANT_INSTANCE_NAME = properties['LOCAL_ANT_INSTANCE_NAME']

    jacocExecReportPath = (properties['javaoptions.jacoco.exec.report.filepath'] != null) ? properties['javaoptions.jacoco.exec.report.filepath'] : "/hybris/log/reports/jacoco"
    fileJacocoAgentJar = properties['javaoptions.jacocoagent.jar.name']
    jacocoPluginExclusionPattern  = properties['jacoco.exclusionPattern']
    jacocoPluginClassPattern  = properties['jacoco.classPattern']
    jacocoPluginSourcePattern  = properties['jacoco.sourcePattern']

    sharedLibraryResources = workspace + "/../" + env.JOB_NAME + "@libs" + "/" + params.libraryDir + "/resources/"


}


def copyJavaOptionsToLocalProp()
{
    final String jacocoagentJARPath =  (properties['javaoptions.jacocoagent.jar.hybrisPath'] != null) ? properties['javaoptions.jacocoagent.jar.hybrisPath'] : "/platform/resources/ant/sonar/lib"
    final String jacocoAgentJARName =  (properties['javaoptions.jacocoagent.jar.name'] != null) ? properties['javaoptions.jacocoagent.jar.name'] : "jacocoagent.jar"
    final String jacocoAppend = (properties['javaoptions.jacoco.append'] != null) ? properties['javaoptions.jacoco.append'] : "true"

    //javaoption that has to inserted in local.properties for Jacoco
    String javaOptionsForJacoco = "standalone.javaoptions=" + "-javaagent:" + "\${HYBRIS_BIN_DIR}" + jacocoagentJARPath + "/" + jacocoAgentJARName + "=destfile=" + HYBRIS_HOME + jacocExecReportPath + "/jacoco.exec,append=" + jacocoAppend

    if (properties['javaoptions.jacoco.exclude.regex'] != null)
    {
        javaOptionsForJacoco = javaOptionsForJacoco +",excludes="+properties['javaoptions.jacoco.exclude.regex']
    }
    if (properties['javaoptions.jacoco.include.regex'] != null)
    {
        javaOptionsForJacoco = javaOptionsForJacoco + ",includes=" + properties['javaoptions.jacoco.include.regex']
    }

    javaOptionsForJacoco = javaOptionsForJacoco + "\n"
    println("JavaOptionsForJacoco inserted in local.properties: " +javaOptionsForJacoco)

    //Copying the Javaoptions for Jacoco in local.properties
    println("Hybris home "+HYBRIS_HOME)
    def localPropertiesFilePath = HYBRIS_HOME+"/hybris/config/local.properties"
    def localProperties = new File(localPropertiesFilePath)
    localProperties.append("\n")
    localProperties.append(javaOptionsForJacoco)
}


def copyJacocoAgentJar(String fileName, String sourceDir, String destinationDir){
    String sourceFilePath = sourceDir + fileName
    String destFilePath = destinationDir + fileName
    (new File(destinationDir)).mkdir()
    printf "Copying File:"
    printf "Source: " + sourceFilePath
    printf "Destination: " + destFilePath
    FileUtils.copyFile(new File(sourceFilePath), new File(destFilePath));
}

def updateLocalPropertyDBUrl(String dbHostURL, String dbName)
{
    def localPropertiesFilePath = HYBRIS_HOME+"/hybris/config/local.properties"
    def fileContent = readFile localPropertiesFilePath
    String dbURL = "mysql://"+dbHostURL
    def newFileContent = fileContent.replaceAll("mysql://localhost:3306",dbURL)
    writeFile file: localPropertiesFilePath, text: "${newFileContent}"
    updateLocalPropertyDBName(dbName)
}

def updateLocalPropertyDBName(String dbName)
{
    def localPropertiesFilePath = HYBRIS_HOME+"/hybris/config/local.properties"
    def fileContent = readFile localPropertiesFilePath

    dbName = "/" + dbName
    def newFileContent = fileContent.replaceAll("/mkthakralDB?", dbName)
    writeFile file: localPropertiesFilePath, text: "${newFileContent}"
}

def initializeTestTenants()
{
    def command = ""
    def commandExecutor = new CommandExecutor()
    String changePath =  ("cd " +HYBRIS_HOME+"/hybris/bin/platform")
    command = command + changePath

    switch(commandExecutor.getOS()){
        case "windows" :
            //command = command + "\n" + "set JAVA_HOME = " + JAVA_HOME
            command = command + "\n" + "setantenv.bat"

            break;
        default :
           // command = command + "\n" + "export JAVA_HOME = " + JAVA_HOME
            command = command + "\n" + ". ./setantenv.sh"
    }

    commandExecutor.execute(command)
    String antJunitInitCommand = "ant yunitinit -Dmaven.update.dbdrivers=false"
    println("Initializing JUnit Tenants with : " + antJunitInitCommand);

    dir(HYBRIS_HOME + "/hybris/bin/platform")
    {
       withAnt(installation: ANT_INSTANCE_NAME)
       {
           commandExecutor.execute(antJunitInitCommand)
       }
    }
}

def runAntAllTarget()
{
    def command = ""
    def commandExecutor = new CommandExecutor()
    String changePath =  ("cd " +HYBRIS_HOME+"/hybris/bin/platform")
    command = command + changePath

    switch(commandExecutor.getOS()){
        case "windows" :
            //command = command + "\n" + "set JAVA_HOME = " + JAVA_HOME
            command = command + "\n" + "setantenv.bat"

            break;
        default :
            //command = command + "\n" + "export JAVA_HOME = " + JAVA_HOME
            command = command + "\n" + ". ./setantenv.sh"
    }

    commandExecutor.execute(command)

    String antAll = "ant all"
    dir(HYBRIS_HOME + "/hybris/bin/platform")
    {
      withAnt(installation: ANT_INSTANCE_NAME)
      {
          commandExecutor.execute(antAll)
      }
    }
}

def runTest(Map params)
{
    //Setting up the ant environment
    println("Hybris Home : "+ HYBRIS_HOME)
    def command = ""
    def commandExecutor = new CommandExecutor()
    String changePath =  ("cd " +HYBRIS_HOME+"/hybris/bin/platform")
    command = command + changePath

    switch(commandExecutor.getOS()){
        case "windows" :
            //command = command + "\n" + "set JAVA_HOME = " + JAVA_HOME
            command = command + "\n" + "setantenv.bat"

            break;
        default :
            //command = command + "\n" + "export JAVA_HOME = " + JAVA_HOME
            command = command + "\n" + ". ./setantenv.sh"
    }
    commandExecutor.execute(command)

    //Running the ant command
    def excludedPackage     = properties['ant.test.excludePackageRegex']
    def includedPackage     = properties['ant.test.includePackageRegex']
    def includedExtensions  = properties['ant.test.includeExtensions']
    String antCommand = "ant"
    antTestTarget = params.antTestTarget
    if ((antTestTarget != null) && !(antTestTarget.isEmpty()))
    {
        antCommand = antCommand + " " + antTestTarget
        println( "###################  Running Ant Test :antTestTarget = "+antTestTarget+" ###################")
    }
    else {
        antCommand = antCommand + " unittests"
        println( "###################  Running default Ant test target: ant unittests ###################")
    }

    if ((excludedPackage != null) && !(excludedPackage.isEmpty()))
    {
        antCommand = antCommand + " -Dtestclasses.packages.excluded=\""+excludedPackage+"\""
    }
    if ((includedPackage != null) && (!(includedPackage.isEmpty())))
    {
        antCommand = antCommand + " -Dtestclasses.packages=\""+includedPackage + "\""
    }
    if ((includedExtensions != null) && (!(includedExtensions.isEmpty())))
    {
        antCommand = antCommand + " -Dtestclasses.extensions=\""+includedExtensions + "\""
    }

    dir(HYBRIS_HOME + "/hybris/bin/platform")
    {
      withAnt(installation: ANT_INSTANCE_NAME)
      {
         commandExecutor.execute(antCommand)
      }
    }

}

def createJacocoReport()
{
    println("################### Creating Jacoco Report On Jenkins ###################")
    def jacocoPluginAttributes = ""
    if ((jacocoPluginClassPattern != null))
    {
        jacocoPluginAttributes = jacocoPluginAttributes + "classPattern: '"+jacocoPluginClassPattern+"',"
    }
    if (jacocoPluginExclusionPattern != null)
    {
        jacocoPluginAttributes = jacocoPluginAttributes + "exclusionPattern: '"+jacocoPluginExclusionPattern+"',"
    }
    if (jacocoPluginSourcePattern != null)
    {
        jacocoPluginAttributes = jacocoPluginAttributes + "sourcePattern: '"+jacocoPluginSourcePattern+"'"
    }

    if (jacocoPluginAttributes.endsWith(",")) {
        jacocoPluginAttributes = jacocoPluginAttributes.substring(0, jacocoPluginAttributes.length()-1);
    }
    println("========  jacocoPluginAttributes = "+ jacocoPluginAttributes+"   =============")
    println(HYBRIS_HOME + jacocExecReportPath)

    dir(HYBRIS_HOME)
    {
        //variable not expected with jacoco, hence giving the patterns directly
        jacoco classPattern: '**/custom/**/classes',exclusionPattern: '**/jalo/**/*.class,**/constants/**/*.class,**/dto/**/*.class,**/*DTO.class,**/integ/webservices/**/*.class,**/*Standalone.class,**/gensrc/**/*.class,**/cmscockpit/**/*.class,**/cscockpit/**/*.class,**/productcockpit/**/*.class,**/*Form.java,*/*Controller.java,**/Jalo/**/*.java,**/*Form.java',sourcePattern: '**/custom/**/src'
    }
}

def readJUnitReport()
{
    println("################### Reading JUnit Report from : hybris/temp/hybris/junit/*.xml  ###################")
    dir(HYBRIS_HOME)
    {
        junit 'hybris/temp/hybris/junit/*.xml'
    }
}