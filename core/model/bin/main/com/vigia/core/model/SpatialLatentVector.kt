package com.vigia.core.model

data class SpatialLatentVector(
    val dimensions: Int,
    val data: FloatArray,
    val originTimestampMs: Long,
) {
    init {
        require(dimensions == 256 || dimensions == 512) { "dimensions must be 256 or 512" }
        require(data.size == dimensions) { "data.size must equal dimensions" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpatialLatentVector) return false
        return dimensions == other.dimensions &&
            originTimestampMs == other.originTimestampMs &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = dimensions
        result = 31 * result + data.contentHashCode()
        result = 31 * result + originTimestampMs.hashCode()
        return result
    }
}
