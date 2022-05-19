import com.gy.gitAction

def call(Map params) {
    def builder = new gitAction();
    builder.cloneRepository(params);
}