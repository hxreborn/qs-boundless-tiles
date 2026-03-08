# Code Review Prompt: QSBoundless-Tiles

You are reviewing a complete Android/Xposed module called **QSBoundless-Tiles** that removes the 6-tile limit in Android's Quick Settings panel. Review ALL code for quality, best practices, KISS principle, Unix philosophy, and absence of "vibe coding."

---

## Project Context

This is an LSPosed/Xposed module with:
- **Host app**: Kotlin + Jetpack Compose UI for configuration
- **Xposed hooks**: Injected into SystemUI to manipulate tile binding
- **IPC**: ContentProvider + SharedPreferences for app ↔ hook communication
- **Architecture**: MVVM with Repository pattern

---

## Code Locations

### UI Layer
- `app/src/main/kotlin/eu/hxreborn/qsboundlesstiles/ui/`
  - `DashboardScreen.kt` - Main Compose UI
  - `DashboardViewModel.kt` - ViewModel with state flows
  - `DashboardUiState.kt` - UI state sealed classes
  - `MainActivity.kt` - Entry point, service binding

### Xposed Hooks
- `app/src/main/kotlin/eu/hxreborn/qsboundlesstiles/hook/`
  - `TileServicesHook.kt` - Hooks tile service management
  - `TileActivityHook.kt` - Hooks tile click handling

### Preferences & IPC
- `app/src/main/kotlin/eu/hxreborn/qsboundlesstiles/prefs/`
  - `Prefs.kt` - Preference definitions
  - `PrefSpec.kt` - Preference specifications
  - `PrefsManager.kt` - Remote prefs management (hook side)
  - `PrefsRepository.kt` - Local prefs with Flow

- `app/src/main/kotlin/eu/hxreborn/qsboundlesstiles/provider/`
  - `HookDataProvider.kt` - ContentProvider for IPC

### Utilities
- `app/src/main/kotlin/eu/hxreborn/qsboundlesstiles/util/`
  - `RootUtils.kt` - Root commands
  - `Logger.kt` - Logging abstraction

- `app/src/main/kotlin/eu/hxreborn/qsboundlesstiles/scanner/`
  - `TileScanner.kt` - Query third-party tile providers

- `app/src/main/kotlin/eu/hxreborn/qsboundlesstiles/`
  - `QSBoundlessTilesApp.kt` - Application class
  - `QSBoundlessTilesModule.kt` - Xposed module entry

---

## Review Checklist

### 1. Architecture & Design
- [ ] Each class/file has a single, clear responsibility
- [ ] Dependencies flow in one direction (no circular dependencies)
- [ ] No over-abstraction (unnecessary interfaces, factories, wrappers)
- [ ] No under-abstraction (logic that should be extracted is inline)
- [ ] IPC mechanism is appropriate for the use case

### 2. Kotlin & Compose Best Practices
- [ ] Proper use of `val` vs `var`
- [ ] Null safety handled correctly (no `!!` unless justified)
- [ ] Coroutine scopes are appropriate (viewModelScope, lifecycleScope)
- [ ] StateFlow/SharedFlow used correctly
- [ ] Compose recomposition is optimized (stable keys, no unnecessary lambdas)
- [ ] No memory leaks from collectors/listeners

### 3. Xposed Hook Patterns
- [ ] Reflection usage is minimal and safe (fields/methods exist check)
- [ ] Hook callbacks are thread-safe
- [ ] BeforeInvocation/AfterInvocation patterns correct
- [ ] No blocking operations in hooks
- [ ] Proper error handling (failures don't crash SystemUI)

### 4. Error Handling
- [ ] Consistent error handling strategy (runCatching vs explicit checks)
- [ ] Errors are logged appropriately
- [ ] Failures are contained, not propagated to crash SystemUI

### 5. Performance
- [ ] No unnecessary allocations in hot paths
- [ ] Background work dispatched to IO/Default dispatchers
- [ ] SharedPreferences commits use `commit` vs `apply` appropriately
- [ ] ContentProvider doesn't do heavy work on main thread

### 6. Code Smells & Vibes
- [ ] **NO vibe coding**: No trendy patterns without justification
- [ ] **NO over-engineering**: No unnecessary indirection
- [ ] **NO under-engineering**: No magic numbers, hardcoded strings
- [ ] **NO cargo cult**: No copied code whose purpose is unknown
- [ ] Naming is clear and consistent
- [ ] Constants are extracted appropriately

### 7. Unix Philosophy
- [ ] Each tool/module does one thing well
- [ ] Small, focused functions
- [ ] Composable parts (can be tested/replaced independently)

---

## Specific Questions to Answer

### For Each File:

1. **What does this file do?** (One sentence)
2. **Does it do only that?** (Single responsibility)
3. **What's the error handling strategy?** Is it consistent?
4. **What could go wrong?** (Edge cases, nulls, races)
5. **Is this the simplest approach?** (KISS)
6. **Any vibe coding indicators?** (Unnecessary abstraction, copied patterns)

### Architecture Questions:

1. **Why is IPC done this way?** (ContentProvider + SharedPreferences vs alternatives)
2. **Who owns the state?** (Where does truth live?)
3. **What's the threading model?** (Which thread runs what?)
4. **How do hooks communicate with the app?** (Is this reliable?)

### Specific Code Review:

#### DashboardViewModel.kt
- Is `combine()` usage correct?
- Are the state flows properly upstreamed?
- Is there any business logic that shouldn't be in ViewModel?

#### MainActivity.kt
- Is the service binding lifecycle correct?
- Are listeners properly registered/unregistered?
- Any potential memory leaks?

#### TileServicesHook.kt
- Why are those specific methods hooked?
- How does `recalculateBindAllowance` modification work?
- Is the reflection safe (what if fields don't exist)?

#### TileActivityHook.kt
- Why use `ConcurrentHashMap` for pending clicks?
- When is the cache cleared?
- Thread safety of the label cache?

#### PrefsManager.kt
- Why cache values locally AND read from remote?
- Is the change listener thread-safe?
- What happens if SystemUI dies?

#### HookDataProvider.kt
- Is `call()` thread-safe?
- Why use string serialization for events?
- Any race conditions in event appending?

---

## Output Format

For each file, provide:

```
## [Filename]

### Purpose
One sentence description

### Strengths
- What works well

### Issues Found
| Issue | Severity | Location | Fix |
|-------|----------|----------|-----|
|         |          |          |     |

### Vibe Check
- [ ] Clean / [ ] Vibey
- Reasoning: 

### KISS Adherence
- [ ] Pass / [ ] Fail
- Reasoning:

### Specific Questions
Any open questions about this code?
```

---

## Final Verdict

At the end, provide:

```
## Summary

### Overall Code Quality
[Excellent / Good / Needs Work / Poor]

### Major Issues Found
1. 
2. 
3. 

### Recommendations
1. 
2. 

### Vibe Coding Score (1-10)
[ ] 1 - Industrial grade
[ ] 5 - Acceptable  
[ ] 10 - Pure vibes

### Would I Ship This?
[Yes / No / With Fixes]
```

---

## Files to Review (in order)

1. DashboardScreen.kt
2. DashboardViewModel.kt
3. DashboardUiState.kt
4. MainActivity.kt
5. TileServicesHook.kt
6. TileActivityHook.kt
7. Prefs.kt
8. PrefSpec.kt
9. PrefsManager.kt
10. PrefsRepository.kt
11. HookDataProvider.kt
12. RootUtils.kt
13. Logger.kt
14. TileScanner.kt
15. QSBoundlessTilesApp.kt
16. QSBoundlessTilesModule.kt
