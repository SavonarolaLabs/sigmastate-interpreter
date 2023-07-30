package sigmastate.basics

import java.math.BigInteger

import sigmastate.crypto.BigIntegers
import sigmastate.Values._
import Value.PropositionCode
import scorex.util.encode.Base16
import sigmastate._
import sigmastate.eval._
import sigmastate.basics.VerifierMessage.Challenge
import CryptoConstants.{EcPointType, dlogGroup}
import sigmastate.serialization.{OpCodes, GroupElementSerializer}
import sigmastate.serialization.OpCodes.OpCode
import special.sigma.SigmaProp

object DLogProtocol {

  trait DLogSigmaProtocol extends SigmaProtocol[DLogSigmaProtocol] {
    override type A = FirstDLogProverMessage
    override type Z = SecondDLogProverMessage
  }

  /** Construct a new SigmaBoolean value representing public key of discrete logarithm signature protocol. */
  case class ProveDlog(value: EcPointType) extends SigmaLeaf {
    override def size: Int = 1
    override val opCode: OpCode = OpCodes.ProveDlogCode
    /** Serialized bytes of the elliptic curve point (using GroupElementSerializer). */
    lazy val pkBytes: Array[Byte] = GroupElementSerializer.toBytes(value)
  }

  object ProveDlog {
    val Code: PropositionCode = 102: Byte
  }

  /** Helper extractor to match SigmaProp values and extract ProveDlog out of it. */
  object ProveDlogProp {
    def unapply(p: SigmaProp): Option[ProveDlog] = SigmaDsl.toSigmaBoolean(p) match {
      case d: ProveDlog => Some(d)
      case _ => None
    }
  }

  case class DLogProverInput(w: BigInteger)
    extends SigmaProtocolPrivateInput[ProveDlog] {

    import CryptoConstants.dlogGroup

    override lazy val publicImage: ProveDlog = {
      val g = dlogGroup.generator
      ProveDlog(dlogGroup.exponentiate(g, w))
    }
  }

  object DLogProverInput {

    import CryptoConstants.dlogGroup

    /** Create random secret in a range 0..q-1, where q - an order of DLog group. */
    def random(): DLogProverInput = {
      val qMinusOne = dlogGroup.order.subtract(BigInteger.ONE)
      val w = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, dlogGroup.secureRandom)
      DLogProverInput(w)
    }
  }

  case class FirstDLogProverMessage(ecData: EcPointType) extends FirstProverMessage {
    override type SP = DLogSigmaProtocol
    override def bytes: Array[Byte] = {
      GroupElementSerializer.toBytes(ecData)
    }

    override def toString = s"FirstDLogProverMessage(${Base16.encode(bytes)})"
  }

  case class SecondDLogProverMessage(z: BigInt) extends SecondProverMessage {
    override type SP = DLogSigmaProtocol
  }


  object DLogInteractiveProver extends SigmaProtocolProver {
    import CryptoConstants.secureRandom

    def firstMessage(): (BigInteger, FirstDLogProverMessage) = {
      import CryptoConstants.dlogGroup

      val qMinusOne = dlogGroup.order.subtract(BigInteger.ONE)
      val r = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, secureRandom)
      val a = dlogGroup.exponentiate(dlogGroup.generator, r)
      r -> FirstDLogProverMessage(a)
    }

    def secondMessage(privateInput: DLogProverInput, rnd: BigInteger, challenge: Challenge): SecondDLogProverMessage = {
      val z = responseToChallenge(privateInput, rnd, challenge)
      SecondDLogProverMessage(z)
    }

    /** Simulation of sigma protocol. */
    def simulate(publicInput: ProveDlog, challenge: Challenge): (FirstDLogProverMessage, SecondDLogProverMessage) = {
      val qMinusOne = dlogGroup.order.subtract(BigInteger.ONE)

      //SAMPLE a random z <- Zq
      val z = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, secureRandom)

      //COMPUTE a = g^z*h^(-e)  (where -e here means -e mod q)
      val e: BigInteger = new BigInteger(1, challenge.toArray)
      val minusE = dlogGroup.order.subtract(e)
      val hToE = dlogGroup.exponentiate(publicInput.value, minusE)
      val gToZ = dlogGroup.exponentiate(dlogGroup.generator, z)
      val a = dlogGroup.multiplyGroupElements(gToZ, hToE)
      FirstDLogProverMessage(a) -> SecondDLogProverMessage(z)
    }

    /**
      * The function computes initial prover's commitment to randomness
      * ("a" message of the sigma-protocol) based on the verifier's challenge ("e")
      * and prover's response ("z")
      *
      * g^z = a*h^e => a = g^z/h^e
      *
      * @param proposition
      * @param challenge
      * @param secondMessage
      * @return
      */
    def computeCommitment(proposition: ProveDlog,
                          challenge: Challenge,
                          secondMessage: SecondDLogProverMessage): EcPointType = {
      val g = dlogGroup.generator
      val h = proposition.value

      dlogGroup.multiplyGroupElements(
        dlogGroup.exponentiate(g, secondMessage.z.underlying()),
        dlogGroup.inverseOf(dlogGroup.exponentiate(h, new BigInteger(1, challenge.toArray))))
    }
  }

}
