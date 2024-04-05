package sigmastate.lang

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.sdk.Parameter
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import sigma.ast.{IntConstant, SInt, SLong, SString, StringConstant}
import sigmastate.eval.CompiletimeIRContext

class SigmaTemplateCompilerTest extends AnyPropSpec with ScalaCheckPropertyChecks with Matchers {
  property("compiles full contract template") {
    val source =
      """/** This is my contracts description.
        |* Here is another line describing what it does in more detail.
        |*
        |* @param p1 describe p1
        |* @param p2 description of the 2nd parameter
        |* which is pretty complex and on many
        |* lines to describe functions
        |* @param param3 the final parameter
        |* @return
        |*/
        |@contract def contractName(p1: Int = 5, p2: String = "default string", param3: Long) = {
        |  sigmaProp(true)
        |}""".stripMargin
    val compiler = SigmaTemplateCompiler(ErgoAddressEncoder.MainnetNetworkPrefix)
    val template = compiler.compile(Map.empty, source)

    template.name shouldBe "contractName"
    template.description shouldBe "This is my contracts description. Here is another line describing what it does in more detail."
    template.parameters should contain theSameElementsInOrderAs IndexedSeq(
      Parameter("p1", "describe p1", 0),
      Parameter("p2", "description of the 2nd parameter which is pretty complex and on many lines to describe functions", 1),
      Parameter("param3", "the final parameter", 2)
    )
    template.constTypes should contain theSameElementsInOrderAs Seq(SInt, SString, SLong)
    template.constValues.get should contain theSameElementsInOrderAs IndexedSeq(
      Some(IntConstant(5).asWrappedType),
      Some(StringConstant("default string").asWrappedType),
      None
    )

    val sigmaCompiler = new SigmaCompiler(ErgoAddressEncoder.MainnetNetworkPrefix)
    implicit val ir = new CompiletimeIRContext
    val result = sigmaCompiler.compile(Map.empty, "{ sigmaProp(true) }")

    template.expressionTree shouldBe result.buildTree
  }

  property("compiles contract template without braces") {
    val source =
      """/** This is my contracts description.
        |* Here is another line describing what it does in more detail.
        |*
        |* @param p1 describe p1
        |* @param p2 description of the 2nd parameter
        |* which is pretty complex and on many
        |
        |* lines to describe functions
        |* @return
        |*/
        |@contract def contractName(p1: Int = 5, p2: String = "default string") = sigmaProp(true)
        |""".stripMargin
    val compiler = SigmaTemplateCompiler(ErgoAddressEncoder.MainnetNetworkPrefix)
    val template = compiler.compile(Map.empty, source)

    template.name shouldBe "contractName"
    template.description shouldBe "This is my contracts description. Here is another line describing what it does in more detail."
    template.parameters should contain theSameElementsInOrderAs IndexedSeq(
      Parameter("p1", "describe p1", 0),
      Parameter("p2", "description of the 2nd parameter which is pretty complex and on many lines to describe functions", 1)
    )
    template.constTypes should contain theSameElementsInOrderAs Seq(SInt, SString)
    template.constValues.get should contain theSameElementsInOrderAs IndexedSeq(
      Some(IntConstant(5).asWrappedType),
      Some(StringConstant("default string").asWrappedType)
    )

    val sigmaCompiler = new SigmaCompiler(ErgoAddressEncoder.MainnetNetworkPrefix)
    implicit val ir = new CompiletimeIRContext
    val result = sigmaCompiler.compile(Map.empty, "sigmaProp(true)")

    template.expressionTree shouldBe result.buildTree
  }
}


