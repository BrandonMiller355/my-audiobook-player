## ADDED Requirements

### Requirement: A folder audiobook is the audio files directly inside one folder

The scanner SHALL consider only the immediate children of the selected folder and SHALL NOT
descend into subdirectories. One selected folder produces at most one audiobook.

#### Scenario: Flat folder of chapter files

- **WHEN** a folder containing 128 audio files and no subdirectories is selected
- **THEN** all 128 files become chapters of a single audiobook

#### Scenario: Folder whose audio lives in a subdirectory

- **WHEN** a folder is selected that contains no audio files directly but has a subdirectory that does
- **THEN** no audiobook is created
- **AND** the user is told that the folder contains no supported audio files

#### Scenario: Folder holding a whole series in nested subdirectories

- **WHEN** a folder is selected whose subdirectories together hold hundreds of files belonging to several different books
- **THEN** those nested files are not gathered into one audiobook

#### Scenario: Non-audio files alongside the audio

- **WHEN** the selected folder contains images, text files, or ebook files next to the audio files
- **THEN** only the supported audio files become chapters
- **AND** the other files are ignored without error

### Requirement: Supported audio formats are recognized by file extension

The scanner SHALL accept files whose extension is one of `mp3`, `m4a`, `m4b`, `aac`, `ogg`, `opus`,
`flac`, or `wav`, matched case-insensitively. Extension SHALL be the deciding signal, because
document providers report MIME types inconsistently.

#### Scenario: Provider reports a generic MIME type

- **WHEN** a document provider reports an `.mp3` file as `application/octet-stream`
- **THEN** the file is still accepted as audio

#### Scenario: Mixed-case extensions

- **WHEN** files ending in `.MP3` and `.Mp3` are present
- **THEN** they are accepted

#### Scenario: Unsupported audio-like file

- **WHEN** the folder contains a file with an unsupported extension such as `.wma`
- **THEN** it is not made into a chapter
- **AND** the remaining supported files still form the audiobook

### Requirement: Chapters are ordered by natural sort of file names

The scanner SHALL order files by comparing runs of digits numerically and the surrounding text
case-insensitively, so that numbers embedded in names sort as a person reads them.

#### Scenario: Unpadded chapter numbers

- **WHEN** the folder contains `Chapter 1`, `Chapter 2`, `Chapter 3`, and `Chapter 10`
- **THEN** the chapter order is 1, 2, 3, 10
- **AND** `Chapter 10` is not placed between `Chapter 1` and `Chapter 2`

#### Scenario: Disc and track numbering

- **WHEN** the folder contains names of the form `1-01`, `1-09`, `1-10`, `2-01`, and `9-15`
- **THEN** all disc 1 files precede disc 2, which precedes disc 9
- **AND** within a disc, track 9 precedes track 10

#### Scenario: Numbers with surrounding words

- **WHEN** the folder contains `01 of 09` through `09 of 09`
- **THEN** the files are ordered 1 through 9

#### Scenario: Names without numbers

- **WHEN** file names contain no digits
- **THEN** they are ordered case-insensitively by name
- **AND** the order is stable

### Requirement: An empty or unusable folder does not create a book

The scanner SHALL report a folder containing no supported audio files as an error rather than
creating an audiobook with no chapters.

#### Scenario: Folder with no audio at all

- **WHEN** the selected folder contains only documents and images
- **THEN** no audiobook is added to the library
- **AND** a readable message explains that no supported audio files were found

#### Scenario: Completely empty folder

- **WHEN** the selected folder contains no files
- **THEN** no audiobook is added and the user is told why
