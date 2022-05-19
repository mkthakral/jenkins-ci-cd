import com.mkthakral.configPackagingAction


def call(Map params)
{
    def configPackagingAction = new configPackagingAction()
    configPackagingAction.deployConfigPackage(params)
}
