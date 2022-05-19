package com.mkthakral

def getUnitTestRsult(String workspacePath)
{
    println("START : Jacoco Unit Test Report creation")
    dir(workspacePath)
            {
                //jacoco classPattern: '**/custom/**/classes', exclusionPattern: '**/jalo/**/*.class,**/constants/**/*.class,**/dto/**/*.class,**/*DTO.class,**/integ/webservices/**/*.class,**/*Standalone.class,**/gensrc/**/*.class,**/cmscockpit/**/*.class,**/cscockpit/**/*.class,**/productcockpit/**/*.class,**/*Form.java,*/*Controller.java,**/Jalo/**/*.java,**/*Form.java', sourcePattern: '**/custom/**/src'
                junit 'hybris/temp/hybris/junit/*.xml'
            }

}

