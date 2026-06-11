package com.huawei.ascend.service.client;

import com.huawei.ascend.service.spi.registry.RuntimeRegistrationResult;
import java.util.Objects;

/**
 * Result of a registration attempt: either the service-issued lease
 * ({@link #result()} carries the instance id and lease deadline) or the
 * service's rejection ({@link #errorCode()} / {@link #errorMessage()} from the
 * error body, e.g. {@code BAD_REQUEST}). Both branches keep the raw HTTP
 * status so callers can distinguish validation rejections from auth failures.
 */
public record RuntimeRegistrationOutcome(
        int httpStatus,
        RuntimeRegistrationResult result,
        String errorCode,
        String errorMessage) {

    public static RuntimeRegistrationOutcome accepted(int httpStatus, RuntimeRegistrationResult result) {
        return new RuntimeRegistrationOutcome(httpStatus, Objects.requireNonNull(result, "result"), null, null);
    }

    public static RuntimeRegistrationOutcome rejected(int httpStatus, String errorCode, String errorMessage) {
        return new RuntimeRegistrationOutcome(httpStatus, null, errorCode, errorMessage);
    }

    public boolean registered() {
        return result != null;
    }
}
