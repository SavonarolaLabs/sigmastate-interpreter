package sigma.serialization

import scorex.util.serialization.Writer.Aux
import scorex.util.serialization.{VLQByteBufferWriter, Writer}
import sigma.ast.SType
import sigma.serialization.CoreByteWriter.{Bits, CostLimitChecker, DataInfo, U, Vlq, ZigZag}

/** Implementation of [[Writer]] provided by `sigma-core` module.
  *
  * @param w              destination [[Writer]] to which all the call got delegated.
  * @param checkCostLimitOpt callback to check if the cost limit at current writing position
  *                       is reached. The callback will throw an exception if the limit is reached.
  *                       Note, the cost of serialization is approximated using formula
  *                       `this.length * CostPerByte + InitialCost`
  */
class CoreByteWriter(val w: Writer, val checkCostLimitOpt: Option[CostLimitChecker]) extends Writer {
  type CH = w.CH

  /** Check the current writer length against the cost limit. */
  @inline protected def checkCostLimit(): Unit = {
    if (checkCostLimitOpt.isDefined)
      checkCostLimitOpt.get(this.length())
  }

  @inline override def length(): Int = w.length()

  @inline override def newWriter(): Aux[CH] = w.newWriter()

  @inline override def putChunk(chunk: CH): this.type = {
    w.putChunk(chunk);
    checkCostLimit()
    this
  }

  @inline override def result(): CH = w.result()

  @inline def put(x: Byte): this.type = {
    w.put(x);
    checkCostLimit()
    this
  }

  @inline def put(x: Byte, info: DataInfo[Byte]): this.type = {
    w.put(x);
    checkCostLimit()
    this
  }

  override def putUByte(x: Int): this.type = {
    super.putUByte(x)
  }
  def putUByte(x: Int, info: DataInfo[U[Byte]]): this.type = {
    super.putUByte(x)
  }

  @inline def putBoolean(x: Boolean): this.type = {
    w.putBoolean(x);
    checkCostLimit()
    this
  }

  @inline def putBoolean(x: Boolean, info: DataInfo[Boolean]): this.type = {
    w.putBoolean(x);
    checkCostLimit()
    this
  }

  @inline def putShort(x: Short): this.type = {
    w.putShort(x);
    checkCostLimit()
    this
  }

  @inline def putShort(x: Short, info: DataInfo[Short]): this.type = {
    w.putShort(x);
    checkCostLimit()
    this
  }

  @inline def putUShort(x: Int): this.type = {
    w.putUShort(x);
    checkCostLimit()
    this
  }

  @inline def putUShort(x: Int, info: DataInfo[Vlq[U[Short]]]): this.type = {
    w.putUShort(x);
    checkCostLimit()
    this
  }

  @inline def putInt(x: Int): this.type = {
    w.putInt(x);
    checkCostLimit()
    this
  }

  @inline def putInt(x: Int, info: DataInfo[Int]): this.type = {
    w.putInt(x);
    checkCostLimit()
    this
  }

  @inline def putUInt(x: Long): this.type = {
    w.putUInt(x);
    checkCostLimit()
    this
  }
  @inline def putUInt(x: Long, info: DataInfo[Vlq[U[Int]]]): this.type = {
    w.putUInt(x);
    checkCostLimit()
    this
  }

  @inline def putLong(x: Long): this.type = {
    w.putLong(x);
    checkCostLimit()
    this
  }
  @inline def putLong(x: Long, info: DataInfo[Vlq[ZigZag[Long]]]): this.type = {
    w.putLong(x);
    checkCostLimit()
    this
  }

  @inline def putULong(x: Long): this.type = {
    w.putULong(x);
    checkCostLimit()
    this
  }

  @inline def putULong(x: Long, info: DataInfo[Vlq[U[Long]]]): this.type = {
    w.putULong(x);
    checkCostLimit()
    this
  }

  override def putBytes(xs: Array[Byte],
                        offset: Int,
                        length: Int): this.type = {
    w.putBytes(xs, offset, length);
    checkCostLimit()
    this
  }

  @inline def putBytes(xs: Array[Byte]): this.type = {
    w.putBytes(xs);
    checkCostLimit()
    this
  }
  @inline def putBytes(xs: Array[Byte], info: DataInfo[Array[Byte]]): this.type = {
    w.putBytes(xs);
    checkCostLimit()
    this
  }

  /** Put the two bytes of the big-endian representation of the Short value into the
   * writer. */
  @inline def putShortBytes(value: Short): this.type = {
    w.put((value >> 8).toByte)
    w.put(value.toByte)
    checkCostLimit()
    this
  }

  @inline def putBits(xs: Array[Boolean]): this.type = {
    w.putBits(xs);
    checkCostLimit()
    this
  }
  @inline def putBits(xs: Array[Boolean], info: DataInfo[Bits]): this.type = {
    w.putBits(xs);
    checkCostLimit()
    this
  }

  @inline def putOption[T](x: Option[T])(putValueC: (this.type, T) => Unit): this.type = {
    w.putOption(x) { (_, v) =>
      putValueC(this, v)
    }
    checkCostLimit()
    this
  }

  @inline def putShortString(s: String): this.type = {
    w.putShortString(s);
    checkCostLimit()
    this
  }

  // TODO refactor: move to Writer
  @inline def toBytes: Array[Byte] = w match {
    case wr: VLQByteBufferWriter => wr.toBytes
  }

  @inline def putType[T <: SType](x: T): this.type = {
    TypeSerializer.serialize(x, this); // the cost is checked in TypeSerializer
    this
  }
  @inline def putType[T <: SType](x: T, info: DataInfo[SType]): this.type = {
    TypeSerializer.serialize(x, this); // the cost is checked in TypeSerializer
    this
  }

}

