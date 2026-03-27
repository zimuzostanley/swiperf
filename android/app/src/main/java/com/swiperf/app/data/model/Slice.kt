package com.swiperf.app.data.model

data class Slice(
    val ts: Long,
    val dur: Long,
    val name: String?,
    val state: String?,
    val depth: Int?,
    val ioWait: Int?,
    val blockedFunction: String?
)

data class MergedSlice(
    val ts: Long,
    val dur: Long,
    val name: String?,
    val state: String?,
    val depth: Int?,
    val ioWait: Int?,
    val blockedFunction: String?,
    val tsRel: Long,
    val merged: Int
)
