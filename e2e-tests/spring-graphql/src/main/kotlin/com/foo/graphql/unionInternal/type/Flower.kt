package com.foo.graphql.unionInternal.type

data class Flower(
        override var id: Int? = null,
        var name: String? = null,
        var color: String? = null,
        var price: Int? = null) : Bouquet
