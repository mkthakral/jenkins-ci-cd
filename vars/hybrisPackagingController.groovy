import com.gy.hybrisPackagingAction


def call(Map params)
{
    def hybrisPackagingAction = new hybrisPackagingAction()
    hybrisPackagingAction.buildHybrisCodePackage(params)
}
