package com.gy
import com.gy.utility.CommandExecutor
@Grab(group='org.zaproxy', module='zap-clientapi', version='1.8.0')
import org.zaproxy.clientapi.core.*
import java.nio.charset.StandardCharsets;
import java.io.File; 
import java.io.FileOutputStream; 
import java.io.OutputStream; 
import com.gy.exception.GyCustomException
import com.gy.QualityGates

String ZAP_INSTALL_DIR
String ZAP_API_KEY
String ZAP_HOST
int ZAP_PORT
String targetDomain
String operatingSystem
def commandExecutor
def activeScanStarted
def activeScanFinished
def passiveScanStarted
def passiveScanFinished
String outputReportDir
String dastReportDir
int ZAP_START_CHECK_MAX_TRIES
int ZAP_START_CHECK_COUNT
String PREFIX_OUTPUT_FILE
int QG_MAX_ALERT_HIGH
int QG_MAX_ALERT_LOW
int QG_MAX_ALERT_MEDIUM
int QG_MAX_ALERT_INFORMATIONAL

def runDast(Map params) {
   intializeVariables(params)
   //createReportDir()
   if(!isZAPRunning()){
      startZAP()
   }
   startSpider()
}

def intializeVariables(Map params){
   String scanTypeToRun = env.DAST_ZAP_SCAN_TYPE
   printf "scanTypeToRun: " + scanTypeToRun
   
   activeScanStarted = false
   activeScanFinished = false
   passiveScanStarted = false
   passiveScanFinished = false

   //Skip scans based on user selection
   if(scanTypeToRun == "Active"){
      passiveScanStarted = true
      passiveScanFinished = true
   } else if(scanTypeToRun == "Passive"){
      activeScanStarted = true
      activeScanFinished = true
   }
    
   //Property file
   def propFileContent = libraryResource("common.properties")
   def propertyFile = readProperties text: propFileContent
   outputReportDir = env.workspace + propertyFile['REPORTS_DIR']
   dastReportDir = outputReportDir + "/dast/" + env.targetEnvironment
   ZAP_INSTALL_DIR = propertyFile['ZAP_INSTALL_DIR']
   ZAP_API_KEY = propertyFile['ZAP_API_KEY']
   ZAP_PROTOCOL = propertyFile['ZAP_PROTOCOL']
   ZAP_HOST = propertyFile['ZAP_HOST']
   ZAP_PORT = propertyFile['ZAP_PORT']
   
   //Quality Gates
   QG_MAX_ALERT_HIGH = propertyFile['qg_zap_max_alert_high']
   QG_MAX_ALERT_LOW = propertyFile['qg_zap_max_alert_low']
   QG_MAX_ALERT_MEDIUM = propertyFile['qg_zap_max_alert_medium']
   QG_MAX_ALERT_INFORMATIONAL = propertyFile['qg_zap_max_alert_informational']

   //Environment Specific Property File
   propFileContent = libraryResource env.targetEnvironment + '.properties'
   propertyFile = readProperties text: propFileContent
   domainName = propertyFile['DOMAIN_NAME']
   domainProtocol = propertyFile['DOMAIN_PROTOCOL']
   targetDomain = domainProtocol + "://" + domainName
   
   //Objects
   commandExecutor = new CommandExecutor()
   operatingSystem = commandExecutor.getOS()

   //vars
   ZAP_START_CHECK_MAX_TRIES = 20
   ZAP_START_CHECK_COUNT = 0
   PREFIX_OUTPUT_FILE = "dast-" + env.targetEnvironment + "-"
}

private boolean isZAPRunning(){
   ZAP_START_CHECK_COUNT++
   printf "isZAPRunning() - Checking for $ZAP_START_CHECK_COUNT time"
   def response = ["curl", "-s", "-o", "/dev/null",  "-I", "-w", "%{http_code}", ZAP_PROTOCOL + ZAP_HOST + ":" + ZAP_PORT].execute().text
   if(response == "200"){
      printf "ZAP is running @ $ZAP_HOST:$ZAP_PORT"
      return true
   }else{
      printf "ZAP is NOT running @ $ZAP_HOST:$ZAP_PORT"
      return false
   } 
}

def startZAP(){
   printf "Starting ZAP Server"
   String command
   if(operatingSystem == "windows")
      command = "START /B zap.bat"
   else
      command = "sh zap.sh"

   //headless
   command += " -daemon -port " + ZAP_PORT + " -config api.key=" + ZAP_API_KEY + " &"
   
   dir(ZAP_INSTALL_DIR) {   
      commandExecutor.execute(command)
   }

   //Check if ZAP is up
   while(true){
      if(ZAP_START_CHECK_COUNT < ZAP_START_CHECK_MAX_TRIES){
         if(isZAPRunning()){
            printf "ZAP running. Breaking Loop"
            break
         }else{
            printf "Wating for ZAP. Continue Loop"
            Thread.sleep(1000);
         }
      }
      else{
         throw new GyCustomException("ZAP Start Check, max limit reached. Please check if ZAP is up on configured host and port.")
      }
   }
      
}

def stopZAP(){
    if(isZAPRunning()){
       printf "Stopping ZAP..."
       def response = ["curl", ZAP_PROTOCOL + ZAP_HOST + ":" + ZAP_PORT, "/JSON/core/action/shutdown/"].execute().text
    }
}

