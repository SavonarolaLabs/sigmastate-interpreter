package org.ergoplatform

import java.util

import org.ergoplatform.ErgoBox._
import scorex.util.encode.Base16
import scorex.crypto.hash.Digest32
import scorex.util.ModifierId
import sigmastate.Values._
import sigmastate._
import sigmastate.SType.AnyOps
import sigmastate.lang.Terms._
import sigmastate.serialization.{ErgoTreeSerializer, Serializer}
import sigmastate.utils.{SerializeLog, SigmaByteReader, SigmaByteWriter}
import sigmastate.utxo.CostTable.Cost

import scala.runtime.ScalaRunTime

class ErgoBoxCandidate(val value: Long,
                       val ergoTree: ErgoTree,
                       val creationHeight: Int,
                       val additionalTokens: Seq[(TokenId, Long)] = Seq(),
                       val additionalRegisters: Map[NonMandatoryRegisterId, _ <: EvaluatedValue[_ <: SType]] = Map()) {

  def proposition: BoolValue = ergoTree.proposition.asBoolValue

  def dataSize: Long = bytesWithNoRef.length.toLong

  lazy val cost: Int = (dataSize / 1024 + 1).toInt * Cost.BoxPerKilobyte

  lazy val propositionBytes: Array[Byte] = ErgoTreeSerializer.DefaultSerializer.serializeErgoTree(ergoTree)

  lazy val bytesWithNoRef: Array[Byte] = ErgoBoxCandidate.serializer.toBytes(this)

  def toBox(txId: ModifierId, boxIndex: Short) =
    ErgoBox(value, ergoTree, creationHeight, additionalTokens, additionalRegisters, txId, boxIndex)

  def get(identifier: RegisterId): Option[Value[SType]] = {
    identifier match {
      case ValueRegId => Some(LongConstant(value))
      case ScriptRegId => Some(ByteArrayConstant(propositionBytes))
      case TokensRegId =>
        val tokenTuples = additionalTokens.map { case (id, amount) =>
          Array(id, amount)
        }.toArray
        Some(Constant(tokenTuples.asWrappedType, STokensRegType))
      case ReferenceRegId =>
        val tupleVal = Array(creationHeight, Array.fill(34)(0: Byte))
        Some(Constant(tupleVal.asWrappedType, SReferenceRegType))
      case n: NonMandatoryRegisterId =>
        additionalRegisters.get(n)
    }
  }

  override def equals(arg: Any): Boolean = {
    arg match {
      case x: ErgoBoxCandidate => util.Arrays.equals(bytesWithNoRef, x.bytesWithNoRef)
      case _ => false
    }
  }

  override def hashCode(): Int =
    ScalaRunTime._hashCode((value, ergoTree, additionalTokens, additionalRegisters, creationHeight))

  override def toString: Idn = s"ErgoBoxCandidate($value, $ergoTree," +
    s"tokens: (${additionalTokens.map(t => Base16.encode(t._1)+":"+t._2).mkString(", ")}), " +
    s"$additionalRegisters, creationHeight: $creationHeight)"
}

object ErgoBoxCandidate {

  object serializer extends Serializer[ErgoBoxCandidate, ErgoBoxCandidate] {

