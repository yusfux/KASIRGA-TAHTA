/*
package wood.fru

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution
import scala.sys.process._
import scala.io.Source
import scala.language.postfixOps
import scala.collection.mutable.ListBuffer

import wood.util.{GetBackendAnnotation, TestGenerateVerilog}
import wood.{TestConfig, WoodConfig}
import chiseltest.internal.TesterThreadList

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
    generateTestCode()
    buildTestCode()

    val nWide        = 1
    val robDepth     = 32
    val miQueueDepth = 16
    val pcListDepth  = 16

    val config =
      new WoodConfig(nWide = nWide, robDepth = robDepth, miQueueDepth = miQueueDepth, pcListDepth = pcListDepth)

    val cwd = System.getProperty("user.dir")
    println(s"CWD: $cwd")

    val filePath     = "src/test/c/build/main.hex" // relative to build.sbt
    val hexLines     = readHexFileToList(filePath)
    val groupedUInts = groupHexLines(hexLines, nWide).map(_.map(hexStringToUInt))
    println("groupedUInts(0).length:     \n", groupedUInts(0).length)
    val pcSeq =
      Seq.range(0, groupedUInts(0).length, 1).map(i => i % ((1 << config.pcIndexWidth) - 1)).map(i => i.asUInt)
    println("pcSeq:     \n", pcSeq)
    test(new FrUnit(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val in = dut.io.in.map(_.initSource())

      val forks = ListBuffer[TesterThreadList]()
      (0 until config.nWide).foreach(j => {
        forks += fork { in(j).enqueueSeq(groupedUInts(j)) }
        forks += fork {
          (0 until config.nWide).foreach(j => {
            dut.io.out(j).ready.poke(1)
            step(50)
            dut.io.out(j).ready.poke(0)
            step(5)
            dut.io.out(j).ready.poke(1)
            step(50)
            dut.io.out(j).ready.poke(0)
            step(50)
            dut.io.out(j).ready.poke(1)
            step(50)
          })
        }
      })
      forks += fork { dut.io.pcIdx.enqueueSeq(pcSeq) }
      forks.map(_.join()).foreach(_ => ())
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
*/