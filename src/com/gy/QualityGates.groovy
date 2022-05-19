package com.gy
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets
import java.util.Base64;
@Grab(group='org.json', module='json', version='20140107')
import org.json.JSONObject;
import org.json.JSONArray;
import com.gy.utility.CommandExecutor
import com.gy.exception.QualityGateException
import java.math.BigDecimal;

HashMap<String, String>  qualityGateProps
CommandExecutor commandExecutor

//Start/Primary method of Quality Gates
public void executeQualityGate(HashMap<String, String> qualityGateProperties){
   printf "Start: executeQualityGate(...)"
   qualityGateProps = qualityGateProperties
   commandExecutor = new CommandExecutor()
   String target = qualityGateProps.get("target")
   printf "Starting Quality Gate execution for: " + target
   switch(target){
      case "Sonar" :
         printf "Sleeping for 20s as there could be change in Sonar Quality Gate Status"
         Thread.sleep(20000);
         qualityGateSonar(qualityGateProps, 0, 30, 5000)
         break;
      case "ZAP" :
         qualityGateZAP(qualityGateProps)
         break;
      case "Lighthouse" :
         qualityGateLighthouse(qualityGateProps)
         break;
   }
   printf "End: executeQualityGate(...)"
}

//Quality Gate Sonar
def qualityGateSonar(HashMap<String, String> qualityGateProps, int SONAR_TRY_COUNT, int SONAR_MAX_TRIES, int SONAR_SLEEP_TIME){
   SONAR_TRY_COUNT++;
   printf "Checking Quality Gate for Sonar status for $SONAR_TRY_COUNT time"
   String sonarHostName = qualityGateProps.get("sonarHostName")
   String projectKey = qualityGateProps.get("projectNameAndKey")
   String projectKeyEncoded = new String(projectKey.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
   String sonarUsername
   String sonarPassword  
   String qualityGateURL = sonarHostName + "api/qualitygates/project_status?projectKey=" + projectKeyEncoded
   transient HttpURLConnection httpConnection

   withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'SONAR_CREDENTIALS', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
      sonarUsername = "$USERNAME"
      sonarPassword = "$PASSWORD"
   }

   try {
      //Prepare URL
      URL url = new URL(qualityGateURL);
      httpConnection = (HttpURLConnection) url.openConnection();
      httpConnection.setRequestMethod("GET");
      //Credentials
      String sonarCredentials = sonarUsername + ":" + sonarPassword;
		String sonarCredentialsEncoded = Base64.getEncoder().encodeToString(sonarCredentials.getBytes());
      httpConnection.setRequestProperty("Authorization", "Basic " + sonarCredentialsEncoded);
      String qualityGateStatus = "DEFAULT STATUS"

      printf "qualityGateURL: " + qualityGateURL
      printf "Response Code: " + httpConnection.getResponseCode()
      
      if (httpConnection.getResponseCode() == 200) {
         transient InputStream inputStream = httpConnection.getInputStream();
         transient InputStreamReader inputStreamReader = new InputStreamReader(inputStream)
         //Reading Response
         BufferedReader br = new BufferedReader(inputStreamReader);
         String line;
         printf "Printing Start: Output from Server: "
         JSONObject jsonObj = new JSONObject(br.readLine());
         if (jsonObj != null && jsonObj.getJSONObject("projectStatus") != null && jsonObj.getJSONObject("projectStatus").getString("status") != null){
            qualityGateStatus = jsonObj.getJSONObject("projectStatus").getString("status")
         }   
         printf "Quality Gate Response: " + jsonObj
         printf "Printing End: Output from Server"
      }
      
      printf "Quality Gate Status: " + qualityGateStatus

      if(qualityGateStatus == "NONE"){
         printf "Quality Gate status analsis is in progress."
         if (SONAR_TRY_COUNT <= SONAR_MAX_TRIES){
            printf "Try again to check status as status is None for now"
            httpConnection = null
            printf "Sleeping for $SONAR_SLEEP_TIME ms"
            Thread.sleep(SONAR_SLEEP_TIME);
            QualityGates qualityGatesObj = new QualityGates()
            qualityGatesObj.qualityGateSonar(qualityGateProps, SONAR_TRY_COUNT, SONAR_MAX_TRIES, SONAR_SLEEP_TIME)
         }else{
            printf "Max Try limit reached. Quitting Quality Gate"
            throw new QualityGateException("Sonar")
         }
      }else if(qualityGateStatus != "OK" || httpConnection.getResponseCode() != 200){
         printf "Marking build as failue as the Sonar Quality Gate Check != Success"
         throw new QualityGateException("Sonar")
      }
   } catch (MalformedURLException e) {
        e.printStackTrace();
    } catch (IOException e) {
        e.printStackTrace();
    }finally{
       httpConnection = null
    }
}

