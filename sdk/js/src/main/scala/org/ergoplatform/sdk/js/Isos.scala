package org.ergoplatform.sdk.js

import org.ergoplatform.ErgoBox._
import org.ergoplatform.{DataInput, ErgoBox, ErgoBoxCandidate, UnsignedErgoLikeTransaction, UnsignedInput}
import org.ergoplatform.sdk.{ExtendedInputBox, Iso}
import org.ergoplatform.sdk.JavaHelpers.UniversalConverter
import org.ergoplatform.sdk.wallet.protocol.context
import scalan.RType
import scorex.crypto.authds.{ADDigest, ADKey}
import scorex.util.ModifierId
import scorex.util.encode.Base16
import sigmastate.{AvlTreeData, AvlTreeFlags, SType}
import sigmastate.Values.{Constant, GroupElementConstant}
import sigmastate.eval.Extensions.ArrayOps
import sigmastate.eval.{CAvlTree, CBigInt, CHeader, CPreHeader, Colls, Digest32Coll, Evaluation}
import sigmastate.fleetSdkCommon.{distEsmTypesBoxesMod => boxesMod, distEsmTypesCommonMod => commonMod, distEsmTypesContextExtensionMod => contextExtensionMod, distEsmTypesInputsMod => inputsMod, distEsmTypesRegistersMod => registersMod, distEsmTypesTokenMod => tokenMod}
import sigmastate.interpreter.ContextExtension
import sigmastate.serialization.{ErgoTreeSerializer, ValueSerializer}
import special.collection.Coll
import special.collection.Extensions.CollBytesOps
import special.sigma
import special.sigma.GroupElement
import sigmastate.fleetSdkCommon.distEsmTypesBoxesMod.Box
import sigmastate.fleetSdkCommon.distEsmTypesCommonMod.HexString
import sigmastate.fleetSdkCommon.distEsmTypesRegistersMod.NonMandatoryRegisters
import sigmastate.fleetSdkCommon.distEsmTypesTokenMod.TokenAmount
import sigmastate.fleetSdkCommon.distEsmTypesTransactionsMod.UnsignedTransaction

import java.math.BigInteger
import scala.collection.immutable.ListMap
import scala.reflect.ClassTag
import scala.scalajs.js
import scala.scalajs.js.JSConverters.JSRichOption
import scala.scalajs.js.Object

