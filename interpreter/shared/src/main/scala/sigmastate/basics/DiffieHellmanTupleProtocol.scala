package sigmastate.basics

import java.math.BigInteger

import sigmastate.crypto.BigIntegers
import sigmastate.Values.Value.PropositionCode
import sigmastate._
import sigmastate.basics.VerifierMessage.Challenge
import sigmastate.eval.SigmaDsl
import CryptoConstants.EcPointType
import sigmastate.serialization.{OpCodes, GroupElementSerializer}
import sigmastate.serialization.OpCodes.OpCode
import special.sigma.SigmaProp


trait DiffieHellmanTupleProtocol extends SigmaProtocol[DiffieHellmanTupleProtocol] {
  override type A = FirstDiffieHellmanTupleProverMessage
  override type Z = SecondDiffieHellmanTupleProverMessage
}

case class DiffieHellmanTupleProverInput(w: BigInteger, commonInput: ProveDHTuple)
  extends SigmaProtocolPrivateInput[ProveDHTuple] {

  override lazy val publicImage: ProveDHTuple = commonInput
}

object DiffieHellmanTupleProverInput {

  import CryptoConstants.dlogGroup

  def random(): DiffieHellmanTupleProverInput = {
    val g = dlogGroup.generator
    val h = dlogGroup.createRandomGenerator()

    val qMinusOne = dlogGroup.order.subtract(BigInteger.ONE)
    val w = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, dlogGroup.secureRandom)
    val u = dlogGroup.exponentiate(g, w)
    val v = dlogGroup.exponentiate(h, w)
    val ci = ProveDHTuple(g, h, u, v)
    DiffieHellmanTupleProverInput(w, ci)
  }
}

//a = g^r, b = h^r
case class FirstDiffieHellmanTupleProverMessage(a: CryptoConstants.EcPointType, b: CryptoConstants.EcPointType)
  extends FirstProverMessage {

  override type SP = DiffieHellmanTupleProtocol

  override def bytes: Array[Byte] = {
    GroupElementSerializer.toBytes(a) ++ GroupElementSerializer.toBytes(b)
  }
}

//z = r + ew mod q
case class SecondDiffieHellmanTupleProverMessage(z: BigInteger) extends SecondProverMessage {

  override type SP = DiffieHellmanTupleProtocol

}

/** Construct a new SigmaProp value representing public key of Diffie Hellman signature protocol.
  * Common input: (g,h,u,v) */
case class ProveDHTuple(gv: EcPointType, hv: EcPointType, uv: EcPointType, vv: EcPointType)
  extends SigmaLeaf {
  override val opCode: OpCode = OpCodes.ProveDiffieHellmanTupleCode
  override def size: Int = 4  // one node for each EcPoint
  lazy val g = gv
  lazy val h = hv
  lazy val u = uv
  lazy val v = vv
}

object ProveDHTuple {
  val Code: PropositionCode = 103: Byte
}

/** Helper extractor to match SigmaProp values and extract ProveDHTuple out of it. */
object ProveDHTupleProp {
  def unapply(p: SigmaProp): Option[ProveDHTuple] = SigmaDsl.toSigmaBoolean(p) match {
    case d: ProveDHTuple => Some(d)
    case _ => None
  }
}

object DiffieHellmanTupleInteractiveProver {

  import CryptoConstants.dlogGroup

  def firstMessage(publicInput: ProveDHTuple): (BigInteger, FirstDiffieHellmanTupleProverMessage) = {
    val qMinusOne = dlogGroup.order.subtract(BigInteger.ONE)
    val r = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, dlogGroup.secureRandom)
    val a = dlogGroup.exponentiate(publicInput.g, r)
    val b = dlogGroup.exponentiate(publicInput.h, r)
    r -> FirstDiffieHellmanTupleProverMessage(a, b)
  }

  def secondMessage(privateInput: DiffieHellmanTupleProverInput,
                    rnd: BigInteger,
                    challenge: Challenge): SecondDiffieHellmanTupleProverMessage = {
    val q: BigInteger = dlogGroup.order
    val e: BigInteger = new BigInteger(1, challenge)
    val ew: BigInteger = e.multiply(privateInput.w).mod(q)
    val z: BigInteger = rnd.add(ew).mod(q)
    SecondDiffieHellmanTupleProverMessage(z)
  }

  def simulate(publicInput: ProveDHTuple, challenge: Challenge):
  (FirstDiffieHellmanTupleProverMessage, SecondDiffieHellmanTupleProverMessage) = {

    val qMinusOne = dlogGroup.order.subtract(BigInteger.ONE)

    //SAMPLE a random z <- Zq
    val z = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, dlogGroup.secureRandom)

    // COMPUTE a = g^z*u^(-e) and b = h^z*v^{-e}  (where -e here means -e mod q)
    val e: BigInteger = new BigInteger(1, challenge)
    val minusE = dlogGroup.order.subtract(e)
    val hToZ = dlogGroup.exponentiate(publicInput.h, z)
    val gToZ = dlogGroup.exponentiate(publicInput.g, z)
    val uToMinusE = dlogGroup.exponentiate(publicInput.u, minusE)
    val vToMinusE = dlogGroup.exponentiate(publicInput.v, minusE)
    val a = dlogGroup.multiplyGroupElements(gToZ, uToMinusE)
    val b = dlogGroup.multiplyGroupElements(hToZ, vToMinusE)
    FirstDiffieHellmanTupleProverMessage(a, b) -> SecondDiffieHellmanTupleProverMessage(z)
  }

  /**
    * The function computes initial prover's commitment to randomness
    * ("a" message of the sigma-protocol, which in this case has two parts "a" and "b")
    * based on the verifier's challenge ("e")
    * and prover's response ("z")
    *
    * g^z = a*u^e, h^z = b*v^e  => a = g^z/u^e, b = h^z/v^e
    *
    * @param proposition
    * @param challenge
    * @param secondMessage
    * @return
    */
  def computeCommitment(proposition: ProveDHTuple,
                        challenge: Challenge,
                        secondMessage: SecondDiffieHellmanTupleProverMessage): (EcPointType, EcPointType) = {

    val g = proposition.g
    val h = proposition.h
    val u = proposition.u
    val v = proposition.v

    val z = secondMessage.z

    val e = new BigInteger(1, challenge)

    val gToZ = dlogGroup.exponentiate(g, z)
    val hToZ = dlogGroup.exponentiate(h, z)

    val uToE = dlogGroup.exponentiate(u, e)
    val vToE = dlogGroup.exponentiate(v, e)

    val a = dlogGroup.multiplyGroupElements(gToZ, dlogGroup.inverseOf(uToE))
    val b = dlogGroup.multiplyGroupElements(hToZ, dlogGroup.inverseOf(vToE))
    a -> b
  }
}