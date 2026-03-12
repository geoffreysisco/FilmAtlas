# FilmAtlas Development Backlog

## MUST FIX
*(true defects / broken behavior)*

1. **Rotate after clearing search shows blank screen**

   When in search mode and the query has been cleared, rotating the device lands in a blank empty state with no UI message.  
   Repro: Always  
   Scope: Medium

2. **Rotation bug between tabs and search results**

   Rotating from landscape after entering search mode while on the **Filter** or **Favorites** tab drops the user into the Discover tab with search results showing (tab label still says "search").  
   Rotating back returns to the Favorites tab but the tab label still says search. RecyclerView remains unaffected.  
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

## SHOULD FIX
*(polish affecting perceived quality)*

1. **Final production cleanup sweep**  

   Remove dead code, tighten comments, verify MVVM boundaries, and check regressions.  
   Scope: Deep

2. **Empty state polish**  

   Improve messaging and optionally add simple graphics.  
   Repro: Always  
   Scope: Medium

3. **Movie dialog animation polish**  

   Add subtle bounce/settle animation on dialog entrance.  
   Repro: Always  
   Scope: Quick

4. **Improve filter button visual depth** 

   Consider adding stroke/elevation to reduce flat appearance.  
   Repro: Always  
   Scope: Quick

5. **Possible icon refresh**  

   Review and potentially improve launcher/app icon design.  
   Scope: Medium

6. **Deduplicate title/year formatting**  

   Move formatting logic into a shared helper.  
   Scope: Quick

7. **Replace fragment tag magic strings with constants**  

   Scope: Quick

8. **Centralize Movie → MovieActionPayload builder** 

   Scope: Medium

9. **Lifecycle-safe async verification**  

   Ensure network callbacks or observers cannot update UI after Activity/Fragment destruction.  
   Repro: Sometimes  
   Scope: Deep

10. **Event delivery cleanup**  

    Ensure one-shot events (e.g., filter empty state events) do not re-fire on rotation unless intended.  
    Repro: Sometimes  
    Scope: Medium

11. **Accessibility polish**  

    Improve content descriptions, dialog focus order, and touch targets.  
    Repro: Always  
    Scope: Medium

12. **Consistency pass**  

    Unify naming conventions and ensure UI strings live in `strings.xml`.  
    Repro: Always  
    Scope: Medium

13. **Harden Glide usage**

- placeholder & error drawables
- caching strategy for TMDB images
- thumbnail loading for fast scroll
- RecyclerView preloading  
  Repro: Sometimes  
  Scope: Deep

14. **Centralize filter FAB visibility logic**  

    Derive FAB visibility from a single UI state source of truth.  
    Repro: Always  
    Scope: Deep

15. **Add scrim behind Favorite and Share icons**  

    In portrait mode the Favorite heart and Share icons appear directly over the poster image.  
    Icon color is currently chosen dynamically based on poster brightness.  
    Add a subtle scrim behind the icons to ensure readability across all poster backgrounds.  
    The scrim should adapt dynamically alongside the existing brightness logic.  
    Repro: Always  
    Scope: Medium

---

## NICE TO HAVE
*(future improvements / optional ideas)*

1. Consolidate search history recording logic into a small helper method.

2. Replace `observeForever` with `MediatorLiveData` or `Transformations.switchMap`.

3. Move history pseudo-movie creation into a helper/mapper.

4. Replace `SearchPolicyCallback` with a more generic event mechanism.

5. Add small unit tests for formatting helpers (title/year/share text).