/** Definitions of isomorphisms. */
object Isos {
  /** Conversion between `Value` and `Constant[SType]`. */
  implicit val isoValueToConstant: Iso[Value, Constant[SType]] = new Iso[Value, Constant[SType]] {
    override def to(x: Value): Constant[SType] =
      Constant(x.runtimeData.asInstanceOf[SType#WrappedType], Evaluation.rtypeToSType(x.tpe.rtype))

    override def from(x: Constant[SType]): Value = {
      val rtype = Evaluation.stypeToRType(x.tpe)
      val jsvalue = Value.fromRuntimeData(x.value, rtype)
      new Value(jsvalue, new Type(rtype))
    }
  }

  val isoStringToArray: Iso[String, Array[Byte]] = new Iso[String, Array[Byte]] {
    override def to(x: String): Array[Byte] = Base16.decode(x).get
    override def from(x: Array[Byte]): String = Base16.encode(x)
  }

  val isoStringToColl: Iso[String, Coll[Byte]] = new Iso[String, Coll[Byte]] {
    override def to(x: String): Coll[Byte] = Colls.fromArray(Base16.decode(x).get)
    override def from(x: Coll[Byte]): String = x.toHex
  }

  val isoStringToGroupElement: Iso[String, GroupElement] = new Iso[String, GroupElement] {
    override def to(x: String): GroupElement = {
      val bytes = Base16.decode(x).get
      ValueSerializer.deserialize(bytes).asInstanceOf[GroupElementConstant].value
    }
    override def from(x: GroupElement): String = {
      val bytes = ValueSerializer.serialize(GroupElementConstant(x))
      Base16.encode(bytes)
    }
  }

  implicit val isoBoxId: Iso[boxesMod.BoxId, ErgoBox.BoxId] = new Iso[boxesMod.BoxId, ErgoBox.BoxId] {
    override def to(x: boxesMod.BoxId): ErgoBox.BoxId = ADKey @@@ isoStringToArray.to(x)

    override def from(x: ErgoBox.BoxId): boxesMod.BoxId = isoStringToArray.from(x)
  }

  implicit val isoHexStringToConstant: Iso[HexString, Constant[SType]] = new Iso[HexString, Constant[SType]] {
    override def to(x: HexString): Constant[SType] = {
      val bytes = isoStringToArray.to(x)
      val value = ValueSerializer.deserialize(bytes)
      value.asInstanceOf[Constant[SType]]
    }
    override def from(x: Constant[SType]): HexString = {
      val bytes = ValueSerializer.serialize(x)
      isoStringToArray.from(bytes)
    }
  }

  implicit val isoAvlTree: Iso[AvlTree, sigma.AvlTree] = new Iso[AvlTree, sigma.AvlTree] {
    override def to(x: AvlTree): sigma.AvlTree = {
      CAvlTree(
        AvlTreeData(
          digest = isoStringToArray.to(x.digest).toColl,
          treeFlags = AvlTreeFlags(x.insertAllowed, x.updateAllowed, x.removeAllowed),
          x.keyLength,
          valueLengthOpt = isoUndefOr(Iso.identityIso[Int]).to(x.valueLengthOpt),
        ),
      )
    }
    override def from(x: sigma.AvlTree): AvlTree = {
      val tree = x.asInstanceOf[CAvlTree]
      val data = tree.treeData
      new AvlTree(
        digest = isoStringToColl.from(tree.digest),
        insertAllowed = data.treeFlags.insertAllowed,
        updateAllowed = data.treeFlags.updateAllowed,
        removeAllowed = data.treeFlags.removeAllowed,
        keyLength = data.keyLength,
        valueLengthOpt = isoUndefOr(Iso.identityIso[Int]).from(data.valueLengthOpt),
      )
    }
  }

  implicit val isoHeader: Iso[Header, special.sigma.Header] = new Iso[Header, special.sigma.Header] {
    override def to(a: Header): sigma.Header = {
      CHeader(
        id = isoStringToColl.to(a.id),
        version = a.version,
        parentId = isoStringToColl.to(a.parentId),
        ADProofsRoot = isoStringToColl.to(a.ADProofsRoot),
        stateRoot = isoAvlTree.to(a.stateRoot),
        transactionsRoot = isoStringToColl.to(a.transactionsRoot),
        timestamp = isoBigIntToLong.to(a.timestamp),
        nBits = isoBigIntToLong.to(a.nBits),
        height = a.height,
        extensionRoot = isoStringToColl.to(a.extensionRoot),
        minerPk = isoStringToGroupElement.to(a.minerPk),
        powOnetimePk = isoStringToGroupElement.to(a.powOnetimePk),
        powNonce = isoStringToColl.to(a.powNonce),
        powDistance = isoBigInt.to(a.powDistance),
        votes = isoStringToColl.to(a.votes)
      )
    }
    override def from(b: sigma.Header): Header = {
      val header = b.asInstanceOf[CHeader]
      new Header(
        id = isoStringToColl.from(header.id),
        version = header.version,
        parentId = isoStringToColl.from(header.parentId),
        ADProofsRoot = isoStringToColl.from(header.ADProofsRoot),
        stateRoot = isoAvlTree.from(header.stateRoot),
        transactionsRoot = isoStringToColl.from(header.transactionsRoot),
        timestamp = isoBigIntToLong.from(header.timestamp),
        nBits = isoBigIntToLong.from(header.nBits),
        height = header.height,
        extensionRoot = isoStringToColl.from(header.extensionRoot),
        minerPk = isoStringToGroupElement.from(header.minerPk),
        powOnetimePk = isoStringToGroupElement.from(header.powOnetimePk),
        powNonce = isoStringToColl.from(header.powNonce),
        powDistance = isoBigInt.from(header.powDistance),
        votes = isoStringToColl.from(header.votes)
      )
    }
  }

  implicit val isoPreHeader: Iso[PreHeader, special.sigma.PreHeader] = new Iso[PreHeader, special.sigma.PreHeader] {
    override def to(a: PreHeader): sigma.PreHeader = {
      CPreHeader(
        version = a.version,
        parentId = isoStringToColl.to(a.parentId),
        timestamp = isoBigIntToLong.to(a.timestamp),
        nBits = isoBigIntToLong.to(a.nBits),
        height = a.height,
        minerPk = isoStringToGroupElement.to(a.minerPk),
        votes = isoStringToColl.to(a.votes)
      )
    }
    override def from(b: sigma.PreHeader): PreHeader = {
      val header = b.asInstanceOf[CPreHeader]
      new PreHeader(
        version = header.version,
        parentId = isoStringToColl.from(header.parentId),
        timestamp = isoBigIntToLong.from(header.timestamp),
        nBits = isoBigIntToLong.from(header.nBits),
        height = header.height,
        minerPk = isoStringToGroupElement.from(header.minerPk),
        votes = isoStringToColl.from(header.votes)
      )
    }
  }

  implicit val isoBlockchainStateContext: Iso[BlockchainStateContext, context.BlockchainStateContext] = new Iso[BlockchainStateContext, context.BlockchainStateContext] {
    override def to(a: BlockchainStateContext): context.BlockchainStateContext = {
      context.BlockchainStateContext(
        sigmaLastHeaders = isoArrayToColl(isoHeader).to(a.sigmaLastHeaders),
        previousStateDigest = isoStringToColl.to(a.previousStateDigest),
        sigmaPreHeader = isoPreHeader.to(a.sigmaPreHeader)
      )
    }

    override def from(b: context.BlockchainStateContext): BlockchainStateContext = {
      new BlockchainStateContext(
        sigmaLastHeaders = isoArrayToColl(isoHeader).from(b.sigmaLastHeaders),
        previousStateDigest = isoStringToColl.from(b.previousStateDigest),
        sigmaPreHeader = isoPreHeader.from(b.sigmaPreHeader)
      )
    }
  }

  implicit val isoContextExtension: Iso[contextExtensionMod.ContextExtension, ContextExtension] = new Iso[contextExtensionMod.ContextExtension, ContextExtension] {
    override def to(x: contextExtensionMod.ContextExtension): ContextExtension = {
      var map = new ListMap[Byte, Constant[SType]]()
      val keys = js.Object.keys(x)
      for ( k <- keys ) {
        val id = k.toInt.toByte
        val c = isoHexStringToConstant.to(x.apply(id).get.get)
        map = map + (id -> c)
      }
      ContextExtension(map)
    }

    override def from(x: ContextExtension): contextExtensionMod.ContextExtension = {
      val res = new Object().asInstanceOf[contextExtensionMod.ContextExtension]
      x.values.foreach { case (k, v: Constant[_]) =>
        val hex = isoHexStringToConstant.from(v)
        res.update(k, hex)
      }
      res
    }
  }

  implicit val isoUnsignedInput: Iso[inputsMod.UnsignedInput, UnsignedInput] = new Iso[inputsMod.UnsignedInput, UnsignedInput] {
    override def to(x: inputsMod.UnsignedInput): UnsignedInput =
      new UnsignedInput(x.boxId.convertTo[ErgoBox.BoxId], isoContextExtension.to(x.extension))

    override def from(x: UnsignedInput): inputsMod.UnsignedInput =
      inputsMod.UnsignedInput(x.boxId.convertTo[boxesMod.BoxId], isoContextExtension.from(x.extension))
  }

  implicit val isoDataInput: Iso[inputsMod.DataInput, DataInput] = new Iso[inputsMod.DataInput, DataInput] {
    override def to(x: inputsMod.DataInput): DataInput = DataInput(x.boxId.convertTo[ErgoBox.BoxId])

    override def from(x: DataInput): inputsMod.DataInput = inputsMod.DataInput(x.boxId.convertTo[boxesMod.BoxId])
  }

  implicit val isoBigInt: Iso[js.BigInt, special.sigma.BigInt] = new Iso[js.BigInt, special.sigma.BigInt] {
    override def to(x: js.BigInt): sigma.BigInt = {
      CBigInt(new BigInteger(x.toString(10)))
    }
    override def from(x: sigma.BigInt): js.BigInt = {
      val bi = x.asInstanceOf[CBigInt].wrappedValue
      val s = bi.toString(10)
      js.BigInt(s)
    }
  }

  implicit val isoBigIntToLong: Iso[js.BigInt, Long] = new Iso[js.BigInt, Long] {
    override def to(x: js.BigInt): Long = java.lang.Long.parseLong(x.toString(10))
    override def from(x: Long): js.BigInt = js.BigInt(x.toString)
  }

  implicit val isoAmount: Iso[commonMod.Amount, Long] = new Iso[commonMod.Amount, Long] {
    override def to(x: commonMod.Amount): Long = x.asInstanceOf[Any] match {
      case s: String => BigInt(s).toLong
      case _ => java.lang.Long.parseLong(x.asInstanceOf[js.BigInt].toString(10))
    }
    override def from(x: Long): commonMod.Amount = x.toString
  }

  implicit val isoToken: Iso[tokenMod.TokenAmount[commonMod.Amount], Token] =
    new Iso[tokenMod.TokenAmount[commonMod.Amount], Token] {
      override def to(x: tokenMod.TokenAmount[commonMod.Amount]): Token =
        (Digest32Coll @@@ Colls.fromArray(Base16.decode(x.tokenId).get), isoAmount.to(x.amount))

      override def from(x: Token): tokenMod.TokenAmount[commonMod.Amount] =
        tokenMod.TokenAmount[commonMod.Amount](isoAmount.from(x._2), x._1.toHex)
    }

  implicit def isoUndefOr[A, B](implicit iso: Iso[A, B]): Iso[js.UndefOr[A], Option[B]] = new Iso[js.UndefOr[A], Option[B]] {
    override def to(x: js.UndefOr[A]): Option[B] = x.toOption.map(iso.to)
    override def from(x: Option[B]): js.UndefOr[A] = x.map(iso.from).orUndefined
  }

  implicit def isoArrayToColl[A, B](iso: Iso[A, B])(implicit ctA: ClassTag[A], tB: RType[B]): Iso[js.Array[A], Coll[B]] = new Iso[js.Array[A], Coll[B]] {
    override def to(x: js.Array[A]): Coll[B] = Colls.fromArray(x.map(iso.to).toArray(tB.classTag))
    override def from(x: Coll[B]): js.Array[A] = js.Array(x.toArray.map(iso.from):_*)
  }

  implicit def isoArrayToIndexed[A, B](iso: Iso[A, B])(implicit cB: ClassTag[B]): Iso[js.Array[A], IndexedSeq[B]] = new Iso[js.Array[A], IndexedSeq[B]] {
    override def to(x: js.Array[A]): IndexedSeq[B] = x.map(iso.to).toArray(cB).toIndexedSeq
    override def from(x: IndexedSeq[B]): js.Array[A] = js.Array(x.map(iso.from):_*)
  }

  val isoTokenArray: Iso[js.Array[tokenMod.TokenAmount[commonMod.Amount]], Coll[Token]] =
    new Iso[js.Array[tokenMod.TokenAmount[commonMod.Amount]], Coll[Token]] {
      override def to(x: js.Array[tokenMod.TokenAmount[commonMod.Amount]]): Coll[Token] = {
        isoArrayToColl(isoToken).to(x)
      }
      override def from(x: Coll[Token]): js.Array[tokenMod.TokenAmount[commonMod.Amount]] = {
        isoArrayToColl(isoToken).from(x)
      }
    }

  val isoNonMandatoryRegisters: Iso[registersMod.NonMandatoryRegisters, AdditionalRegisters] =
    new Iso[registersMod.NonMandatoryRegisters, AdditionalRegisters] {
      override def to(x: registersMod.NonMandatoryRegisters): AdditionalRegisters = {
        val regs = Seq(
          x.R4 -> R4,
          x.R5 -> R5,
          x.R6 -> R6,
          x.R7 -> R7,
          x.R8 -> R8,
          x.R9 -> R9
        ).collect {
          case (regOpt, id) if regOpt.isDefined => id -> isoHexStringToConstant.to(regOpt.get)
        }
        Map(regs:_*)
      }
      override def from(regs: AdditionalRegisters): registersMod.NonMandatoryRegisters = {
        def regHexOpt(t: NonMandatoryRegisterId): Option[HexString] =
          regs.get(t).map(v => isoHexStringToConstant.from(v.asInstanceOf[Constant[SType]]))

        val resRegs = NonMandatoryRegisters()
        regHexOpt(R4).foreach(resRegs.setR4(_))
        regHexOpt(R5).foreach(resRegs.setR5(_))
        regHexOpt(R6).foreach(resRegs.setR6(_))
        regHexOpt(R7).foreach(resRegs.setR7(_))
        regHexOpt(R8).foreach(resRegs.setR8(_))
        regHexOpt(R9).foreach(resRegs.setR9(_))
        resRegs
      }
    }

  implicit val isoBoxCandidate: Iso[boxesMod.BoxCandidate[commonMod.Amount], ErgoBoxCandidate] = new Iso[boxesMod.BoxCandidate[commonMod.Amount], ErgoBoxCandidate] {
    override def to(x: boxesMod.BoxCandidate[commonMod.Amount]): ErgoBoxCandidate = {
      val ergoBoxCandidate = new ErgoBoxCandidate(
        value = isoAmount.to(x.value),
        ergoTree = {
          val bytes = Base16.decode(x.ergoTree).get
          ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(bytes)
        },
        x.creationHeight.toInt,
        additionalTokens = isoTokenArray.to(x.assets),
        additionalRegisters = isoNonMandatoryRegisters.to(x.additionalRegisters)
      )
      ergoBoxCandidate
    }

    override def from(x: ErgoBoxCandidate): boxesMod.BoxCandidate[commonMod.Amount] = {
      val ergoTree = ErgoTreeSerializer.DefaultSerializer.serializeErgoTree(x.ergoTree)
      val ergoTreeStr = Base16.encode(ergoTree)
      val assets = isoTokenArray.from(x.additionalTokens)
      boxesMod.BoxCandidate[commonMod.Amount](
        ergoTree = ergoTreeStr,
        value = isoAmount.from(x.value),
        assets = assets,
        creationHeight = x.creationHeight,
        additionalRegisters = isoNonMandatoryRegisters.from(x.additionalRegisters)
      )
    }
  }

  // Implements Iso between UnsignedTransaction and UnsignedErgoLikeTransaction
  val isoUnsignedTransaction: Iso[UnsignedTransaction, UnsignedErgoLikeTransaction] =
    new Iso[UnsignedTransaction, UnsignedErgoLikeTransaction] {
      override def to(a: UnsignedTransaction): UnsignedErgoLikeTransaction = {
        new UnsignedErgoLikeTransaction(
          inputs = isoArrayToIndexed(isoUnsignedInput).to(a.inputs),
          dataInputs = isoArrayToIndexed(isoDataInput).to(a.dataInputs),
          outputCandidates = isoArrayToIndexed(isoBoxCandidate).to(a.outputs),
        )
      }
      override def from(b: UnsignedErgoLikeTransaction): UnsignedTransaction = {
        UnsignedTransaction(
          inputs = isoArrayToIndexed(isoUnsignedInput).from(b.inputs),
          dataInputs = isoArrayToIndexed(isoDataInput).from(b.dataInputs),
          outputs = isoArrayToIndexed(isoBoxCandidate).from(b.outputCandidates)
        )
      }
    }

  val isoBox: Iso[Box[commonMod.Amount], ErgoBox] = new Iso[Box[commonMod.Amount], ErgoBox] {
    override def to(x: Box[commonMod.Amount]): ErgoBox = {
      val ergoBox = new ErgoBox(
        value = isoAmount.to(x.value),
        ergoTree = {
          val bytes = Base16.decode(x.ergoTree).get
          ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(bytes)
        },
        creationHeight = x.creationHeight.toInt,
        additionalTokens = isoTokenArray.to(x.assets),
        additionalRegisters = isoNonMandatoryRegisters.to(x.additionalRegisters),
        transactionId = ModifierId @@ x.transactionId,
        index = x.index.toShort
      )
      ergoBox
    }

    override def from(x: ErgoBox): Box[commonMod.Amount] = {
      val ergoTree = ErgoTreeSerializer.DefaultSerializer.serializeErgoTree(x.ergoTree)
      val ergoTreeStr = Base16.encode(ergoTree)
      val assets = isoTokenArray.from(x.additionalTokens)
      Box[commonMod.Amount](
        boxId = Base16.encode(x.id),
        ergoTree = ergoTreeStr,
        value = isoAmount.from(x.value),
        assets = assets,
        creationHeight = x.creationHeight,
        additionalRegisters = isoNonMandatoryRegisters.from(x.additionalRegisters),
        transactionId = x.transactionId,
        index = x.index
      )
    }
  }

  val isoEIP12UnsignedInput: Iso[inputsMod.EIP12UnsignedInput, ExtendedInputBox] =
    new Iso[inputsMod.EIP12UnsignedInput, ExtendedInputBox] {
      override def to(x: inputsMod.EIP12UnsignedInput): ExtendedInputBox = {
        val box = Box[commonMod.Amount](
          boxId = x.boxId,
          ergoTree = x.ergoTree,
          value = x.value,
          assets = x.assets.asInstanceOf[js.Array[TokenAmount[commonMod.Amount]]],
          creationHeight = x.creationHeight,
          additionalRegisters = x.additionalRegisters,
          transactionId = x.transactionId,
          index = x.index
        )
        val ergoBox = isoBox.to(box)
        val extendedInputBox = ExtendedInputBox(ergoBox, isoContextExtension.to(x.extension))
        extendedInputBox
      }
      override def from(x: ExtendedInputBox): inputsMod.EIP12UnsignedInput = {
        val box = isoBox.from(x.box)
        val ext = isoContextExtension.from(x.extension)
        inputsMod.EIP12UnsignedInput(
          boxId = box.boxId,
          ergoTree = box.ergoTree,
          value = box.value.toString,
          assets = box.assets.asInstanceOf[js.Array[TokenAmount[String]]],
          creationHeight = box.creationHeight,
          additionalRegisters = box.additionalRegisters,
          transactionId = box.transactionId,
          index = box.index,
          extension = ext
        )
      }
    }
}