object CoreByteWriter {
  import scala.language.implicitConversions

  /** Callback type of cost limit checker. */
  type CostLimitChecker = Int => Unit

  /** Format descriptor type family. */
  trait FormatDescriptor[T] {
    /** Size formula associated with this format */
    def size: String
  }

  /** Marker type to automatically resolve correct implicit format descriptor
    * in Writer methods.
    * This is type-level type, since no instances of it are ever created. */
  trait Vlq[T]

  /** Marker type to automatically resolve correct implicit format descriptor
    * in Writer methods.
    * This is type-level type, since no instances of it are ever created. */
  trait ZigZag[T]

  /** Marker type for Unsigned types to automatically resolve correct implicit format descriptor
    * in Writer methods.
    * This is type-level type, since no instances of it are ever created. */
  trait U[T]

  /** Marker type for bits representation of Coll[Boolean].
    * Should be used only as argument for FormatDescriptor.
    * This is type-level type, since no instances of it are ever created.
    */
  trait Bits

  implicit case object TypeFmt extends FormatDescriptor[SType] {
    override def size: String = "[1, *]"
    override def toString: String = "Type"
  }

  case object BitsFmt extends FormatDescriptor[Bits] {
    override def size: String = "[1, *]"
    override def toString: String = "Bits"
  }

  case class MaxBitsFmt(maxBits: Int) extends FormatDescriptor[Bits] {
    override def size: String = {
      val maxBytes = (maxBits - 1) / 8 + 1
      if (maxBytes == 1) "1"
      else s"[1, $maxBytes]"
    }
    override def toString: String = "Bits"
  }

  implicit object BooleanFmt extends FormatDescriptor[Boolean] {
    override def size: String = "1"
    override def toString: String = "Boolean"
  }
  implicit object ByteFmt extends FormatDescriptor[Byte] {
    override def size: String = "1"
    override def toString: String = "Byte"
  }
  implicit object ShortFmt extends FormatDescriptor[Short] {
    override def size: String = "2"
    override def toString: String = "Short"
  }
  implicit object IntFmt extends FormatDescriptor[Int] {
    override def size: String = "4"
    override def toString: String = "Int"
  }
  implicit object LongFmt extends FormatDescriptor[Long] {
    override def size: String = "8"
    override def toString: String = "Long"
  }

  implicit object UByteFmt extends FormatDescriptor[U[Byte]] {
    override def size: String = "1"
    override def toString: String = "UByte"
  }
  implicit object UShortFmt extends FormatDescriptor[U[Short]] {
    override def size: String = "2"
    override def toString: String = "UShort"
  }
  implicit object UIntFmt extends FormatDescriptor[U[Int]] {
    override def size: String = "4"
    override def toString: String = "UInt"
  }
  implicit object ULongFmt extends FormatDescriptor[U[Long]] {
    override def size: String = "8"
    override def toString: String = "ULong"
  }

  case class ZigZagFmt[T](fmt: FormatDescriptor[T]) extends FormatDescriptor[ZigZag[T]] {
    override def size: String = s"[1, *]"
    override def toString: String = s"ZigZag($fmt)"
  }
  case class UVlqFmt[T](fmt: FormatDescriptor[U[T]]) extends FormatDescriptor[Vlq[U[T]]] {
    override def size: String = s"[1, *]"
    override def toString: String = s"VLQ($fmt)"
  }
  case class ZigZagVlqFmt[T](fmt: FormatDescriptor[ZigZag[T]]) extends FormatDescriptor[Vlq[ZigZag[T]]] {
    override def size: String = s"[1, *]"
    override def toString: String = s"VLQ($fmt)"
  }
  case class SeqFmt[T](fmt: FormatDescriptor[T]) extends FormatDescriptor[Seq[T]] {
    override def size: String = s"n * ${fmt.size}"
    override def toString: String = s"Seq($fmt)"
  }

  implicit def toZigZagFmt[T](implicit fmt: FormatDescriptor[T]): FormatDescriptor[ZigZag[T]] = ZigZagFmt(fmt)
  implicit def toUVlqFmt[T](implicit fmt: FormatDescriptor[U[T]]): FormatDescriptor[Vlq[U[T]]] = UVlqFmt(fmt)
  implicit def toZigZagVlqFmt[T](implicit fmt: FormatDescriptor[ZigZag[T]]): FormatDescriptor[Vlq[ZigZag[T]]] = ZigZagVlqFmt(fmt)
  implicit def toSeqFmt[T](implicit fmt: FormatDescriptor[T]): FormatDescriptor[Seq[T]] = SeqFmt(fmt)

  /** Meta information which can be attached to each argument of SMethod.
    *
    * @param name        name of the argument
    * @param description argument description. */
  case class ArgInfo(name: String, description: String)

  case class DataInfo[T](info: ArgInfo, format: FormatDescriptor[T])

  object DataInfo {
    implicit def argnfoToDataInfo[T](arg: ArgInfo)(implicit fmt: FormatDescriptor[T]): DataInfo[T] = DataInfo(arg, fmt)

    // TODO refactor: remove this conversion and make it explicit
    /** Helper conversion */
    implicit def nameToDataInfo[T](name: String)
        (implicit fmt: FormatDescriptor[T]): DataInfo[T] = ArgInfo(name, "")
  }

  def bitsInfo(name: String, desc: String = ""): DataInfo[Bits] = DataInfo(ArgInfo(name, desc), BitsFmt)
  def maxBitsInfo(name: String, maxBits: Int, desc: String = ""): DataInfo[Bits] = DataInfo(ArgInfo(name, desc), MaxBitsFmt(maxBits))

  val valuesLengthInfo: DataInfo[Vlq[U[Int]]] = ArgInfo("\\#items", "number of items in the collection")
}
