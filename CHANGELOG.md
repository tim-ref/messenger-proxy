# Changelog

All notable changes to this project will be documented in this file.

**Related change logs**:

- [messenger-client](https://github.com/tim-ref/messenger-client/blob/main/CHANGELOG.md)
- [messenger-org-admin](https://github.com/tim-ref/messenger-org-admin/blob/main/CHANGELOG.md)
- [messenger-proxy](https://github.com/tim-ref/messenger-proxy/blob/main/CHANGELOG.md)
- [messenger-push](https://github.com/tim-ref/messenger-push/blob/main/CHANGELOG.md)
- [messenger-rawdata-master](https://github.com/tim-ref/messenger-rawdata-master/blob/main/CHANGELOG.md)
- [messenger-registration-service](https://github.com/tim-ref/messenger-registration-service/blob/main/CHANGELOG.md)

<!--
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
-->

## [0.8.3] (published on 2025-07-021)

### Changed

- Increase performance for mutliple parallel client connections.
- Removed readiness check for registration service.

## [0.8.2] (published on 2025-06-03)

### Fixed

- Any fetched federation list will be ignored if it has the same version as the currently saved one.
- Corrected handling of reaction messages containing text instead of emoji.

## [0.8.1] (published on 2025-04-28)

### Fixed

- Use streaming for all media requests.  

## [0.8.0] (published on 2025-04-09)

### Added

- Micrometer metrics collection and five separate scrape endpoints.

### Fixed

- Aligned expected response for unmodified federation list with API specification.
- Added routes `/_matrix/client/v1/media/thumbnail/{serverName}/{mediaId}` and
  `/_matrix/media/v3/thumbnail/{serverName}/{mediaId}`.

## [0.7.2] (published on 2025-03-25)

### Added

- This change log.

### Changed

- Modified copyright headers to pass automated license header check.

### Fixed

- Added routes `/_matrix/client/v1/media/download/{serverName}/{mediaId}/{fileName}` and
  `/_matrix/media/v3/download/{serverName}/{mediaId}/{fileName}`.
- Response status code 405 Method Not Allowed (related to 10X.01.10).
- Removed `/_matrix/media/v3/config` route (related to A_26262).
- Removed `/_matrix/media/v3/preview_url` and `/_matrix/client/v1/media/preview_url` routes (A_26344).
- Corrected federation check for routes `/_matrix/federation/v2/invite/{roomId}/{eventId}` and
  `/_matrix/federation/v1/invite/{roomId}/{eventId}` (AF_10064-02).

## [0.7.1] (published on 2025-03-17)

### Added

- Added missing federation check for `/_matrix/federation/v2/invite/{roomId}/{eventId}` and
  `/_matrix/federation/v1/invite/{roomId}/{eventId}` (AF_10064-02).

## [0.7.0] (published on 2025-03-11)

### Added

- Full support for TI-Messenger Pro.
- Added `/.well-known/matrix/support` route (A_26265).

### Changed

- Refactored `MatrixAuthService` and `ContactManagement` to use `Either`.

### Fixed

- Validation of room version.
- Authentication of TI-Messenger information API.
- Added deprecated route `/_matrix/media/v3/download/{serverName}/{mediaId}`.

## [0.6.1] (published on 2025-02-27)

## [0.6.0] (published on 2025-02-18)

### Added

- Support for Matrix 1.11.
- Partial support for TI-Messenger Pro.

## [0.5.1] (published on 2025-02-11)

## [0.4.0] (published on 2025-01-09)

## [0.3.0] (published on 2024-09-19)

## [0.2.0] (published on 2024-07-04)

## [0.1.0] (published on 2024-03-13)
