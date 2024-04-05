package sigma

import scala.collection.compat.immutable.ArraySeq

/** Base class for Sigma-related exceptions.
  *
  * @param message the error message
  * @param cause an optional cause for the exception
  * @param args an optional sequence of arguments to be passed with the exception
  */
class SigmaException(
    val message: String,
    val cause: Option[Throwable] = None,
    val args: Seq[Any] = ArraySeq.empty) extends Exception(message, cause.orNull)



