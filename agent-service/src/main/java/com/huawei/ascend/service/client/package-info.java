/**
 * Runtime-side client for the service facade's registration plane: a runtime
 * instance registers itself at boot, keeps its lease alive with a daemon
 * heartbeat, and deregisters on shutdown.
 *
 * <p>The client encodes the registration wire contract (the JSON bodies of
 * {@code POST /v1/runtime-registrations}, {@code PUT
 * /v1/runtime-registrations/{id}/lease} and {@code DELETE
 * /v1/runtime-registrations/{id}}) itself, because the module dependency
 * direction is starter&nbsp;&rarr;&nbsp;service: this Spring-free module must
 * not see the Spring edge that serves those routes. The public API speaks the
 * {@code com.huawei.ascend.service.spi.registry} records; the HTTP shape is an
 * internal mapping.
 */
package com.huawei.ascend.service.client;
