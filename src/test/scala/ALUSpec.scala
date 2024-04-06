package alu

import chisel3._
import chiseltest._
import chiseltest.ChiselScalatestTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random
import scala.math.log10
import scala.math.pow

import alu.ALUOp

trait ALUBehavior {
  this: AnyFlatSpec with ChiselScalatestTester =>

  def testOperation(a: BigInt, b: BigInt, dataWidth: Int, op: (BigInt, BigInt) => BigInt, fn: ALUOp.Type): Unit = {
    val mask: BigInt = (BigInt(1) << dataWidth) - 1
    val ua:   BigInt = a & mask
    val ub:   BigInt = b & mask
    val result = op(ua, ub) & mask
    it should s"$fn on 0x${a.toString(16)}, 0x${b.toString(16)}, width:$dataWidth and the result == 0x${result
      .toString(16)}" in {
      test(new ALU(dataWidth)) { c =>
        c.io.control.poke(fn)
        c.io.value1.poke(a.U(dataWidth.W))
        c.io.value2.poke(b.U(dataWidth.W))
        c.clock.step()
        val actual = c.io.result.peek().litValue
        assert(actual == result, s"Expected 0x${result.toString(16)}, but got 0x${actual.toString(16)}")
      }
    }
  }

}

class ALUSpec extends AnyFlatSpec with ALUBehavior with ChiselScalatestTester with Matchers {
  behavior.of("ALU")

  val dataWidths: List[Int] = List(32, 64)
  val numVectors: Int = 100 // Number of random test vectors
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

    testData.foreach { data =>
      operations.foreach { op =>
        (it should behave).like(testOperation(data._1, data._2, dataWidth, op._2, op._1))
      }
    }
  }
}
