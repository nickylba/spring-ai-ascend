---
level: L1
view: [logical, development]
module: agent-service-starter
status: active
freeze_id: null
authority: "ADR-0161 (serviceization Stage 2 — Spring edge as a separate starter)"
---

# agent-service-starter — L1 (Index)

The Spring Boot edge of the agent-service serviceization facade. The facade
itself (`agent-service`) is deliberately Spring-free: its
registration/discovery/routing SPI and reference implementations are plain
JDK. This starter is the ONLY Spring-aware layer — it auto-configures the
HTTP controllers over the facade SPI and the JWT tenant cross-check filter at
the service ingress, behind `@ConditionalOnMissingBean` seams so a deployment
can replace any implementation without forking the edge.

## Logical surface (package `com.huawei.ascend.service.starter`)

- `AgentServiceAutoConfiguration` — registers one `InMemoryRuntimeRegistry`
  bean serving both `RuntimeRegistry` and `AgentDirectory` (interface alias
  beans are deliberately absent so by-type injection never sees two
  candidates), `HmacRouteGrantService`, `RuntimeA2aGateway`, the three
  controllers, and — when `agent-service.access.jwt.enabled=true` — the
  ingress filter. `agent-service.enabled=false` switches the whole edge off.
  Route-grant signing WARNs on the checked-in development default secret and
  refuses to start a JWT-provisioned deployment that still uses it.
- `RuntimeRegistryController` — `POST/PUT/DELETE /v1/runtime-registrations*`,
  `GET /v1/agents*`, `POST /v1/agents/{agentId}/routes/resolve`. Card
  serving applies `MaskedAgentDirectory` when `agent-service.public-base-url`
  is set (discovery-metadata rewrite only; A2A payloads are never touched).
- `A2aGatewayController` — `POST /v1/agents/{agentId}/a2a`: byte-level A2A
  pass-through with signed route-grant headers (`X-Ascend-*`), lenient relay
  of third-party runtime status/content-type, and the `A2aForwardObserver`
  observation seam (no bespoke telemetry store; the e2e example bridges it
  into its own interaction log).
- `RouteGrantController` — `POST /v1/route-grants/{resolve,validate}`.
- `ServiceTenantAuthFilter` — the service-ingress half of the ADR-0040 tenant
  cross-check, reusing the runtime's `JwtTenantValidator` (one validator, one
  set of security tests; transition status per ADR-0164) plus the
  service-specific `tenantId` query-parameter cross-check.
- `AgentServiceProperties` — `agent-service.{enabled,route-grant-secret,
  public-base-url,access.jwt.{enabled,hmac-secret,clock-skew-seconds}}`;
  configuration metadata ships via the annotation processor.

## Development view

Depends on `agent-service` (the facade it serves) and `agent-runtime`
(solely for the shared `JwtTenantValidator`; a facade-only deployment turns
the runtime kernel off with `agent-runtime.enabled=false`). No SPI of its
own — `spi_packages` is empty by design; the SPI lives in `agent-service`.
Tests: auto-configuration seams (`AgentServiceAutoConfigurationTest`,
`FacadeOnlyDeploymentTest`), ingress security (`ServiceTenantAuthFilterTest`),
gateway relay hardening (`A2aGatewayControllerTest`), registration wire
round-trip (`RuntimeRegistryControllerTest`).
