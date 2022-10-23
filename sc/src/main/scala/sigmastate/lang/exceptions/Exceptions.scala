package sigmastate.lang.exceptions

import sigmastate.JitCost
import sigmastate.lang.SourceContext

class SigmaException(val message: String, val cause: Option[Throwable] = None)
    extends Exception(message, cause.orNull) {
}

class CompilerException(message: String, val source: Option[SourceContext] = None, cause: Option[Throwable] = None)
    extends SigmaException(message, cause) {

  override def getMessage: String = source.map { srcCtx =>
    val lineNumberStrPrefix = s"line ${srcCtx.line}: "
    "\n" + lineNumberStrPrefix +
      s"${srcCtx.sourceLine}\n${" " * (lineNumberStrPrefix.length + srcCtx.column - 1)}^\n" + message
  }.getOrElse(message)
}

class BinderException(message: String, source: Option[SourceContext] = None)
    extends CompilerException(message, source)

class TyperException(message: String, source: Option[SourceContext] = None)
    extends CompilerException(message, source)

case class SerializerException(
  override val message: String,
  override val cause: Option[Throwable] = None)
  extends SigmaException(message, cause)

class BuilderException(message: String, source: Option[SourceContext] = None)
  extends CompilerException(message, source)

class CosterException(message: String, source: Option[SourceContext], cause: Option[Throwable] = None)
    extends CompilerException(message, source, cause)

class InterpreterException(message: String, cause: Option[Throwable] = None)
  extends SigmaException(message, cause)

class CostLimitException(val estimatedCost: Long, message: String, cause: Option[Throwable] = None)
    extends SigmaException(message, cause)

object CostLimitException {
  def msgCostLimitError(cost: JitCost, limit: JitCost) = s"Estimated execution cost $cost exceeds the limit $limit"
}
