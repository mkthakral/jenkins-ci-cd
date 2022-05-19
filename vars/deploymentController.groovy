import com.mkthakral.deploymentAction

def call(Map params)
{
    def deploymentActionRunner = new deploymentAction()
    deploymentActionRunner.deploy(params)
}

