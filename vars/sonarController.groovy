import com.gy.sonarAction

def call(Map params) {
    def builder = new sonarAction();
    builder.runSonar(params);
}