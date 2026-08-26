## MODIFIED Requirements

### Requirement: Removing a book never deletes the user's files

Removing a book SHALL delete only the app's own record of it, together with anything the app
derived and cached for it, and SHALL release the persisted URI permission taken when it was
added. The user's audio files, and the folder they live in, SHALL be left untouched.

The app's own record includes the marks and notes the user took against that book, which are removed
with it. Those are app data rather than the user's files, so removing them is consistent with the
rule above; because they are the only content in the app the user authored themselves, the
confirmation states what will go before it goes.

The one file the app MAY delete on removal is its own copy of its own bundled asset, held in the
app-private directory it created for that purpose. That file was never the user's; deleting it
reclaims space the app spent, and the containment check that permits it SHALL be by directory,
never by file name, URI text, or source type.

#### Scenario: Removing a folder book

- **WHEN** the user removes a folder book from the library
- **THEN** the book disappears from the library
- **AND** the source folder and every audio file in it still exist and are unmodified

#### Scenario: Removing a single-file book

- **WHEN** the user removes a single-file book from the library
- **THEN** the book disappears from the library
- **AND** the source file still exists and is unmodified

#### Scenario: Removing the bundled sample

- **WHEN** the user removes the bundled sample from the library
- **THEN** the book disappears from the library
- **AND** the app's copy of it in app-private storage is deleted
- **AND** no file outside that directory is touched

#### Scenario: The permission grant is released

- **WHEN** a book is removed
- **THEN** the persisted URI permission taken for its folder or file is released
- **AND** the app's persisted permission grants do not accumulate for removed books

#### Scenario: Removing a book with notes

- **WHEN** the user removes a book that has marks or notes
- **THEN** those marks and notes are removed with it
- **AND** the marks and notes of every other book are unaffected

#### Scenario: Re-adding a removed book

- **WHEN** a removed book's folder or file is selected again
- **THEN** it is added to the library as normal

### Requirement: Removal is confirmed in a thumb-reachable sheet

Confirming the removal of a book SHALL be presented as a bottom sheet rather than a centered
dialog, so that its controls fall within reach of a thumb. The confirmation SHALL name the book
being removed and SHALL state that the user's files are not deleted. Dismissing the sheet, by any
means the platform offers, SHALL remove nothing.

When the book has marks or notes, the confirmation SHALL state how many will be removed with it.
When it has none, the confirmation SHALL NOT mention notes at all.

#### Scenario: Long-pressing a book

- **WHEN** the user long-presses a book in the list
- **THEN** a bottom sheet asks whether to remove that book
- **AND** the sheet names the book
- **AND** the sheet states that the user's files stay where they are

#### Scenario: Confirming removal

- **WHEN** the user confirms removal from the sheet
- **THEN** the book is removed from the library
- **AND** the sheet closes

#### Scenario: Declining removal

- **WHEN** the user declines removal from the sheet
- **THEN** the book remains in the library
- **AND** the sheet closes

#### Scenario: Dismissing the sheet without answering

- **WHEN** the user dismisses the sheet by swiping it away or pressing back
- **THEN** the book remains in the library

#### Scenario: Confirming removal of a book that has notes

- **WHEN** the user is asked to confirm removing a book that has marks or notes
- **THEN** the sheet states how many will be removed with the book

#### Scenario: Confirming removal of a book that has no notes

- **WHEN** the user is asked to confirm removing a book that has none
- **THEN** the sheet says nothing about notes
