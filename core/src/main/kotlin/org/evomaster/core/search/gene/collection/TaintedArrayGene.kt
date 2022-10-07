package org.evomaster.core.search.gene.collection

import org.evomaster.client.java.instrumentation.shared.TaintInputName
import org.evomaster.core.output.OutputFormat
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.root.CompositeGene
import org.evomaster.core.search.gene.utils.GeneUtils
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.mutator.genemutation.AdditionalGeneMutationInfo
import org.evomaster.core.search.service.mutator.genemutation.SubsetGeneMutationSelectionStrategy


/**
 * Special gene used to represent a valid array, with a single tainted value.
 * Once the tainted is resolved, an actual array with the proper type is used.
 *
 * This is needed in 2-phase marshalling, where a string is marshalled into an array of maps,
 * and then each map value is marshalled into a DTO.
 */
class TaintedArrayGene(

    name: String,

    var taintedValue : String,

    arrayGene:  ArrayGene<*>? = null

) :  CompositeGene(name, if(arrayGene == null) mutableListOf() else mutableListOf(arrayGene)) {

    init {
        if(! TaintInputName.isTaintInput(taintedValue)){
            throw IllegalArgumentException("Invalid taint: $taintedValue")
        }
    }

    private val arrayGene : ArrayGene<*>?
        get(){ return if(children.isNotEmpty()) children[0] as ArrayGene<*> else null}

    fun isResolved() = arrayGene != null

    fun resolveTaint(gene: ArrayGene<*>) {
        if(isResolved()){
            throw IllegalArgumentException("TaintedArray '$name' has already been resolved")
        }
        addChild(gene.copy())
    }

    override fun copyContent(): Gene {
        return TaintedArrayGene(name, taintedValue, arrayGene?.copy() as ArrayGene<*>? )
    }

    override fun isLocallyValid(): Boolean {
        return TaintInputName.isTaintInput(taintedValue) && (arrayGene == null || arrayGene!!.isLocallyValid())
    }

    override fun randomize(randomness: Randomness, tryToForceNewValue: Boolean) {
        arrayGene?.randomize(randomness,tryToForceNewValue)
    }

    override fun isMutable(): Boolean {
        return arrayGene!=null && arrayGene!!.isMutable()
    }

    override fun isPrintable(): Boolean {
        return arrayGene == null || arrayGene!!.isPrintable()
    }

    override fun customShouldApplyShallowMutation(
        randomness: Randomness,
        selectionStrategy: SubsetGeneMutationSelectionStrategy,
        enableAdaptiveGeneMutation: Boolean,
        additionalGeneMutationInfo: AdditionalGeneMutationInfo?
    ): Boolean {
        return false
    }

    override fun getValueAsPrintableString(
        previousGenes: List<Gene>,
        mode: GeneUtils.EscapeMode?,
        targetFormat: OutputFormat?,
        extraCheck: Boolean
    ): String {
        if(arrayGene == null) {
            return "[\"$taintedValue\"]"
        }
        return arrayGene!!.getValueAsPrintableString(previousGenes,mode,targetFormat,extraCheck)
    }

    override fun copyValueFrom(other: Gene) {
        if(other !is TaintedArrayGene){
            throw IllegalArgumentException("Other is not a TaintedArray: ${other::class.java}")
        }
        this.taintedValue = other.taintedValue
        this.arrayGene?.copyValueFrom(other.arrayGene!!)
    }

    override fun containsSameValueAs(other: Gene): Boolean {
        if(other !is TaintedArrayGene){
            throw IllegalArgumentException("Other is not a TaintedArray: ${other::class.java}")
        }
        if(arrayGene != null) {
            if(other.arrayGene == null){
                return false
            }
            return arrayGene!!.containsSameValueAs(other.arrayGene!!)
        }

        if(other.arrayGene != null){
            return false
        }

        return this.taintedValue == other.taintedValue
    }

    override fun bindValueBasedOn(gene: Gene): Boolean {
        if(gene !is TaintedArrayGene){
            throw IllegalArgumentException("Other is not a TaintedArray: ${gene::class.java}")
        }

        if(arrayGene != null && gene.arrayGene != null){
            return arrayGene!!.bindValueBasedOn(gene.arrayGene!!)
        }

        return false
    }
}