package com.appforge.server.routing

import com.appforge.server.infrastructure.sql.SqlRequestContext
import com.appforge.server.middleware.RequestContext

suspend fun <T> withRouteSqlUserContext(ctx: RequestContext, block: suspend () -> T): T {
    return SqlRequestContext.withUserId(ctx.userId, block)
}
