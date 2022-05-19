import com.mkthakral.automationTestAction

def call(Map params)
{
    def automationTestRunner = new automationTestAction()
    automationTestRunner.runTests(params)
}