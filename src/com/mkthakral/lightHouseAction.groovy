package com.mkthakral
import com.mkthakral.utility
import org.apache.commons.io.FileUtils
import com.mkthakral.utility.CommandExecutor
import java.util.ArrayList;

String lighthousePackageJSON
String outputReportDir
String domainName
String domainProtocol
String domainHTTTPAuthenticated
String domainHTTTPCredentialsId
def destPackageJSON
String jenkinsLightHouseDir
def commandExecutor
String environment
String PREFIX_OUTPUT_FILE
String enableQualityGate
ArrayList<String> outputJSONReports
def REPORT_FILE_TYPE
String OOTB_REPORT_POSTFIX
def JSON_EXTENSION

//Primary function that in turn calls all other functions
def runLightHouse() {
    intializeVariables()
    copyNodeJSFile()
    installLightHouse()
    runPerformanceTest()
    runQualityGate()
}

//Function to initialize variables
def intializeVariables(){
   environment =  env.targetEnvironment
   //Common property file
   def propFileContent = libraryResource("common.properties")
   def propertyFile = readProperties text: propFileContent
   jenkinsLightHouseDir = propertyFile['LIGHHOUSE_DIR']
   lighthousePackageJSON = jenkinsLightHouseDir + "/package.json"
   outputReportDir = env.workspace + propertyFile['REPORTS_DIR']
   enableQualityGate = propertyFile['qg-lighthouse-enable']

   //Environment Specific Property File
   propFileContent = libraryResource environment + '.properties'
   propertyFile = readProperties text: propFileContent
   domainName = propertyFile['DOMAIN_NAME']
   domainProtocol = propertyFile['DOMAIN_PROTOCOL']
   domainHTTTPAuthenticated = propertyFile['DOMAIN_HTTP_AUTHENTICATION']
   domainHTTTPCredentialsId = propertyFile['DOMAIN_HTTP_AUTHENTICATION_CREDENTIALS_ID']

   //Objects
   commandExecutor = new CommandExecutor()
   outputJSONReports =  new ArrayList<String>();

   //Vars
   PREFIX_OUTPUT_FILE = "lighthouse-" + environment + "-"

   //Constants
   REPORT_FILE_TYPE = "html,json"
   OOTB_REPORT_POSTFIX = ".report"
   JSON_EXTENSION = ".json"
}

//Copy Sample NPM file from GIT to defined install location
def copyNodeJSFile(){
    def sourcePakageJSON = libraryResource "lighthouse-package.json"
    destPackageJSON = new File(lighthousePackageJSON)
    destPackageJSON.delete()
    destPackageJSON << sourcePakageJSON
}

//Install Lighhouse using NPM
def installLightHouse(){
    dir(jenkinsLightHouseDir) {
        commandExecutor.execute("npm install lighthouse --save-dev")
    }
}

//Lighhouse commands are executed for different pages
def runPerformanceTest(){
    String credentials = ""
    String outputFile = ""
    if(domainHTTTPAuthenticated == "true"){
        println "This domain $domainName is HTTP Authentication Protected"
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'GY_HTTP_CREDENTIALS', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            credentials = "$USERNAME:$PASSWORD@"
        }
    }
    def request = libraryResource "lighthouse-input.properties"
    def pageList = request.readLines()
    pageList.eachWithIndex { item, index ->
        def (page, uri) = item.tokenize( '=' )
        dir(jenkinsLightHouseDir) {
            outputFile = outputReportDir + "/" + PREFIX_OUTPUT_FILE + page
            outputJSONReports.add(outputFile + OOTB_REPORT_POSTFIX + JSON_EXTENSION)
            commandExecutor.execute('node ./node_modules/lighthouse/lighthouse-cli/index.js --output ' + REPORT_FILE_TYPE + ' --output-path=' + outputFile + " --quiet --chrome-flags=\"--headless --ignore-certificate-errors --no-sandbox\" " + domainProtocol + "://"  + domainName + uri)
        }
    }
}

//Quality Gate for Lighthouse
def runQualityGate(){
   if(enableQualityGate == "true"){
      printf "Start Running: Quality Gate for Lighthouse"
       //Property file
      def propFileContent = libraryResource("common.properties")
      def propertyFile = readProperties text: propFileContent
      
      //Call Quality Gate
      QualityGates qualityGatesObj = new QualityGates()
      Map<String,String> qualityGateProps = new HashMap<String,String>();
      qualityGateProps.put("target", "Lighthouse")
      qualityGateProps.put("outputJSONReports", outputJSONReports)
      qualityGateProps.put("qg-lighthouse-first-contentful-paint-max-ms", propertyFile["qg-lighthouse-first-contentful-paint-max-ms"])
      qualityGateProps.put("qg-lighthouse-largest-contentful-paint-max-ms", propertyFile["qg-lighthouse-largest-contentful-paint-max-ms"])
      qualityGateProps.put("qg-lighthouse-time-to-interactive-max-ms", propertyFile["qg-lighthouse-time-to-interactive-max-ms"])
      qualityGatesObj.executeQualityGate(qualityGateProps)
      printf "End Running: Quality Gate for Lighthouse"
   }else{
      printf "Quality Gate for Lighthouse is disabled. Skipping..."
   }
   
}