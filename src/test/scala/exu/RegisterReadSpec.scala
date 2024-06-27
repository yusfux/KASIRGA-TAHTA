package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.GenerateVerilog
import wood.TestConfig

class RegisterReadSpec extends AnyFlatSpec with ChiselScalatestTester {
  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    "RegisterReadStage" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new RegisterReadStage(config), path = testRunDir)
    }
  })

}
