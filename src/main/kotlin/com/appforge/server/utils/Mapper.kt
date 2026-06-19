package com.appforge.server.utils

interface Mapper<DOMAIN, DOC> {
    fun toDoc(domain: DOMAIN): DOC
    fun fromDoc(id: String, doc: DOC): DOMAIN
}
