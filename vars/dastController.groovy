import com.mkthakral.dastAction

def call(Map params) {
    def builder = new dastAction();
    builder.runDast(params);
}