    def serializeBodyWithIndexedDigests(obj: ErgoBoxCandidate,
                                        digestsInTx: Option[Array[Digest32]],
                                        w: SigmaByteWriter): Unit = {
      SerializeLog.logPrintf(true, true, false, "ErgoBoxCandidate")

      SerializeLog.logPrintf(true, true, false, "value")
      w.putULong(obj.value)
      SerializeLog.logPrintf(false, true, false, "value")

      SerializeLog.logPrintf(true, true, false, "ergoTree")
      w.putBytes(ErgoTreeSerializer.DefaultSerializer.serializeErgoTree(obj.ergoTree))
      SerializeLog.logPrintf(false, true, false, "ergoTree")

      SerializeLog.logPrintf(true, true, false, "creationHeight")
      w.putUInt(obj.creationHeight)
      SerializeLog.logPrintf(false, true, false, "creationHeight")

      SerializeLog.logPrintf(true, true, false, "additionalTokens.size")
      w.putUByte(obj.additionalTokens.size)
      SerializeLog.logPrintf(false, true, false, "additionalTokens.size")

      SerializeLog.logPrintf(true, true, false, "additionalTokens")
      obj.additionalTokens.foreach { case (id, amount) =>
        if (digestsInTx.isDefined) {
          val digestIndex = digestsInTx.get.indexOf(id)
          if (digestIndex == -1) sys.error(s"failed to find token id ($id) in tx's digest index")
          w.putUInt(digestIndex)
        } else {
          w.putBytes(id)
        }
        w.putULong(amount)
      }
      SerializeLog.logPrintf(false, true, false, "additionalTokens")


      val nRegs = obj.additionalRegisters.keys.size
      if (nRegs + ErgoBox.startingNonMandatoryIndex > 255)
        sys.error(s"The number of non-mandatory indexes $nRegs exceeds ${255 - ErgoBox.startingNonMandatoryIndex} limit.")

      SerializeLog.logPrintf(true, true, false, "nRegs")
      w.putUByte(nRegs)
      SerializeLog.logPrintf(false, true, false, "nRegs")

      // we assume non-mandatory indexes are densely packed from startingNonMandatoryIndex
      // this convention allows to save 1 bite for each register
      val startReg = ErgoBox.startingNonMandatoryIndex
      val endReg = ErgoBox.startingNonMandatoryIndex + nRegs - 1

      SerializeLog.logPrintf(true, true, false, "regs")
      for (regId <- startReg to endReg) {
        val reg = ErgoBox.findRegisterByIndex(regId.toByte).get
        obj.get(reg) match {
          case Some(v) =>
            w.putValue(v)
          case None =>
            sys.error(s"Set of non-mandatory indexes is not densely packed: " +
              s"register R$regId is missing in the range [$startReg .. $endReg]")
        }
      }
      SerializeLog.logPrintf(false, true, false, "regs")


      SerializeLog.logPrintf(false, true, false, "ErgoBoxCandidate")
    }

    override def serializeBody(obj: ErgoBoxCandidate, w: SigmaByteWriter): Unit = {
      serializeBodyWithIndexedDigests(obj, None, w)
    }

    def parseBodyWithIndexedDigests(digestsInTx: Option[Array[Digest32]], r: SigmaByteReader): ErgoBoxCandidate = {
      val value = r.getULong()
      val tree = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(r)
      val creationHeight = r.getUInt().toInt
      val addTokensCount = r.getByte()
      val addTokens = (0 until addTokensCount).map { _ =>
        val tokenId = if (digestsInTx.isDefined) {
          val digestIndex = r.getUInt().toInt
          if (!digestsInTx.get.isDefinedAt(digestIndex)) sys.error(s"failed to find token id with index $digestIndex")
          digestsInTx.get.apply(digestIndex)
        } else {
          Digest32 @@ r.getBytes(TokenId.size)
        }
        val amount = r.getULong()
        tokenId -> amount
      }
      val regsCount = r.getByte()
      val regs = (0 until regsCount).map { iReg =>
        val regId = ErgoBox.startingNonMandatoryIndex + iReg
        val reg = ErgoBox.findRegisterByIndex(regId.toByte).get.asInstanceOf[NonMandatoryRegisterId]
        val v = r.getValue().asInstanceOf[EvaluatedValue[SType]]
        (reg, v)
      }.toMap
      new ErgoBoxCandidate(value, tree, creationHeight, addTokens, regs)
    }

    override def parseBody(r: SigmaByteReader): ErgoBoxCandidate = {
      parseBodyWithIndexedDigests(None, r)
    }
  }
}
