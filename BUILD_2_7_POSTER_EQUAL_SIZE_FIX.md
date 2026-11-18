# Build 2.7 — Home TMDB poster equal-size correction

Both Home poster rows call the same HomeTmdbRow composable with the same cardWidth. The previous viewport calculation could allocate more vertical space than was available, causing the lower TV Series row to be clipped and appear smaller. This patch reserves 92dp for labels/row padding and computes the width from the available height for TWO 3:2 portrait rows, removes excess top padding, and permits smaller widths on short displays instead of forcing a minimum that overflows. No other layout, logos, or playback changes intended.

Source patch only; Android TV compilation and device testing not performed.
