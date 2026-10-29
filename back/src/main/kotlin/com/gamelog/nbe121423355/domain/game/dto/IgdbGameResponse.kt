package com.gamelog.nbe121423355.domain.game.dto

import java.math.BigDecimal

@JvmRecord
data class IgdbGameResponse(
    val id: Long?,
    val name: String?,
    val summary: String?,
    val cover: Cover?,
    val first_release_date: Long?,
    val rating: BigDecimal?,
    val involved_companies: List<InvolvedCompany>?,
    val genres: List<NamedResource>?,
    val platforms: List<NamedResource>?,
    val collections: List<NamedResource>?,
) {
    @JvmRecord data class Cover(val id: Long?, val url: String?) {}

    @JvmRecord
    data class InvolvedCompany(
        val id: Long?,
        val developer: Boolean?,
        val company: Company?,
    ) {}

    @JvmRecord data class Company(val id: Long?, val name: String?) {}

    @JvmRecord data class NamedResource(val id: Long?, val name: String?) {}
}
