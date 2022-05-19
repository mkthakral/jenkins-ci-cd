import com.mkthakral.hybrisAction

def call(Map params){
    def builder = new hybrisAction();
    if(params.action == "Compile"){
        builder.compileHybris(params);
    }else{
        builder.copyAndUpdateFiles(params);
    }
    
}