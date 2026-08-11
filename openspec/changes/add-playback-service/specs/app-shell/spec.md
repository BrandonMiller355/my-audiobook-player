## MODIFIED Requirements

### Requirement: The application requests no network permission

The merged Android manifest SHALL contain no `INTERNET` permission, no other network-related
permission, and no storage permission. It SHALL declare only those permissions genuinely required
for background media playback. This SHALL be verified against the merged manifest rather than the
source manifest, so that permissions contributed by dependencies are caught.

#### Scenario: Merged manifest is inspected after a build

- **WHEN** a debug build completes and the merged manifest in the build output is inspected
- **THEN** it declares no `android.permission.INTERNET`
- **AND** it declares no `android.permission.ACCESS_NETWORK_STATE`

#### Scenario: A dependency contributes a network permission

- **WHEN** a newly added dependency contributes a network permission during manifest merging
- **THEN** the merged-manifest check fails and the permission is surfaced rather than shipped silently

#### Scenario: No storage permission is declared

- **WHEN** the merged manifest is inspected
- **THEN** it declares no `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, or `READ_MEDIA_AUDIO` permission
- **AND** file access continues to come solely from Storage Access Framework grants

#### Scenario: Only media-playback permissions are declared

- **WHEN** the merged manifest is inspected
- **THEN** the only declared permissions are those required to run a foreground media playback service and post its notification
- **AND** any permission outside that set is treated as a regression to be justified or removed
