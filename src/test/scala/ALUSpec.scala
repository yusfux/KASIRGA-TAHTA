package alu

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._

import chiseltest._
import chiseltest.ChiselScalatestTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random
import scala.math.log10

import scala.collection.mutable.Queue
import alu.ALUOp
import wood._

trait ALUBehavior {
  this: AnyFlatSpec with ChiselScalatestTester =>

  def testOperation(
    data:      Seq[(BigInt, BigInt)],
    dataWidth: Int,
    tagWidth:  Int,
    opWidth:   Int,
    op:        (BigInt, BigInt) => BigInt,
    fn:        ALUOp.Type
  ): Unit = {
    val mask: BigInt = (BigInt(1) << dataWidth) - 1
    var results = Queue[TagBus]()

    it should s"$fn on width:$dataWidth" in {
      test(new ALU(dataWidth, tagWidth, opWidth)) { dut =>
        dut.io.microOp.initSource()
        dut.io.tagBus.initSink()

        fork {
          for ((a, b) <- data) {
            val ua: BigInt = a & mask
            val ub: BigInt = b & mask
            val result = op(ua, ub) & mask
            val tagBus = new TagBus(dataWidth, tagWidth).Lit(
              _.data -> result.U(dataWidth.W),
              _.tag  -> fn.litValue.U
            )
            results.enqueue(tagBus)
          }
        }.fork {
          val microOps = data.map {
            case (a, b) =>
              val microOp = new MicroOperation(dataWidth, tagWidth, opWidth).Lit(
                _.data1 -> a.U,
                _.data2 -> b.U,
                _.tag   -> fn.litValue.U,
                _.op    -> fn.litValue.U
              )
              microOp
          }
          dut.io.microOp.enqueueSeq(microOps)
        }.fork {
          for ((expected, index) <- results.zipWithIndex) {
            try {
              dut.io.tagBus.expectDequeue(expected)
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
  val tagWidth = 5
  val opWidth  = log2Ceil(ALUOp.values.length)
  val dataWidths: List[Int] = List(32, 64)
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

    "ALU" should s"emit Verilog with dataWidth:$dataWidth, tagWidth:$tagWidth, opWidth:$opWidth" in {
      GenerateVerilog(new ALU(dataWidth, tagWidth, opWidth))
    }
  }
}
