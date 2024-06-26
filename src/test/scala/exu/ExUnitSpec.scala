package wood.exu

// import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import scala.io.Source

class ExUnitSpec extends AnyFlatSpec with ChiselScalatestTester {

  def readHexFileToList(filePath: String): List[String] = {
    val source = Source.fromFile(filePath)
    val lines  = source.getLines().toList
    source.close()
    lines
  }

  def groupHexLines(hexLines: List[String], numGroups: Int): List[List[String]] = {
    val expectedSize = hexLines.size / numGroups
    val groupedLines = hexLines.grouped(expectedSize).toList
    groupedLines.map(_.padTo(expectedSize, "0")) // Pad shorter lists with empty strings
  }

  // def hexStringToUInt(hexString: String): UInt = {
  def hexStringToUInt(hexString: String): BigInt = {
    val bigIntValue = BigInt(hexString, 16)
    // val uintValue   = bigIntValue.U(32.W)
    // uintValue
    bigIntValue
  }

  "ExUnit" should "work with li instructions" in {
    val cwd = System.getProperty("user.dir")
    println(s"CWD: $cwd")

    val numPorts       = 4
    val filePath       = "src/test/hex/li_test/li_test.hex" // PWD is where you run sbt, assume repo root
    val hexLines       = readHexFileToList(filePath)
    val groupedBitPats = groupHexLines(hexLines, numPorts).map(_.map(hexStringToUInt))
    test(new ExUnit(numPorts)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inst = dut.io.inst.map(_.initSource())

      dut.io.pcIdx.bits.poke(0)
      dut.io.pcIdx.valid.poke(1)

      dut.io.inst(0).bits.poke(groupedBitPats(0)(0))
      dut.io.inst(1).bits.poke(groupedBitPats(0)(1))
      dut.io.inst(2).bits.poke(groupedBitPats(0)(2))
      dut.io.inst(3).bits.poke(groupedBitPats(0)(3))
      dut.io.inst(0).valid.poke(1)
      dut.io.inst(1).valid.poke(1)
      dut.io.inst(2).valid.poke(1)
      dut.io.inst(3).valid.poke(1)

      // groupedBitPats.zipWithIndex.foreach {
      //   case (inst, i) =>
      //     (0 until numPorts).foreach(j => {
      //       dut.io.inst(j).bits.poke(inst(0))
      //       dut.io.inst(j).valid.poke(1)
      //       fork {
      //         // inst(i).enqueueSeq(bitPats)
      //       }
      //     })
      // }

      fork {
        step(100)
      }.joinAndStep()
    }
  }

  "ExUnit" should "emit Verilog" in {
    val numPorts = 4
    GenerateVerilog(new ExUnit(numPorts))
  }

}
