# Security Policy

## Educational scope

SmileBoss AI is an educational and architectural reference project. The demo authentication, seed accounts and local H2 data are not a production security baseline. Before processing real candidate data, replace demo authentication with password hashing and enterprise identity, implement full RBAC/tenant isolation, use least-privilege database accounts, and complete an independent security/privacy review.

## Secrets

This repository must never contain real API keys, database or Redis passwords, JWT signing keys, DingTalk webhooks/secrets, private certificates, candidate exports, uploaded resumes, or populated `.env` files.

- Copy `.env.example` locally and keep the resulting `.env` untracked.
- Inject production values with a Secret Manager or deployment-platform secret store.
- If a secret is committed, revoke/rotate it immediately. Deleting it from the latest commit is insufficient because Git history remains accessible.
- Run a secret scanner before every public release.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting feature when it is enabled for this repository. Do not open a public issue containing credentials, candidate personal data, exploit details for an unpatched issue, or private infrastructure information.

Include the affected component, reproducible steps, impact, and a suggested mitigation if available. Remove all real personal data and credentials from screenshots, logs, requests and examples.