def startSpider(){
   printf "Starting Spider"
   ClientApi api = new ClientApi(ZAP_HOST, Integer.parseInt(ZAP_PORT), ZAP_API_KEY);
   
      // Start spidering the target
      printf "Spidering target : " + targetDomain
      ApiResponse resp = api.spider.scan(targetDomain, null, null, null, null)
      String scanID
      int progress = 0
      // The scan returns a scan id to support concurrent scanning
      scanID = ((ApiResponseElement) resp).getValue();
      printf "Scan ID: $scanID"
      // Poll the status until it completes
      while (true) {
         Thread.sleep(1000);
         progress = Integer.parseInt(((ApiResponseElement) api.spider.status(scanID)).getValue());
         printf "Spider progress: " + progress + "/100"
         
         if(progress >= 10 && !activeScanStarted){
            printf "Kick off Active Scan"
            activeScanStarted = true
            startActiveScan()
         }

         if(progress >= 10 && activeScanFinished && !passiveScanStarted){
            printf "Kick off Passive Scan"
            passiveScanStarted = true
            startPassiveScan()
         }
         
         printf "activeScanFinished flag: " + activeScanFinished
         printf "passiveScanFinished flag: " + passiveScanFinished
        
         if (activeScanFinished && passiveScanFinished) {
            runQualityGate()
            stopZAP()
            printf "Scan has finished. Breaking spider loop to terminate the jenkins job."
            break
         }  
      }
      printf "Spider completed"
      // If required post process the spider results
      List<ApiResponse> spiderResults = ((ApiResponseList) api.spider.results(scanID)).getItems()
}

def startPassiveScan(){
   printf "Starting Passive Scan"
   ClientApi api = new ClientApi(ZAP_HOST, Integer.parseInt(ZAP_PORT), ZAP_API_KEY);
   try {
      // Loop until the passive scan has finished
      while (true) {
         Thread.sleep(2000);
         api.pscan.recordsToScan();
         numberOfRecords = Integer.parseInt(((ApiResponseElement) api.pscan.recordsToScan()).getValue());
         printf "Number of records left for scanning : " + numberOfRecords;
         if (numberOfRecords == 0) {
            break;
         }
      }
      passiveScanFinished = true
      printf "Passive Scan completed"  
      // Print vulnerabilities found by the scanning
      writeReponseToFile("Passive", api.core.htmlreport())
     } catch (Exception e) {
         printf "Exception : " + e.getMessage();
         e.printStackTrace();
    }
}

def startActiveScan(){
   printf "Starting Active Scan"
   ClientApi api = new ClientApi(ZAP_HOST, Integer.parseInt(ZAP_PORT), ZAP_API_KEY);
   try {
      printf "Active Scanning target : " + targetDomain     
      ApiResponse resp = api.ascan.scan(targetDomain, "True", "False", null, null, null);
      String scanid;
      int progress;

      // The scan now returns a scan id to support concurrent scanning
      scanid = ((ApiResponseElement) resp).getValue();
         
      // Poll the status until it completes
      while (true) {
         Thread.sleep(5000);
         progress = Integer.parseInt(((ApiResponseElement) api.ascan.status(scanid)).getValue());
         printf "Active Scan progress : " + progress + "/100";
         if (progress >= 100) {
             break;
         }
      }
      
      activeScanFinished = true
      printf "Active Scan complete"     
      // Print vulnerabilities found by the scanning
      writeReponseToFile("Active", api.core.htmlreport())
   } catch (Exception e) {
      System.out.println("Exception : " + e.getMessage());
      e.printStackTrace();
   }
}

def createReportDir(){
    File dir 
    dir = new File(outputReportDir)
    printf "Creating directory: " + outputReportDir
    dir.mkdirs()
    dir = new File(dastReportDir)
    printf "Removing existing report directory if exist: " + dastReportDir
    dir.deleteDir()
    printf "Creating directory: " + dastReportDir
    dir.mkdirs()
}


def writeReponseToFile(String scanType, byte[] fileContent){
   printf "Begin::writeReponseToFile"
   String fileName = outputReportDir + "/" + PREFIX_OUTPUT_FILE + scanType + "Scan.html"
   printf "Going to publish to file: " + fileName

   def file = new File(fileName)
   file.createNewFile()
   OutputStream os = new FileOutputStream(file); 
  
   // Starts writing the bytes in it 
    os.write(fileContent); 
    printf "Report published in file: $fileName" 
  
   // Close the file 
   os.close(); 
}


def runQualityGate(){
   QualityGates qualityGatesObj = new QualityGates()
   Map<String,String> qualityGateProps = new HashMap<String,String>();
   qualityGateProps.put("target", "ZAP")
   qualityGateProps.put("ZAP_HOST", ZAP_HOST)
   qualityGateProps.put("ZAP_PORT", ZAP_PORT)
   qualityGateProps.put("ZAP_API_KEY", ZAP_API_KEY)
   qualityGateProps.put("ZAP_ALERT_URI", "/JSON/alert/view/alertCountsByRisk/")
   //Quality Gates
   qualityGateProps.put("QG_MAX_ALERT_HIGH", QG_MAX_ALERT_HIGH)
   qualityGateProps.put("QG_MAX_ALERT_LOW", QG_MAX_ALERT_LOW)
   qualityGateProps.put("QG_MAX_ALERT_MEDIUM", QG_MAX_ALERT_MEDIUM)
   qualityGateProps.put("QG_MAX_ALERT_INFORMATIONAL", QG_MAX_ALERT_INFORMATIONAL)
   qualityGatesObj.executeQualityGate(qualityGateProps)
}