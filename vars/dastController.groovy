import com.gy.dastAction

def call(Map params) {
    def builder = new dastAction();
    builder.runDast(params);
}