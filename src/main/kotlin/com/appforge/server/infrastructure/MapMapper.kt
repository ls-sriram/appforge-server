package com.appforge.server.infrastructure

import com.appforge.server.utils.Mapper

object MapMapper : Mapper<Map<String, Any?>, Map<String, Any?>> {
    override fun toDoc(domain: Map<String, Any?>): Map<String, Any?> = domain
    override fun fromDoc(id: String, doc: Map<String, Any?>): Map<String, Any?> = doc
}
