import com.mkthakral.runUnitTest

def call(Map params)
{
    def unitTestRunner = new runUnitTest()
    unitTestRunner.runUnitTest(params)
}