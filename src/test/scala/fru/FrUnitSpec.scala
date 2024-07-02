package wood.fru

// import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.GenerateVerilog
import wood.{TestConfig, WoodConfig}
import wood.fru.FrUnit
import org.scalatest.ParallelTestExecution

class FrUnitSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    "FrUnit" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new FrUnit(config), path = testRunDir)
    }
  })
}
