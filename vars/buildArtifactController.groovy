import com.gy.buildArtifactAction


def call(Map params)
{
    def buildArtifactAction = new buildArtifactAction()
    buildArtifactAction.buildArtifact(params)
}
