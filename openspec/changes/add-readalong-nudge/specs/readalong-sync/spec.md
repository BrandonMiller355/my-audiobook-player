## ADDED Requirements

### Requirement: The reader can correct the correspondence within a chapter

For a book that supports read-along, the reader SHALL offer a control that moves the text earlier or
later against the narration in fixed steps, so that a reader who finds the page running ahead of or
behind the narrator can say so. The control SHALL be offered only where read-along is active, and
SHALL NOT appear for a book that cannot support it.

Correcting SHALL NOT require pausing, and SHALL NOT change whether the book is playing. The page
SHALL move to the corrected position, and the correction SHALL be retained for that book without the
reader confirming it separately.

The control SHALL show the size of the correction accumulated so far, so that a reader who has
overshot can see it and step back.

#### Scenario: Correcting while listening

- **WHEN** the reader finds the text running ahead of the narration and steps the correction
- **THEN** the page moves back to the place the narrator is actually reading
- **AND** the book carries on playing

#### Scenario: Correcting while paused

- **WHEN** the book is paused and the reader steps the correction
- **THEN** the page moves to the corrected position
- **AND** the book stays paused

#### Scenario: Stepping repeatedly

- **WHEN** the reader steps the correction several times in succession
- **THEN** each step moves the text by a further fixed amount in that direction
- **AND** the accumulated correction is shown

#### Scenario: Stepping back after overshooting

- **WHEN** the reader has corrected too far and steps in the opposite direction
- **THEN** the correction is reduced by that amount rather than a second correction being made

#### Scenario: A book that cannot support read-along

- **WHEN** the reader opens a book whose audio carries no chapter marks, or whose ebook provides no
  table of contents
- **THEN** no correction control is offered

#### Scenario: Correcting does not seek the audio

- **WHEN** the reader steps the correction
- **THEN** the audio position is unchanged
- **AND** the page moving to the corrected position does not seek the audio either

### Requirement: A correction applies to one chapter and preserves its boundaries

A correction SHALL apply only to the audio chapter it was made in. The correspondence at that
chapter's start and at the next chapter's start SHALL remain exact, and neighboring chapters SHALL be
unaffected.

A correction SHALL be retained across closing and reopening the book.

#### Scenario: The chapter's boundaries stay exact

- **WHEN** a correction has been made partway through a chapter
- **THEN** the reader still shows the start of that chapter's text when the audio reaches the
  chapter's start
- **AND** still shows the start of the next chapter's text when the audio reaches it

#### Scenario: The correction holds through the chapter

- **WHEN** a correction has been made and the reader listens on through the rest of that chapter
- **THEN** the correction stays applied in full rather than fading as the chapter runs on
- **AND** it is given up only over a short stretch before the chapter ends, so that the next
  chapter still starts in the right place

#### Scenario: Giving the correction up is not a jump

- **WHEN** the audio reaches the stretch where the correction is given up
- **THEN** the text goes on moving at a readable pace rather than jumping or stalling

#### Scenario: Neighboring chapters are unaffected

- **WHEN** a correction has been made in one chapter
- **THEN** the correspondence in every other chapter of that book is unchanged

#### Scenario: Another book is unaffected

- **WHEN** a correction has been made for one book
- **THEN** another book's correspondence is unchanged

#### Scenario: A correction survives reopening

- **WHEN** the reader closes the book and opens it again
- **THEN** the correction made earlier is still in effect

#### Scenario: A correction cannot be pushed past the chapter

- **WHEN** the reader steps the correction far enough that it would carry the text beyond the
  chapter it applies to
- **THEN** the correction stops at the chapter's extent rather than running into the neighboring one

### Requirement: A correction is limited to what its chapter can express

A correction SHALL be limited to what the chapter around it can absorb without the text moving at an
implausible pace on either side of it. The room available SHALL be smaller the closer the correction
is made to a chapter boundary, since there is correspondingly less text left to redistribute. When a
step is refused for this reason the reader SHALL be told why, rather than the control appearing not
to respond.

#### Scenario: Correcting near the end of a chapter

- **WHEN** the reader steps the correction a short way before a chapter ends, by more than the
  remaining text can absorb
- **THEN** the correction is limited to what that chapter can express
- **AND** the text does not race through the end of the chapter to reach the next one's opening
- **AND** the next chapter still starts in the right place

#### Scenario: The reader is told the limit was reached

- **WHEN** a step is refused because the chapter has no more room
- **THEN** the reader is told the chapter cannot move the text further
- **AND** the figure shown stops changing rather than the control appearing broken

#### Scenario: Room returns in the body of a chapter

- **WHEN** the reader corrects well away from either boundary of a chapter
- **THEN** a correction of ordinary size is accepted in full

### Requirement: A correction supersedes the previous one for that chapter

Correcting a chapter that already carries a correction SHALL replace it rather than adding a second
one, and SHALL be measured from the corrected correspondence rather than from the uncorrected one, so
that a later adjustment refines the earlier one.

#### Scenario: Correcting a chapter a second time

- **WHEN** the reader corrects a chapter that was corrected earlier in the same session or a previous
  one
- **THEN** the new correction replaces the old one
- **AND** the step moves the text relative to where the earlier correction left it

### Requirement: A correction is discarded when the ebook changes

When the linked ebook is replaced or unlinked, every correction for that book SHALL be discarded
rather than applied to a different file, since a correction records a place in one specific ebook.
Removing a book SHALL remove its corrections with it.

#### Scenario: Replacing the linked ebook

- **WHEN** the reader links a different ebook to a book that carries corrections
- **THEN** those corrections are discarded
- **AND** the new ebook's correspondence is the uncorrected one

#### Scenario: Unlinking the ebook

- **WHEN** the ebook is unlinked from a book that carries corrections
- **THEN** those corrections are discarded

#### Scenario: Removing the book

- **WHEN** the book is removed from the library
- **THEN** its corrections are removed with it

### Requirement: An owner's correction is not overridden by the rate guard

The app SHALL apply a correction the owner entered as given. The safeguard that falls back to the
book's typical narration rate when a stretch implies an implausible one SHALL NOT apply to a stretch
bounded by an owner's correction, since that safeguard exists to contain uncertain automatic matching
rather than a position the owner stated.

#### Scenario: A correction implying an unusual rate

- **WHEN** a correction places the narrator's position such that the text either side of it is
  covered at a rate far from the book's typical one
- **THEN** the correspondence still passes through the corrected position
- **AND** the correction is not discarded in favor of the typical rate

#### Scenario: Automatic matching is still guarded

- **WHEN** a chapter carries no correction and its matched extent implies an implausible rate
- **THEN** the existing safeguard still applies to it
