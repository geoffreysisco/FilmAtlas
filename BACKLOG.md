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

4. **Occasional network failure on initial load** ✅ FIXED

The app sometimes launches directly into a **network failure state**.

Root cause:
Discover random start page could exceed API `totalPages`, producing an empty (200 OK) response.

Fix:
Detect out-of-range page after receiving `totalPages`, reselect a valid page, and retry before empty-state handling.

5. **Search history dropdown behavior**

When search pill focus returns, the **history dropdown should appear even if text is already present** in the pill.
Repro: Always
Scope: Medium

6. **Back button on Filter tab does not reopen Filter bottom sheet after search** ✅ FIXED

After performing a search while on the Filter tab, pressing back should reopen the Filter bottom sheet.

Root cause:
Back press exited search mode but did not route through the correct tab restoration path.

Fix:
Route back press through `exitSearchBackToLastTab()` to restore the correct mode and UI state.

7. **Favorites RecyclerView not refreshed after exiting search with back button** ✅ FIXED

Pressing back after performing a search while on the Favorites tab restores the tab label but leaves the RecyclerView displaying the previous search results.

Root cause:
Back press restored the tab shell but did not restore the Favorites dataset.

Fix:
Route back press through `exitSearchBackToLastTab()` so exiting search from Favorites restores both tab state and RecyclerView content.

8. **Back button leaves invalid UI state** ✅ FIXED

In search mode with results present, pressing back restores the original tab name but leaves the RecyclerView showing a network error message while on the Browser tabs (Discover, Popular and New).
Root cause:
UI state (tab label vs RecyclerView content) became inconsistent when exiting search.

Fix:
Unified back press handling to fully restore tab mode, dataset, and UI state.


9. **Add Favorites Heart and Share Icon to landscape movie dialog**

There is no way to share or favorite while in landscape mode; the buttons need to be added and wired correctly.
Repro: Always
Scope: Medium

---