package wood.fru

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution
import scala.sys.process._
import scala.io.Source
import scala.language.postfixOps

import wood.util.{GetBackendAnnotation, TestGenerateVerilog}
import wood.{TestConfig, WoodConfig}

class FrUnitSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  def generateTestCode(): Unit = {
    Process("sh -c \"python src/test/python/gen_li.py > src/test/c/src/main.S\"").!
  }

  def buildTestCode(): Unit = {
    val process = Process("make -C src/test/c") !
  }

  def readHexFileToList(filePath: String): List[String] = {
    val source = Source.fromFile(filePath)
    val lines  = source.getLines().toList
    source.close()
    lines
  }

  def groupHexLines(hexLines: List[String], numGroups: Int): List[List[String]] = {
    val groupedLines = Array.fill(numGroups)(List[String]())

    for ((line, index) <- hexLines.zipWithIndex) {
      groupedLines(index % numGroups) = groupedLines(index % numGroups) :+ line
    }

    val maxSize = hexLines.size / numGroups
    groupedLines.map(_.padTo(maxSize, "0")).toList
  }

  def hexStringToBigInt(hexString: String): BigInt = {
    val bigIntValue = BigInt(hexString, 16)
    bigIntValue
  }

  def hexStringToUInt(hexString: String): UInt = {
    val bigIntValue = BigInt(hexString, 16)
    val uintValue   = bigIntValue.U(32.W)
    uintValue
  }

  "FrUnit" should "work with li instructions" in {
    val process = Process("which python") !

    generateTestCode()
    buildTestCode()

    val nWide    = 1
    val prfDepth = 32
    val config   = new WoodConfig(nWide = nWide, prfDepth = prfDepth)

    val cwd = System.getProperty("user.dir")
    println(s"CWD: $cwd")

    val filePath     = "src/test/c/build/main.hex" // relative to build.sbt
    val hexLines     = readHexFileToList(filePath)
    val groupedUInts = groupHexLines(hexLines, nWide).map(_.map(hexStringToUInt))
    test(new FrUnit(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val in = dut.io.in.map(_.initSource())

      dut.io.pcIdx.bits.poke(0)
      dut.io.pcIdx.valid.poke(1)

      // (0 until config.nWide).foreach(j => {
      //   dut.io.in(j).bits.poke(groupedBitPats(0)(j))
      //   dut.io.in(j).valid.poke(1)
      // })

      (0 until config.nWide).foreach(j => {
        // dut.io.in(j).bits.poke(in(0))
        // dut.io.in(j).valid.poke(1)
        fork {
          dut.io.in(j).enqueueSeq(groupedUInts(j))
        }
      })

      println("fuck:     \n", groupedUInts(0).length)
      val pcs = Seq.range(0, groupedUInts(0).length, 1)
      val pcSeq: Seq[UInt] = pcs.map(i => i.asUInt)
      println("fuck:     \n", pcSeq)
      // fork {
      //   dut.io.pcIdx.enqueueSeq(pcSeq)
      // }

      fork {
        step(200)
      }.joinAndStep()
    }
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "FrUnit" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new FrUnit(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
