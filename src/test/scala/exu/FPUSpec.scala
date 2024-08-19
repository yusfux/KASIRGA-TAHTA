package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution
import wood.util.GenerateVerilog
import wood.WoodConfig

class FPUSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  "FPU" should s"emit Verilog" in {
    GenerateVerilog(new FPU(new WoodConfig))
  }
}


