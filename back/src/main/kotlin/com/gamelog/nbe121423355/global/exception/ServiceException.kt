package com.gamelog.nbe121423355.global.exception

import com.gamelog.nbe121423355.global.dto.RsData

class ServiceException : RuntimeException {
    val rsData: RsData<*>

    constructor(resultCode: String, message: String?) : super(message) {
        this.rsData = RsData<Any?>(resultCode, message)
    }

    constructor(resultCode: String, message: String?, cause: Throwable?) : super(message, cause) {
        this.rsData = RsData<Any?>(resultCode, message)
    }

    val resultCode: String
        get() = rsData.resultCode

    val msg: String?
        get() = rsData.msg
}
