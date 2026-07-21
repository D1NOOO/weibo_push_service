# Design QA

- Primary source visual: `C:/Users/dino/.codex/visualizations/2026/07/21/019f8300-aa1e-7691-8b69-89ecd0395a4e/selected-option-2.png`
- Annotated hero source: `C:/Users/dino/AppData/Local/Temp/codex-clipboard-029c3667-5853-47a3-ad9c-bc4cdae02a44.png`
- Annotated rank source: `C:/Users/dino/AppData/Local/Temp/codex-clipboard-98acbf1a-5964-4611-80cd-729d59071e0c.png`
- Revised hero capture: `C:/Users/dino/.codex/visualizations/2026/07/21/019f8300-aa1e-7691-8b69-89ecd0395a4e/alignment-hero-after.jpg`
- Revised rank capture: `C:/Users/dino/.codex/visualizations/2026/07/21/019f8300-aa1e-7691-8b69-89ecd0395a4e/alignment-list-after.jpg`
- Mobile rank capture: `C:/Users/dino/.codex/visualizations/2026/07/21/019f8300-aa1e-7691-8b69-89ecd0395a4e/alignment-mobile-after.jpg`
- Focused comparison: `C:/Users/dino/.codex/visualizations/2026/07/21/019f8300-aa1e-7691-8b69-89ecd0395a4e/alignment-comparison.png`
- State: authenticated light theme with live data; desktop and 390 x 844 mobile layout.

## Findings

No actionable P0, P1, or P2 differences remain.

- Typography keeps the selected Apple editorial hierarchy and system font stack.
- The hero rank now starts on the same 32px content baseline as the overview and list sections; its trend action ends on the same right edge as the list controls.
- The rank header, podium badges, and ordinary rank numbers now share one measured center line.
- Desktop rank centers are all x=77px for ranks 1-8. Mobile rank centers are all x=45px for ranks 1-6.
- Red, orange, and gold remain restricted to top-three rank semantics; interaction color remains Action Blue.
- The existing flame raster asset remains sharp and correctly scaled. No asset substitution was introduced.
- Copy and live product data are unchanged.

## Comparison History

### Iteration 1

- Fixed the freshness block ordering so the editorial story band remains the first content section.
- Switched chart emphasis to Action Blue and removed crowded x-axis labels.

### Iteration 2

- Destroyed canvas-bound Chart.js instances before initialization to prevent warnings after reload.

### Iteration 3

- Added the flame asset and compact slanted badges for ranks 1-3.
- Increased the rank column only as much as required and preserved the mobile heat-value column.

### Iteration 4

- Earlier P2: the hero used 64px horizontal padding while surrounding content used 32px, visibly breaking the page grid.
- Fix: changed hero horizontal padding to 32px. Post-fix measurements show the hero rank, overview, and list section all start at x=32px; the trend action and list section both end at x=1242.4px.
- Earlier P2: podium badges had an extra 10px left margin and ordinary ranks had no fixed centered box.
- Fix: assigned every rank a shared 54px centered box on desktop and 50px on mobile, removed the podium offset, and centered the rank column heading.
- Post-fix evidence: `alignment-comparison.png`, `alignment-list-after.jpg`, and `alignment-mobile-after.jpg`.

### Iteration 5

- Source annotations: the four configuration-card comments captured from the authenticated configuration page at 960 x 650.
- Earlier P2: the dedupe select used an isolated 8px radius while numeric and Sink inputs fell back to square browser defaults; the Sink labels also remained inline and collided with 17px input text.
- Fix: standardized configuration cards at 18px radius with 24px padding, form controls at 44px height with 10px radius and 14px input text, and save buttons at the same minimum height.
- Fix: moved Sink labels above their fields in a two-column grid with a 16px gutter; the grid collapses to one column below 900px.
- Desktop measurements: dedupe/frequency/retention controls are each 188 x 44px; Sink inputs are 320 x 44px. All use the same border and radius.
- Mobile measurements at 390 x 844: document width remains 385px with no horizontal overflow; Sink fields are 303.2 x 44px and stay within their card.
- Interaction check: changing the fetch interval or dedupe window enables its save button; restoring the loaded value disables it again. No settings were saved during QA.
- Post-fix evidence: `config-form-comparison.png`, `config-controls-after.jpg`, `config-sink-after.jpg`, and `config-mobile-sink-after.jpg`.

## Interaction and Responsive Checks

- Live hot-search data loaded after the preview proxy was updated from the old host port 8080 to 28080.
- At 390 x 844, document scroll width equaled viewport width (385px); no horizontal overflow was present.
- Rank classes remain based on real rank values: `podium rank-1`, `podium rank-2`, `podium rank-3`, then `mid`.
- JavaScript syntax and Git whitespace checks passed.
- Browser logs contain only the earlier preview-proxy failures before the port correction; the post-correction reload loaded live rows successfully.

## Follow-up Polish

- P3: a future backend time-series endpoint could replace the top-10 bar chart with a true daily line chart.

final result: passed
