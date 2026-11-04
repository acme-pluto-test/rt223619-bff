# pluto-template (Kotlin + Spring BFF)

The skeleton every `<product>-bff` starts from: Spring Boot 4.1, Kotlin, JDK 21, Gradle 8.14.

- `src/main/openapi/api.yaml` is the contract (synced from `<product>-contracts`); the build generates the controller interface from it.
- Layers (`dev.pluto.template`): `domain`, `application` (plain Kotlin), `adapter.inbound.web`, `adapter.outbound.http`, `adapter.outbound.aws`, `config`. `ArchitectureTest` enforces the dependency rule.
- Run the tests: `./gradlew build` (needs Docker: the S3 test starts Floci). Run locally with Floci: `docker compose up`.
- Rename it for a product with `kotlin-spring-scaffold/scripts/adapt-template.sh <package> <artifact>`.

## Authentication

Every route except `/actuator/health` needs `Authorization: Bearer <token>`. A call without a valid token gets `401` with `{"code":"unauthorized"}` and `WWW-Authenticate: Bearer`, and the reason is not said.

The token is checked against these settings, none of which has a default. The service does not start unless **exactly one** of the first two is set, and the issuer and the audience are not empty:

| Setting | Environment | What it is |
| --- | --- | --- |
| `pluto.security.jwt.public-key` | `PLUTO_JWT_PUBLIC_KEY` | the PEM public key (RSA or EC P-256) that signs the tokens |
| `pluto.security.jwt.jwk-set-uri` | `PLUTO_JWT_JWK_SET_URI` | the key set of your identity provider, used instead of the key |
| `pluto.security.jwt.issuer` | `PLUTO_JWT_ISSUER` | the `iss` the token must carry |
| `pluto.security.jwt.audience` | `PLUTO_JWT_AUDIENCE` | the `aud` the token must carry |

Only RS256 and ES256 are accepted; `none`, HS256, a token without `exp` and an expired one (no clock skew) are refused. The tests sign their own tokens with keys made for the run (`TestTokens`); `docker compose up` needs the three environment variables and says which one is missing.
