package com.foo.graphql.nullable.array

import com.foo.graphql.nullable.array.type.Flower
import org.springframework.stereotype.Component


@Component
open class DataRepository {

    //flowersNullInNullOut(id: [Int]): Flower
    fun findFlowersNullInNullOut(id: Array<Int?>?): Flower? {
        return if (id == null) null
        else
            if (id.all { it != null }) Flower(0, "flowerNameX") else null
    }

    //flowersNullIn(id: [Int]!): Flower
    fun findFlowersNullIn(id: Array<Int?>): Flower? {
        return if (id.all { it != null }) Flower(0, "flowerNameX") else null
    }

    //flowersNullOut(id: [Int!]): Flower
    fun findFlowersNullOut(id: Array<Int>?): Flower? {
        return if (id == null) null
        else Flower(0, "flowerNameX")
    }

    // flowersNotNullInOut(id: [Int!]!): Flower
    fun findFlowersNotNullInOut(id: Array<Int>): Flower? {
        return  Flower(0, "flowerNameX")
    }

}




