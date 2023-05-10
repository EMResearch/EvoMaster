package org.evomaster.core.output.clustering.metrics

object FaultOriginDistance {

    /***
     * The idea is to find a distance between fault origins in a module
     * We use a distance between branches, based on the deepest common node
     * The distance is then calculated as the sum of the remaining branches
     */
    fun distance(p0: String, p1: String): Double {

        val p0tokens = pathProcessing(p0)
        val p1tokens = pathProcessing(p1)

        var lastCommonNode = minOf(p0tokens.size, p1tokens.size)

        for (i in 0..minOf(p0tokens.size, p1tokens.size)){
            if(!p0tokens.get(i).contentEquals(p1tokens.get(i))){
                lastCommonNode = i
                break
            }


        }

        return ((p0tokens.size - lastCommonNode) + (p1tokens.size - lastCommonNode)).toDouble()
    }

    /**
     * The distance is meant to look at the origin of a fault,
     * and assumes the distances are between paths.
     */

    fun pathProcessing(p: String): List<String>{
        val tokens = p.split("/", "_")

        return tokens
    }

}