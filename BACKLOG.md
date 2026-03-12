# FilmAtlas Development Backlog

## MUST FIX
*(true defects / broken behavior)*

1. **Rotate after clearing search shows blank screen** ✅ FIXED

   When in search mode and the query has been cleared, rotating the device lands in a blank empty state with no UI message.

   Root cause:
   The browse restore guard (`restoringBrowseUi`) suppressed empty-state rendering even
   when the UI was in search mode.

   Fix:
   Restrict the browse restore guard so it does not run during search mode.

   ```java
   if (!inSearch && uiMode == UiMode.BROWSE && restoringBrowseUi)
   ```

2. **Rotate during search from Filter/Favorites resets return tab to Discover**

   Entering search from the **Filter** or **Favorites** tab correctly sets the cleared-search
   button to “Back to Filter/Favorites”. After rotating the device, the cleared-search button
   changes to “Back to Discover”, indicating the app now believes Discover is the return tab.

   In portrait, the UI also drops onto the Discover tab after rotation. In landscape, the
   correct Filter/Favorites tab location is preserved, but the cleared-search button still assumes
   Discover as the return target.

   RecyclerView content and search results remain correct throughout.

   Repro: Always  
   Scope: Deep

3. **Duplicate search results in landscape**

   Search occasionally displays **two identical result sets** in landscape orientation.  
   Repro: Edge (cannot reliably reproduce yet)  
   Scope: Medium

4. **Occasional network failure on initial load**

   The app sometimes launches directly into a **network failure state**.  
   Repro: Edge  
   Scope: Medium

5. **Search history dropdown behavior**

   When search pill focus returns, the **history dropdown should appear even if text is already present** in the pill.  
   Repro: Always  
   Scope: Medium

6. **Back button on Filter tab does not reopen Filter bottom sheet after search**

   After performing a search while on the Filter tab, pressing back should reopen the Filter bottom sheet.  
   Repro: Always  
   Scope: Medium

7. **Favorites RecyclerView not refreshed after exiting search with back button**

   Pressing back after performing a search while on the Favorites tab restores the tab label but leaves the RecyclerView displaying the previous search results.  
   Repro: Always  
   Scope: Medium

8. **Back button leaves invalid UI state**

   In search mode with results present, pressing back restores the original tab name but leaves the RecyclerView showing a network error message while on the Browser tabs (Discover, Popular and New).  
   Repro: Always  
   Scope: Medium

9. **Add Favorites Heart and Share Icon to landscape movie dialog**

   There is no way to share or favorite while in landscape mode; the buttons need to be added and wired correctly.  
   Repro: Always  
   Scope: Medium

---

## UX / STRUCTURAL
*(navigation, state, flow integrity)*

1. **Verify unified navigation behavior**

   Validate landscape layout behavior, rotation handling, navigation flow, and paging stability across all modes.  
   Repro: Sometimes  
   Scope: Deep

2. **Lifecycle-safe async handling**

   Verify async operations and observers are lifecycle-safe and properly cleaned up.  
   Repro: Sometimes  
   Scope: Deep

3. **Search entry/exit flow problem**

   There must be a clear way to **enter and exit search mode** and return to the origin tab.  
   Currently the tab is hijacked by search and there is no clean return path.  
   Repro: Always  
   Scope: Deep

4. **Explicit exit-search pathway**

   Add a clear pathway (possibly a FAB) for exiting search mode and returning to the active tab.  
   Repro: Always  
   Scope: Medium

5. **Centralize UI mode state**

   `MainActivity` currently tracks multiple UI state variables (`uiMode`, last tab state, etc.).  
   Consider introducing a small **UI state object** if complexity grows.  
   Repro: Sometimes  
   Scope: Deep

---

## ENGINEERING / REFACTOR
*(maintainability, architecture, internal cleanup)*

1. **Extract state-restore helpers from MainActivity**

   Reduce `onCreate()` / lifecycle complexity by extracting focused helpers for:
    - navigation restore
    - browse snapshot restore
    - filter snapshot restore
    - search UI restore

   Goal: improve readability without changing behavior.  
   Scope: Deep

2. **Centralize RecyclerView restore timing**

   RecyclerView state restoration currently happens through multiple timing-sensitive paths.  
   Unify restore flow so dataset binding and layout-state restore happen through a single, consistent pathway.  
   Scope: Deep

3. **Centralize Gson snapshot serialization/deserialization**

   `MainActivity` repeats Gson and `TypeToken<List<Movie>>` setup for browse/filter save and restore paths.  
   Move this into a small helper or shared utility to reduce duplication and improve readability.  
   Scope: Medium

4. **Review MainActivity state flags and restore guards**

   Document and, if safe, simplify restore-related flags such as:
    - `restoringSearchUi`
    - `restoringBrowseUi`
    - `restoredBrowseSnapshot`
    - `restoringFromRotation`
    - `pendingRvNavIndex`

   Goal: make restore flow easier to reason about without regressing behavior.  
   Scope: Deep