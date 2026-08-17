## ADDED Requirements

### Requirement: A library row shows whether the book has an ebook linked

A book with an ebook linked SHALL be marked in the library list. The mark is an indicator only —
the row SHALL continue to open the Player, and the library SHALL NOT offer linking or reading.

#### Scenario: A book with an ebook linked

- **WHEN** the library lists a book that has an ebook linked
- **THEN** the row shows an ebook indicator alongside its existing details

#### Scenario: A book with no ebook linked

- **WHEN** the library lists a book with no ebook linked
- **THEN** the row shows no ebook indicator

#### Scenario: The indicator is not a control

- **WHEN** the user taps a row carrying the ebook indicator, including on the indicator itself
- **THEN** the Player for that book opens, as it does for any other row

#### Scenario: The indicator follows the link

- **WHEN** the user links an ebook to a book, or unlinks one, and returns to the library
- **THEN** the row's indicator reflects the change without needing a restart
