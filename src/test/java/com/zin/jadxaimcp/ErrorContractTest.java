package com.zin.jadxaimcp;

import com.zin.jadxaimcp.utils.JadxAIMCPPluginError;
import com.zin.jadxaimcp.utils.PaginationUtils;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErrorContractTest {
    @Test void keepsLegacyMessageAndAddsRetryability() {
        assertEquals("bad", JadxAIMCPPluginError.response(400, "bad").get("error"));
        assertEquals(false, JadxAIMCPPluginError.response(400, "bad").get("retryable"));
        assertEquals(true, JadxAIMCPPluginError.response(503, "down").get("retryable"));
    }
    @Test void invalidPaginationIsClientError() {
        PaginationUtils pagination = new PaginationUtils();
        Context ctx = mock(Context.class, RETURNS_SELF);
        when(ctx.queryParam("offset")).thenReturn("-1");
        Exception error = assertThrows(PaginationUtils.PaginationException.class,
                () -> pagination.handlePagination(ctx, List.of("one"), "list", "items"));
        JadxAIMCPPluginError.handleError(ctx, error.getMessage(), error, LoggerFactory.getLogger(getClass()));
        verify(ctx).status(400);
    }
    @Test void rejectsOverflowAndNegativeLimit() {
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn("2147483648");
        assertThrows(PaginationUtils.PaginationException.class,
                () -> new PaginationUtils().handlePagination(ctx, List.of(), "list", "items"));
    }
}
