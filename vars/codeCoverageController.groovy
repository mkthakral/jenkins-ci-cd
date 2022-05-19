import com.gy.codeCoverageAction

def call(Map params)
{
    def codeCoverageReporter = new codeCoverageAction()
    codeCoverageReporter.makeReport(params)
}