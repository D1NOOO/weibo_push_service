# Project Guidance

## Scope

- This is a Spring Boot 3.2 / Java 21 service with a native HTML, CSS, and JavaScript admin UI.
- Backend code lives in `src/main/java/com/hotsearch`; static UI files live in `src/main/resources/static`.
- Keep changes focused on the requested behavior. Preserve unrelated local modifications.

## Development

- Follow the existing package structure, naming, validation, and exception-handling conventions.
- Prefer existing services, DTOs, repositories, utilities, and browser-side helpers over new abstractions.
- Do not add production dependencies or change public APIs unless the task requires it.
- Never hard-code credentials, webhook URLs, tokens, or other secrets. Do not print values from `.env`.
- Preserve UTF-8 encoding for Chinese copy.

## UI Design

- Use system fonts: `SF Pro Display`, `SF Pro Text`, `-apple-system`, `BlinkMacSystemFont`, `PingFang SC`, and suitable fallbacks.
- Follow the Apple-inspired design language documented at `https://github.com/VoltAgent/awesome-design-md/tree/main/design-md/apple`: near-black text, white/parchment surfaces, one blue interactive accent, generous spacing, light hairlines, and restrained elevation.
- Use blue only for interactive emphasis. Keep semantic success, warning, and danger colors limited to status feedback.
- Prefer typography, alignment, spacing, and subtle dividers over decorative cards, gradients, or shadows.
- Use 600 rather than 700 for display text; keep body text at weight 400 with readable line height.
- Use consistent radii: 8px for compact controls, 18px for utility cards, and full pills for primary actions, search, and filters.
- Buttons must have visible hover, active (`transform: scale(0.95)`), focus-visible, and disabled states.
- Preserve responsive behavior, keyboard access, reduced-motion preferences, and sufficient color contrast.
- Do not replace working interactions or element IDs without updating and verifying their JavaScript consumers.

## Verification

- Run the narrowest relevant check first.
- For backend changes, prefer targeted tests such as `mvn -Dtest=<TestClass> test` before broader verification.
- For UI changes, verify the affected flow in the browser at desktop and mobile widths, inspect console errors, and review the final diff.
- Report checks that were not run and pre-existing failures separately.

## Git

- Do not commit, push, reset, or discard user changes unless explicitly requested.
