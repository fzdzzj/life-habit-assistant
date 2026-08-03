package com.fzdzzj.lifehabitassistant.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {
    @Test
    void unexpectedExceptionShouldReturnUnifiedInternalError() {
        ResponseEntity<Result<Void>> response =
                new ApiExceptionHandler().handleUnexpected(new RuntimeException("boom"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), response.getBody().code());
    }
}
