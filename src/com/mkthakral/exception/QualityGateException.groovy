package com.mkthakral.exception

//Custom Exception for Quality Gates
class QualityGateException extends Exception{  
    QualityGateException(String message){  
        super(message); 
        printf "Quality Gate Error for: " + message
    }  
}