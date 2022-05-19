import com.gy.configPackagingAction


def call(Map params)
{
    def configPackagingAction = new configPackagingAction()
    configPackagingAction.deployConfigPackage(params)
}
