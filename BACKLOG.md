# FilmAtlas Development Backlog

## Contents
- [KNOWN BUGS](#known-bugs)
- [UX / POLISH](#ux--polish)
- [DEFERRED / REFACTOR](#deferred--refactor)

---

## KNOWN BUGS
*(reproducible or clearly observed defects)*

1. **Intermittent blank screen after exiting search**

   Exiting search mode back to Discover can occasionally leave the movie grid blank with no visible content or empty state.

   Repro: Intermittent (~1 in 10)  
   Scope: Medium  
   Status: UNRELIABLE REPRO

   Notes:  
   Likely related to timing between dataset emission and RecyclerView visibility restore.

   Next:  
   Capture a reliable repro before attempting a fix.

---

## UX / POLISH
*(visible improvements, not correctness issues)*

1. **Add scrollbar indicator to search suggestions**

   The suggestions / recent searches list supports vertical scrolling but does not show a visible scrollbar indicator.

   Repro: Always  
   Scope: Small

---

2. **Add Favorites and Share actions to landscape movie dialog**

   Landscape movie details dialog does not expose favorite/share actions consistently with portrait mode.

   Repro: Always  
   Scope: Medium

---

## DEFERRED / REFACTOR
*(non-blocking engineering improvements)*

1. **Extract state-restore helpers from MainActivity**

   `MainActivity` still owns a large amount of navigation, search, filter, and rotation restore logic.

   Potential extractions:
   - navigation restore
   - browse snapshot restore
   - filter snapshot restore
   - search UI restore

   Scope: Deep  
   Status: Deferred

---

2. **Centralize RecyclerView restore timing**

   RecyclerView state restoration depends on multiple timing-sensitive pathways.

   Goal:  
   Unify dataset binding and layout-state restore into a single, consistent flow.

   Scope: Deep  
   Status: Deferred

---

3. **Review restore flags and guards**

   Restore-related flags currently include:

   - `restoringSearchUi`
   - `restoringBrowseUi`
   - `restoredBrowseSnapshot`
   - `restoringFromRotation`
   - `pendingRvNavIndex`

   Goal:  
   Improve clarity and reduce cognitive overhead without regressing behavior.

   Scope: Deep  
   Status: Deferred  