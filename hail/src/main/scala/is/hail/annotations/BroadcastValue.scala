package is.hail.annotations

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import is.hail.HailContext
import is.hail.backend.{Backend, BroadcastValue}
import is.hail.expr.ir.ExecuteContext
import is.hail.expr.types.physical.{PArray, PStruct, PType}
import is.hail.expr.types.virtual.{TBaseStruct, TStruct}
import is.hail.io.{BufferSpec, Decoder, TypedCodecSpec}
import org.apache.spark.sql.Row


case class SerializableRegionValue(encodedValue: Array[Byte], t: PType, makeDecoder: ByteArrayInputStream => Decoder) {
  def readRegionValue(r: Region): Long = {
    val dec = makeDecoder(new ByteArrayInputStream(encodedValue))
    val offset = dec.readRegionValue(r)
    dec.close()
    offset
  }
}

object BroadcastRow {
  def empty(ctx: ExecuteContext): BroadcastRow = apply(ctx, Row(), TStruct.empty)

  def apply(ctx: ExecuteContext, value: Row, t: TBaseStruct): BroadcastRow = {
    val pType = PType.literalPType(t, value).asInstanceOf[PStruct]
    val rvb = new RegionValueBuilder(ctx.r)
    rvb.start(pType)
    rvb.addAnnotation(t, value)
    val offset = rvb.end()
    BroadcastRow(RegionValue(ctx.r, offset), pType, HailContext.get.backend)
  }
}

trait BroadcastRegionValue {

  def value: RegionValue

  def t: PType

  def backend: Backend

  lazy val broadcast: BroadcastValue[SerializableRegionValue] = {
    val encoding = TypedCodecSpec(t, BufferSpec.wireSpec)
    val makeEnc = encoding.buildEncoder(t)
    val (decodedPType, makeDec) = encoding.buildDecoder(t.virtualType)
    assert(decodedPType == t)

    val baos = new ByteArrayOutputStream()

    val enc = makeEnc(baos)
    enc.writeRegionValue(value.offset)
    enc.flush()
    enc.close()

    val srv = SerializableRegionValue(baos.toByteArray, decodedPType, makeDec)
    backend.broadcast(srv)
  }

  def javaValue: Any

  def safeJavaValue: Any

  override def equals(obj: Any): Boolean = obj match {
    case b: BroadcastRegionValue => t == b.t && backend == b.backend && t.unsafeOrdering().compare(value, b.value) == 0
    case _ => false
  }

  override def hashCode(): Int = javaValue.hashCode()
}

case class BroadcastRow(value: RegionValue,
  t: PStruct,
  backend: Backend) extends BroadcastRegionValue {

  def javaValue: UnsafeRow = UnsafeRow.readBaseStruct(t, value.region, value.offset)

  def safeJavaValue: Row = SafeRow.read(t, value).asInstanceOf[Row]

  def cast(newT: PStruct): BroadcastRow = {
    assert(t.virtualType == newT.virtualType)
    if (t == newT)
      return this

    BroadcastRow(
      RegionValue(value.region, newT.copyFromAddress(value.region, t, value.offset, deepCopy = false)),
      newT,
      backend)
  }
}

case class BroadcastIndexedSeq(value: RegionValue,
  t: PArray,
  backend: Backend) extends BroadcastRegionValue {

  def safeJavaValue: IndexedSeq[Row] = SafeRow.read(t, value).asInstanceOf[IndexedSeq[Row]]

  def javaValue: UnsafeIndexedSeq = new UnsafeIndexedSeq(t, value.region, value.offset)

  def cast(newT: PArray): BroadcastIndexedSeq = {
    assert(t.virtualType == newT.virtualType)
    if (t == newT)
      return this

    BroadcastIndexedSeq(
      RegionValue(value.region, newT.copyFromAddress(value.region, t, value.offset, deepCopy = false)),
      newT,
      backend)
  }
}
