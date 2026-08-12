## MODIFIED Requirements

### Requirement: The application requests no network permission

The merged Android manifest SHALL contain no `INTERNET` permission, no other network-related
permission, and no storage permission. It SHALL declare only those permissions genuinely required
for background media playback. This SHALL be verified automatically against the **merged** manifest
rather than the source manifest, and the build SHALL fail when a forbidden permission appears,
including one contributed by a dependency during manifest merging.

#### Scenario: Merged manifest is inspected after a build

- **WHEN** a build completes and the merged manifest is inspected
- **THEN** it declares no `android.permission.INTERNET`
- **AND** it declares no `android.permission.ACCESS_NETWORK_STATE`
- **AND** it declares no `android.permission.ACCESS_WIFI_STATE`

#### Scenario: A dependency contributes a forbidden permission

- **WHEN** a dependency contributes a forbidden permission during manifest merging
- **THEN** the build fails naming the permission that appeared
- **AND** the failure states that it must either be removed or deliberately justified

#### Scenario: A contributed permission is removed rather than accepted

- **WHEN** a playback dependency contributes `ACCESS_NETWORK_STATE` for streaming features this app does not use
- **THEN** it is stripped from the merged manifest
- **AND** playback of local files continues to work without it

#### Scenario: No storage permission is declared

- **WHEN** the merged manifest is inspected
- **THEN** it declares no `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, or `READ_MEDIA_AUDIO` permission
- **AND** file access continues to come solely from Storage Access Framework grants

#### Scenario: Only playback-supporting permissions are declared

- **WHEN** the merged manifest is inspected
- **THEN** the declared permissions are limited to those that background media playback requires — running a foreground media service, posting its notification, and holding a wake lock while audio plays with the screen off
- **AND** any permission outside that set is treated as a regression to be justified or removed
