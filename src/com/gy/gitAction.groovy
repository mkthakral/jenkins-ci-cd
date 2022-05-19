package com.gy
import com.gy.utility

def cloneRepository(Map params) {
   
   //Params 
   def repoURL = params.repoURL

   //Variables
   def branch = env.branch
   String workspace = env.workspace
   def GIT_CLONE_TIMEOUT = 60
   
   //Property File
   def propFileContent = libraryResource 'common.properties'
   def propertyFile = readProperties text: propFileContent
   String alternateCheckoutDir = propertyFile['ALTERNATE_WORKSPACE_DIR']
   
   def checkoutDir = utility.getCheckoutDir(workspace, alternateCheckoutDir)
    
    //Echo Statements
    printf "checkoutDir: " + checkoutDir
    printf "branch: " + branch
    printf "repoURL: " + repoURL

    //Checkout Repository
    dir(checkoutDir) {
        checkout([
            $class: 'GitSCM',
            branches: [[name:  branch ]],
            userRemoteConfigs: [[ credentialsId: "gitlab", url: repoURL ]],
            extensions: [[$class: 'CloneOption', timeout: GIT_CLONE_TIMEOUT]]
        ])
    }
}
