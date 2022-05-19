import com.mkthakral.sonarAction

def call(Map params) {
    def builder = new sonarAction();
    builder.runSonar(params);
}