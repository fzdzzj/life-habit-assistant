package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.common.ErrorCode;
import com.fzdzzj.lifehabitassistant.common.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResultTest {
    @Test void createsConsistentSuccessAndErrorBodies() {
        Result<String> success = Result.success("ok");
        Result<Void> error = Result.error(ErrorCode.UNAUTHORIZED);

        assertEquals(1, success.code());
        assertEquals("ok", success.data());
        assertEquals(40100, error.code());
        assertNull(error.data());
    }
}
