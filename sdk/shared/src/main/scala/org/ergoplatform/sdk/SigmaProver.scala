package org.ergoplatform.sdk

import org.ergoplatform.ErgoAddressEncoder.NetworkPrefix
import org.ergoplatform._
import org.ergoplatform.sdk.wallet.protocol.context.BlockchainStateContext
import sigmastate.Values.SigmaBoolean
import sigmastate.eval.{CostingSigmaDslBuilder, SigmaDsl}
import sigmastate.interpreter.HintsBag
import sigmastate.utils.Helpers.TryOps
import special.sigma.{BigInt, SigmaProp}

/** Represents a prover for signing Ergo transactions and messages.
  *
  * @param _prover        an instance of interpreter and a prover combined
  * @param networkPrefix  the network prefix for Ergo addresses
  */
class SigmaProver(private[sdk] val _prover: AppkitProvingInterpreter, networkPrefix: NetworkPrefix) {
  require(!hasSecrets || _prover.pubKeys.nonEmpty,
    "Prover has secrets but no public keys.")

  implicit val ergoAddressEncoder: ErgoAddressEncoder = ErgoAddressEncoder(networkPrefix)

  def hasSecrets: Boolean = _prover.secrets.nonEmpty

  /** Returns the Pay-to-Public-Key (P2PK) address associated with the prover's public key.
    * The returned address corresponds to the master secret derived from the mnemonic
    * phrase configured in the [[ProverBuilder]].
    */
  def getP2PKAddress: P2PKAddress = {
    val pk = _prover.pubKeys(0)
    P2PKAddress(pk)
  }

  /** Returns the prover's secret key. */
  def getSecretKey: BigInt =
    CostingSigmaDslBuilder.BigInt(_prover.secretKeys(0).privateInput.w)

  /** Returns a sequence of EIP-3 addresses associated with the prover's secret keys. */
  def getEip3Addresses: Seq[P2PKAddress] = {
    val addresses = _prover.secretKeys
        .drop(1) // the master address
        .map { k =>
          val p2pkAddress = P2PKAddress(k.publicImage)
          p2pkAddress
        }
    addresses
  }

  /** Signs a given `UnreducedTransaction` using the prover's secret keys and the provided [[BlockchainStateContext]].
    * Uses baseCost == 0.
    */
  def sign(stateCtx: BlockchainStateContext, tx: UnreducedTransaction): SignedTransaction =
    sign(stateCtx, tx, baseCost = 0)

  /** Signs a given `UnreducedTransaction` using the prover's secret keys and the provided [[BlockchainStateContext]].
    * Uses the given baseCost.
    */
  def sign(stateCtx: BlockchainStateContext, tx: UnreducedTransaction, baseCost: Int): SignedTransaction = {
    val signed = _prover
        .sign(tx, stateContext = stateCtx, baseCost = baseCost)
        .getOrThrow
    signed
  }

  /** Sign arbitrary message under a key representing a statement provable via a sigma-protocol.
    *
    * @param sigmaProp - public key
    * @param message   - message to sign
    * @param hintsBag  - additional hints for a signer (useful for distributed signing)
    * @return - signature bytes
    */
  def signMessage(sigmaProp: SigmaProp, message:  Array[Byte], hintsBag: HintsBag): Array[Byte] = {
    _prover.signMessage(SigmaDsl.toSigmaBoolean(sigmaProp), message, hintsBag).getOrThrow
  }

  /** Reduces a given `UnreducedTransaction` using the prover's secret keys and the
    * provided [[BlockchainStateContext]] with a base cost.
    */
  def reduce(stateCtx: BlockchainStateContext, tx: UnreducedTransaction, baseCost: Int): ReducedTransaction = {
    val reduced = _prover.reduceTransaction(
      unreducedTx = tx, stateContext = stateCtx, baseCost = baseCost)
    reduced
  }

  /** Signs a given ReducedTransaction using the prover's secret keys. */
  def signReduced(tx: ReducedTransaction): SignedTransaction = {
    _prover.signReduced(tx, tx.ergoTx.cost)
  }

  def generateCommitments(sigmaTree: SigmaBoolean): HintsBag = {
    _prover.generateCommitments(sigmaTree)
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: SigmaProver =>
      if (!this.hasSecrets || !that.hasSecrets) this eq that
      else {
        // both have secrets, compare masterKeys
        this._prover.pubKeys(0) == that._prover.pubKeys(0)
      }
    case _ => false
  }

  override def hashCode(): Int =
    if (hasSecrets) this._prover.pubKeys(0).hashCode() else super.hashCode()
}
