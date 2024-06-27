package wood.exu

import chisel3._
// import chisel3.util._

import chiseltest._
import chiseltest.ChiselScalatestTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random
import scala.math.log10

import scala.collection.mutable.Queue
import wood.exu.ALUOp
import wood.util.{GetBackendAnnotation}
import wood.fru.{DecodeConfig, MI}
import wood.WoodConfig
import wood.TestConfig
import wood.util.GenerateVerilog

trait ALUBehavior {
  this: AnyFlatSpec with ChiselScalatestTester =>

  val config = new WoodConfig()

  def testOperation(
    data:      Seq[(BigInt, BigInt)],
    dataWidth: Int,
    tagWidth:  Int,
    opWidth:   Int,
    op:        (BigInt, BigInt) => BigInt,
    fn:        ALUOp.Type
  ): Unit = {
    val mask: BigInt = (BigInt(1) << dataWidth) - 1
    var results = Queue[MI]()

    it should s"$fn on width:$dataWidth" in {
      test(new ALU(config)).withAnnotations(GetBackendAnnotation()) { dut =>
        dut.io.mi.initSource()
        dut.io.out.initSink()

        fork {
          for ((a, b) <- data) {
            val ua: BigInt = a & mask
            val ub: BigInt = b & mask
            val result = op(ua, ub) & mask
            val mi =
              MI(
                config,
                0.U,
                Map("rs1_data" -> a.U, "rs2_data" -> b.U, "exOp" -> fn.litValue.U, "rd_data" -> result.U(dataWidth.W))
              )
            results.enqueue(mi)
          }
        }.fork {
          val mis = data.map {
            case (a, b) =>
              val mi =
                MI(config, 0.U, Map("rs1_data" -> a.U, "rs2_data" -> b.U, "exOp" -> fn.litValue.U))
              mi
          }
          dut.io.mi.enqueueSeq(mis)
        }.fork {
          for ((expected, index) <- results.zipWithIndex) {
            try {
              dut.io.out.expectDequeue(expected)
            } catch {
              case e: Exception =>
                println("\u001b[31m" + s"${fn} on (0x${data(index)._1.toString(16)}, 0x${data(index)._2
                  .toString(16)}) at index: $index: ${e.getMessage}" + "\u001b[0m")
                throw new Exception()
            }
          }
        }.joinAndStep(dut.clock)
      }
    }
  }
}

class ALUSpec extends AnyFlatSpec with ALUBehavior with ChiselScalatestTester with Matchers {
  behavior.of("ALU")
  val tagWidth = config.tagWidth
  val opWidth  = DecodeConfig.op.maxWidth
  val dataWidths: List[Int] = List(config.dataWidth)
  val numVectors: Int       = 100 // Number of random test vectors
  val rand = new Random()

  dataWidths.foreach { dataWidth =>
    val randomData: List[(BigInt, BigInt)] =
      (1 to numVectors).map(_ => (BigInt(dataWidth, rand), BigInt(dataWidth, rand))).toList

    val cornerCasesList: List[BigInt] = List(
      BigInt(0), // 0
      BigInt(1), // 1
      BigInt(2).pow(dataWidth) - 1, // maxint
      BigInt(2).pow(dataWidth - 1), // minint
      BigInt("a" * (dataWidth / 4), 16), // aaaa
      BigInt("f" * (dataWidth / 4), 16), // ffff
      BigInt("5" * (dataWidth / 4), 16) // 5555
    )

    val shiftWidth: Int = if (dataWidth > 1) ((log10(dataWidth) / log10(2)).toInt) else 0

    def toSigned(x: BigInt, dataWidth: Int): BigInt = {
      val mask = (BigInt(1) << dataWidth) - 1
      val xInt = x & mask
      if ((xInt & (BigInt(1) << (dataWidth - 1))) != 0) xInt - (BigInt(1) << dataWidth) else xInt
    }

    val operations: List[(ALUOp.Type, (BigInt, BigInt) => BigInt)] = List(
      (ALUOp.add, _ + _),
      (ALUOp.sub, _ - _),
      (ALUOp.xor, _ ^ _),
      (ALUOp.or, _ | _),
      (ALUOp.and, _ & _),
      (ALUOp.sll, (a, b) => a << ((b & ((1 << shiftWidth) - 1)).toInt)),
      (ALUOp.srl, (a, b) => a >> ((b & ((1 << shiftWidth) - 1)).toInt)),
      (
        ALUOp.sra,
        (a, b) => {
          val aSigned = toSigned(a, dataWidth)
          aSigned >> ((b & ((1 << shiftWidth) - 1)).toInt)
        }
      ),
      (
        ALUOp.slt,
        (a, b) => {
          val aSigned = toSigned(a, dataWidth)
          val bSigned = toSigned(b, dataWidth)
          if (aSigned < bSigned) 1 else 0
        }
      ),
      (ALUOp.sltu, (a, b) => if (a < b) 1 else 0),
      (ALUOp.pass, (_, b) => b)
    )

    val cornerCases: List[(BigInt, BigInt)] = for {
      a <- cornerCasesList
      b <- cornerCasesList
    } yield (a, b)

    val testData: List[(BigInt, BigInt)] = (cornerCases ++ randomData).toSet.toList

    operations.foreach { op =>
      (it should behave).like(testOperation(testData, dataWidth, tagWidth, opWidth, op._2, op._1))
    }

  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    "ALU" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new ALU(config), path = testRunDir)
    }
  })

}
