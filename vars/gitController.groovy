import com.mkthakral.gitAction

def call(Map params) {
    def builder = new gitAction();
    builder.cloneRepository(params);
}