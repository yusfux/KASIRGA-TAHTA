package wood

import scala.sys.process._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import scala.io.Source
import wood.WoodConfig
import wood.TestConfig
import scala.language.postfixOps

class WoodSpec extends AnyFlatSpec with ChiselScalatestTester {

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
  "Wood" should "work with li instructions" in {
    val process = Process("which python") !

    val nWide  = 4
    val config = new WoodConfig(nWide = nWide)

    val cwd = System.getProperty("user.dir")
    println(s"CWD: $cwd")

    val filePath       = "src/test/hex/li_test/li_test.hex" // PWD is where you run sbt, assume repo root
    val hexLines       = readHexFileToList(filePath)
    val groupedBitPats = groupHexLines(hexLines, nWide).map(_.map(hexStringToUInt))
    test(new Wood(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val in = dut.io.in.map(_.initSource())

      dut.io.pcIdx.bits.poke(0)
      dut.io.pcIdx.valid.poke(1)

      dut.io.in(0).bits.poke(groupedBitPats(0)(0))
      dut.io.in(1).bits.poke(groupedBitPats(0)(1))
      dut.io.in(2).bits.poke(groupedBitPats(0)(2))
      dut.io.in(3).bits.poke(groupedBitPats(0)(3))
      dut.io.in(0).valid.poke(1)
      dut.io.in(1).valid.poke(1)
      dut.io.in(2).valid.poke(1)
      dut.io.in(3).valid.poke(1)

      // groupedBitPats.zipWithIndex.foreach {
      //   case (in, i) =>
      //     (0 until numPorts).foreach(j => {
      //       dut.io.in(j).bits.poke(in(0))
      //       dut.io.in(j).valid.poke(1)
      //       fork {
      //         // in(i).enqueueSeq(bitPats)
      //       }
      //     })
      // }

      fork {
        step(100)
      }.joinAndStep()
    }
  }

  val tconfig = new TestConfig()

  (1 to tconfig.maxWidth).foreach(j => {
    "Wood" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new Wood(config), path = testRunDir)
    }
  })

}
