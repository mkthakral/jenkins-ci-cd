package com.mkthakral.utility

def readPropertyFile(String environment)
{
    def propFileContent = libraryResource environment + '.properties'
    println(propFileContent)
    def properties = readProperties text: propFileContent
    return properties
}