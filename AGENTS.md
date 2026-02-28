# AGENTS.md

This file defines development rules for AI coding agents working in this repository.

## General Principles

- Follow existing project architecture and coding conventions.
- Prefer small, focused changes rather than large refactors.
- Do not modify unrelated files.
- Ensure the project builds successfully after changes.
- Add tests when introducing new logic.
- Avoid introducing new dependencies unless necessary.

---

# Git Commit Rules

Use **Conventional Commits**.

Format:

`<type>(scope): <short summary>`

Examples:

feat(auth): add OAuth2 login support
fix(api): handle null response from payment service
refactor(user): simplify profile update logic
docs(readme): update setup instructions

## Allowed Types

feat     → new feature  
fix      → bug fix  
refactor → code refactoring (no behavior change)  
docs     → documentation changes  
test     → add or modify tests  
chore    → tooling / build changes  
perf     → performance improvement  
style    → formatting / linting only

## Commit Message Rules

- Subject line must be **≤ 72 characters**
- Use **imperative mood** (e.g., "add", not "added")
- Do not include a period at the end of the subject
- Add a body when the change is non-trivial

Example:

feat(auth): add JWT refresh token support

- implement refresh endpoint
- add token expiry validation
- update auth middleware

---

# Pull Request Rules

When creating a PR:

1. Provide a clear title following Conventional Commits
2. Include a summary of the change
3. Explain why the change was necessary
4. Mention breaking changes if any
5. Ensure tests pass

PR template:

Summary:
- What changed

Reason:
- Why the change was needed

Impact:
- Affected modules / APIs

Testing:
- How the change was validated

---

# Code Quality Rules

- Prefer readability over cleverness
- Follow existing naming conventions
- Functions should generally stay under ~50 lines
- Avoid deeply nested conditionals
- Extract reusable logic into utilities

---

# Testing Rules

- New logic should include tests
- Fixing a bug should include a regression test
- Avoid flaky tests
- Prefer deterministic unit tests

---

# Security Rules

- Never commit secrets
- Do not hardcode API keys
- Validate user input
- Handle errors explicitly