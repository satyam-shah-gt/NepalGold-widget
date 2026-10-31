# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the versions are the ones the
app ships under.

## [Unreleased]

### Added

- **The transparency of the widget can be chosen.** The settings screen has a new control with
  three levels - Subtle, Balanced and Solid - that say how much of the wallpaper shows through
  the widget's glass surface. Changing it repaints every placed widget at once. Balanced is what
  the widget looks like today, so nothing changes until the user picks a level.

## [1.1] - 2026-10-31

### Added

- **The settings screen is rebuilt in the One UI 9 proportions.** Tapping the widget opens a
  screen with the latest rate as a glass focus block at the top, the language and the metal as
  segmented controls, and the refresh pinned to the bottom, where the thumb is.
- **The history is a table now**: the day, the rate and the change, for the metal the widget is
  showing. The heading and the rows share their column weights, so the numbers line up in a
  straight column, and the newest day is highlighted. The rates are the tola rates, formatted by
  the same helpers the widget uses, so the two surfaces cannot disagree.
- **A night palette for the settings screen.** It follows the system theme, and the numbers
  follow the selected language, including the Devanagari digits and the South Asian grouping.

### Changed

- The radio groups of the settings screen became segmented controls, the same control the widget
  uses for its own GOLD / SILVER switch.
- The settings screen is edge to edge: the insets are applied to the screen and to the pinned
  action bar, and the scrolling content keeps clear of the bar.

## [1.0] - 2026-10-30

The first version of the app.

### Added

- A 4x2 home screen widget: the latest rate, the change against the previous day, the day the
  rate was published, a chart of the last seven published days and a GOLD / SILVER switch that
  repaints every placed widget.
- A daily background fetch through WorkManager, plus a manual refresh.
- English and Nepali, including Devanagari digits and the South Asian 2-2-3 grouping Nepal uses
  (`2,43,055`).
- Thirty days of history on the device, filled in one go on the first run: the publisher keeps a
  month of rates, and the app asks for it.
- A notification after a successful fetch.
- A hidden settings screen, opened by tapping the widget: the language, the metal and the stored
  history.

### Notes

- The rate on the widget is the *latest*, not today's. The publisher updates once a day in the
  late morning Nepal time, so the widget says `LATEST` and prints the day the rate belongs to.
- The chart's x axis is a calendar: a day the publisher did not publish keeps its place and
  carries no point, so a gap in the line is visible instead of hidden.