//Quality Gate ZAP
def qualityGateZAP(HashMap<String, String> qualityGateProps){
   def methodName = "Method::qualityGateZAP(...):"
   printf "$methodName Start: qualityGateZAP(...)"
   String ZAP_HOST = qualityGateProps.get("ZAP_HOST")
   String ZAP_PORT = qualityGateProps.get("ZAP_PORT")
   String ZAP_API_KEY = qualityGateProps.get("ZAP_API_KEY")
   String ZAP_ALERT_URI = qualityGateProps.get("ZAP_ALERT_URI")
   String urlString = "http://" + ZAP_HOST + ":" + ZAP_PORT + ZAP_ALERT_URI + "?apikey=" + ZAP_API_KEY 
   printf "$methodName urlString: $urlString"
   URL url = new URL(urlString);
   HttpURLConnection connection = (HttpURLConnection) url.openConnection();
   connection.setRequestMethod("GET");
   connection.setRequestProperty("Content-Type", "application/json");
   int responseCode = connection.getResponseCode();
   printf "$methodName ResponseCode: $responseCode"
   if(responseCode != 200){
      //throw exception if could not get response from ZAP
      throw new QualityGateException("ZAP")
   }
   InputStream inputStream = connection.getInputStream();
   InputStreamReader inputStreamReader = new InputStreamReader(inputStream)
   BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
   String line;
   JSONObject jsonZAPAlertSummary = new JSONObject(bufferedReader.readLine());
   printf "$methodName jsonZAPAlertSummary: $jsonZAPAlertSummary"
   
   int alertCountHigh = jsonZAPAlertSummary.getInt("High")
   int alertCountLow = jsonZAPAlertSummary.getInt("Low")
   int alertCountMedium = jsonZAPAlertSummary.getInt("Medium")
   int alertCountInformational = jsonZAPAlertSummary.getInt("Informational")
   bufferedReader.close();

   //Get Expected Quality Gates
   int QG_MAX_ALERT_HIGH = Integer.parseInt(qualityGateProps.get("QG_MAX_ALERT_HIGH"))
   int QG_MAX_ALERT_LOW = Integer.parseInt(qualityGateProps.get("QG_MAX_ALERT_LOW"))
   int QG_MAX_ALERT_MEDIUM = Integer.parseInt(qualityGateProps.get("QG_MAX_ALERT_MEDIUM"))
   int QG_MAX_ALERT_INFORMATIONAL = Integer.parseInt(qualityGateProps.get("QG_MAX_ALERT_INFORMATIONAL"))
  
   printf "$methodName Actual vs MAX_Expected - alertCountHigh: $alertCountHigh (should be <=) $QG_MAX_ALERT_HIGH"
   printf "$methodName Actual vs MAX_Expected - alertCountMedium: $alertCountMedium (should be <=) $QG_MAX_ALERT_MEDIUM"
   printf "$methodName Actual vs MAX_Expected - alertCountLow: $alertCountLow (should be <=) $QG_MAX_ALERT_LOW"
   printf "$methodName Actual vs MAX_Expected - alertCountInformational: $alertCountInformational (should be <=) $QG_MAX_ALERT_INFORMATIONAL"

   //Check if any alert type crossed defined threshhold value
   if(alertCountHigh > QG_MAX_ALERT_HIGH || alertCountLow > QG_MAX_ALERT_LOW || alertCountMedium > QG_MAX_ALERT_MEDIUM || alertCountInformational > QG_MAX_ALERT_INFORMATIONAL){
      throw new QualityGateException("Quality Gate Failed for ZAP")
   }else{
      printf "$methodName Quality Gate Passed for ZAP"
   }

   printf "$methodName End: qualityGateZAP(...)"
}

//Quality Gate Lighthouse
def qualityGateLighthouse(HashMap<String, String> qualityGateProps){
   def methodName = "Method::qualityGateLighthouse(...):"
   ArrayList<String> outputJSONFiles = qualityGateProps.get("outputJSONReports")

   //Loop all JSON files
   for(String jsonFile : outputJSONFiles){
      printf "$methodName Executing Quality Gate on $jsonFile"
      
      //Reading output JSON file of Lighthouse
      JSONObject jsonRoot = new JSONObject(new File(jsonFile).getText('UTF-8'))
      JSONObject jsonAudits = jsonRoot.getJSONObject("audits")
      
      //Results from Lighhouse Run
      float firstContentfulPaint = ((jsonAudits).getJSONObject("first-contentful-paint")).getDouble("numericValue")
      float largestContentfulPaint = ((jsonAudits).getJSONObject("largest-contentful-paint")).getDouble("numericValue")
      float timeToInteractive = ((jsonAudits).getJSONObject("interactive")).getDouble("numericValue")

      //Expected Quality Gates
      float QG_MAX_FIRST_CONTENTFUL_PAINT = Float.parseFloat(qualityGateProps.get("qg-lighthouse-first-contentful-paint-max-ms"))
      float QG_MAX_LARGEST_CONTENTFUL_PAINT = Float.parseFloat(qualityGateProps.get("qg-lighthouse-largest-contentful-paint-max-ms"))
      float QG_MAX_TIME_TO_INTERACTIVE = Float.parseFloat(qualityGateProps.get("qg-lighthouse-time-to-interactive-max-ms"))

      printf "$methodName | Actual vs MAX_Expected | firstContentfulPaint: $firstContentfulPaint ms (should be <=) $QG_MAX_FIRST_CONTENTFUL_PAINT ms"
      printf "$methodName | Actual vs MAX_Expected | largestContentfulPaint: $largestContentfulPaint ms (should be <=) $QG_MAX_LARGEST_CONTENTFUL_PAINT ms"
      printf "$methodName | Actual vs MAX_Expected | timeToInteractive: $timeToInteractive ms (should be <=) $QG_MAX_TIME_TO_INTERACTIVE ms"

      //Check if any alert type crossed defined threshhold value
      if(firstContentfulPaint > QG_MAX_FIRST_CONTENTFUL_PAINT || largestContentfulPaint > QG_MAX_LARGEST_CONTENTFUL_PAINT || timeToInteractive > QG_MAX_TIME_TO_INTERACTIVE){
         throw new QualityGateException("Quality Gate Failed for Lighthouse")
      }else{
         printf "$methodName Quality Gate Passed for Lighthouse"
      }
   }

   printf "$methodName End: qualityGateLighthouse(...)"
}