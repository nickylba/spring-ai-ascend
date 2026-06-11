package com.huawei.ascend.service.client;

/**
 * Transport-level failure talking to the service registration plane (the
 * service was unreachable or the call was interrupted). Distinct from a
 * service-side rejection, which carries an HTTP status and error code in
 * {@link RuntimeRegistrationOutcome} instead of being thrown.
 */
public final class RuntimeRegistrationClientException extends RuntimeException {

    public RuntimeRegistrationClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
