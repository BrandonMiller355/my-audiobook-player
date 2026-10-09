## 1. Parsing

- [x] 1.1 Keep headings deeper than the open entry's marker inside the entry, as a bold line.
- [x] 1.2 Track the enclosing section from `Book` / `Part` / `Volume` headings, plain section lines,
      and named-section headings with chapters beneath; record it on each `SummaryEntry`.
- [x] 1.3 Accept `Book 2, Chapter 1` as a marker without tripping the length rule.

## 2. Matching

- [x] 2.1 Qualify chapter titles and entries by section; match book and chapter together first.
- [x] 2.2 Fall back to chapter number alone only when the entry names no book, or the titles name
      none and the file does not renumber per book.
- [x] 2.3 Ignore a section that is the same on every chapter or entry.

## 3. Verification

- [x] 3.1 Unit tests for sub-headings, sections, qualified matching, and the renumbering guard.
- [x] 3.2 Real-file test: all 96 Karamazov entries land on their own book's chapters.
- [x] 3.3 Full unit suite passes; debug APK builds.
