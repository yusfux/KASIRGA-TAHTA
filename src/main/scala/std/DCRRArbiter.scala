package wood.std

import chisel3._
import chisel3.util._

// https://github.com/chipsalliance/chisel/pull/267

private object ArbiterCtrl {
  def apply(request: Seq[Bool]): Seq[Bool] = request.length match {
    case 0 => Seq()
    case 1 => Seq(true.B)
    case _ => true.B +: request.tail.init.scanLeft(request.head)(_ || _).map(!_)
  }
}

class LockingRRArbiter[T <: Data](gen: T, n: Int, count: Int, needsLock: Option[T => Bool] = None)
    extends LockingArbiterLike[T](gen, n, count, needsLock) {
  // this register is not initialized on purpose, see #267
  lazy val lastGrant = RegEnable(io.chosen, 0.U, io.out.fire)
  lazy val grantMask = (0 until n).map(_.asUInt > lastGrant)
  lazy val validMask = io.in.zip(grantMask).map { case (in, g) => in.valid && g }

  override def grant: Seq[Bool] = {
    val ctrl = ArbiterCtrl((0 until n).map(i => validMask(i)) ++ io.in.map(_.valid))
    (0 until n).map(i => ctrl(i) && grantMask(i) || ctrl(i + n))
  }

  override lazy val choice = WireDefault((n - 1).asUInt)
  for (i <- n - 2 to 0 by -1)
    when(io.in(i).valid) { choice := i.asUInt }
  for (i <- n - 1 to 1 by -1)
    when(validMask(i)) { choice := i.asUInt }
}

/** Hardware module that is used to sequence n producers into 1 consumer.
  * Producers are chosen in round robin order.
  *
  * @param gen data type
  * @param n number of inputs
  * @example {{{
  * val arb = Module(new RRArbiter(UInt(), 2))
  * arb.io.in(0) <> producer0.io.out
  * arb.io.in(1) <> producer1.io.out
  * consumer.io.in <> arb.io.out
  * }}}
  */
class DCRRArbiter[T <: Data](val gen: T, val n: Int) extends LockingRRArbiter[T](gen, n, 1)
