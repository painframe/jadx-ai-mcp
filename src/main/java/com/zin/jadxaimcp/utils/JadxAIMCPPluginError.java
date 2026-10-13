package com.zin.jadxaimcp.utils;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.http.Context;

public class JadxAIMCPPluginError {

    /**
     * @param ctx The HTTP request context to send error response
     * @param error_response The error message to return to client
     * @param e The exception that caused the error
     * @param logger The logger instance for error logging
     * @return void
     * 
     * This method handles exceptions with full error logging and HTTP 500 response.
     * 1. It logs the error message with the full exception stack trace
     * 2. It sets HTTP status to 500 (Internal Server Error)
     * 3. It sends a JSON response containing the error message
     * 
     * This overload is used when an exception needs to be logged with details.
     */
    public static void handleError(Context ctx, String error_response, Exception e, Logger logger) {
        logger.error("JADX AI MCP Error: " + error_response, e);
        int status = e instanceof MethodSignatures.AmbiguousSignatureException ? 409
                : e instanceof PaginationUtils.PaginationException ? 400 : 500;
        Map<String, Object> result = new HashMap<>(response(status, error_response));
        if (e instanceof MethodSignatures.AmbiguousSignatureException)
            result.put("candidates", ((MethodSignatures.AmbiguousSignatureException) e).candidates);
        ctx.status(status).json(result);
    }

    /**
     * @param ctx The HTTP request context to send error response
     * @param status The HTTP status code to return
     * @param error_response The error message to return to client
     * @param logger The logger instance for error logging
     * @return void
     * 
     * This method handles errors with custom HTTP status codes.
     * 1. It logs the error message without exception details
     * 2. It sets the specified HTTP status code (e.g., 404, 400)
     * 3. It sends a JSON response containing the error message
     * 
     * This overload is used for validation errors or missing resources
     * where a specific HTTP status code is appropriate.
    */
    public static void handleError(Context ctx, int status, String error_response, Logger logger) {
        logger.error("JADX AI MCP Error: " + error_response);
        ctx.status(status).json(response(status, error_response));
    }
    public static Map<String, Object> response(int status, String message) {
        String code;
        switch (status) {
            case 422:
            case 400: code = "invalid_request"; break;
            case 401: code = "unauthorized"; break;
            case 403: code = "forbidden"; break;
            case 404: code = "not_found"; break;
            case 409: code = "conflict"; break;
            case 413: code = "response_too_large"; break;
            case 429: code = "busy"; break;
            case 501: code = "unsupported_capability"; break;
            case 503: code = "unavailable"; break;
            case 504: code = "timeout"; break;
            default: code = "internal_error";
        }
        return Map.of("error", message, "code", code, "retryable", status == 429 || status == 503 || status == 504,
                "source", "jadx", "status", status);
    }
}
