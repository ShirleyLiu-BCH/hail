package is.hail.asm4s

abstract class SettableBuilder {
  def newSettable[T](name: String)(implicit tti: TypeInfo[T]): Settable[T]
}

object CodeBuilder {
  def apply(mb: MethodBuilder[_]): CodeBuilder = new CodeBuilder(mb, Code._empty)

  def apply(mb: MethodBuilder[_], code: Code[Unit]): CodeBuilder = new CodeBuilder(mb, code)

  def scoped[T](mb: MethodBuilder[_])(f: (CodeBuilder) => T): (Code[Unit], T) = {
    val cb = CodeBuilder(mb)
    val t = f(cb)
    (cb.result(), t)
  }

  def scopedCode[T](mb: MethodBuilder[_])(f: (CodeBuilder) => Code[T]): Code[T] = {
    val (cbcode, retcode) = CodeBuilder.scoped(mb)(f)
    Code(cbcode, retcode)
  }

  def scopedVoid[T](mb: MethodBuilder[_])(f: (CodeBuilder) => Unit): Code[Unit] = {
    val (cbcode, _) = CodeBuilder.scoped(mb)(f)
    cbcode
  }
}

trait CodeBuilderLike {
  def mb: MethodBuilder[_]

  def append(c: Code[Unit]): Unit

  def result(): Code[Unit]

  def localBuilder: SettableBuilder = mb.localBuilder

  def fieldBuilder: SettableBuilder = mb.fieldBuilder

  def +=(c: Code[Unit]): Unit = append(c)

  def assign[T](s: Settable[T], v: Code[T]): Unit = {
    append(s := v)
  }

  def assignAny[T](s: Settable[T], v: Code[_]): Unit = {
    append(s := coerce[T](v))
  }

  def ifx(c: Code[Boolean], emitThen: => Unit): Unit = {
    val Ltrue = CodeLabel()
    val Lafter = CodeLabel()
    append(c.mux(Ltrue.goto, Lafter.goto))
    append(Ltrue)
    emitThen
    append(Lafter)
  }

  def ifx(c: Code[Boolean], emitThen: => Unit, emitElse: => Unit): Unit = {
    val Ltrue = CodeLabel()
    val Lfalse = CodeLabel()
    val Lafter = CodeLabel()
    append(c.mux(Ltrue.goto, Lfalse.goto))
    append(Ltrue)
    emitThen
    append(Lafter.goto)
    append(Lfalse)
    emitElse
    append(Lafter)
  }

  def whileLoop(c: Code[Boolean], emitBody: => Unit): Unit = {
    val Lstart = CodeLabel()
    val Lbody = CodeLabel()
    val Lafter = CodeLabel()
    append(Lstart)
    append(c.mux(Lbody.goto, Lafter.goto))
    append(Lbody)
    emitBody
    append(Lstart.goto)
    append(Lafter)
  }

  def newLocal[T](name: String)(implicit tti: TypeInfo[T]): LocalRef[T] = mb.newLocal[T](name)

  def newLocal[T](name: String, c: Code[T])(implicit tti: TypeInfo[T]): LocalRef[T] = {
    val l = newLocal[T](name)
    append(l := c)
    l
  }

  def newLocalAny[T](name: String, c: Code[_])(implicit tti: TypeInfo[T]): LocalRef[T] =
    newLocal[T](name, coerce[T](c))

  def newField[T](name: String)(implicit tti: TypeInfo[T]): ThisFieldRef[T] = mb.genFieldThisRef[T](name)

  def newField[T](name: String, c: Code[T])(implicit tti: TypeInfo[T]): ThisFieldRef[T] = {
    val f = newField[T](name)
    append(f := c)
    f
  }

  def newFieldAny[T](name: String, c: Code[_])(implicit tti: TypeInfo[T]): ThisFieldRef[T] =
    newField[T](name, coerce[T](c))

  def goto(L: CodeLabel): Unit = {
    append(L.goto)
  }

  def define(L: CodeLabel): Unit = {
    append(L)
  }

  def _fatal(msg: Code[String]): Unit = {
    append(Code._fatal[Unit](msg))
  }

  def _throw[T <: java.lang.Throwable](cerr: Code[T]): Unit = {
    append(Code._throw[T, Unit](cerr))
  }
}

class CodeBuilder(val mb: MethodBuilder[_], var code: Code[Unit]) extends CodeBuilderLike {
  def append(c: Code[Unit]): Unit = {
    code = Code(code, c)
  }

  def result(): Code[Unit] = code
}
