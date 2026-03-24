# Runtime Workspace

This directory stores temporary runtime data created during local development,
smoke testing, and deployment rehearsal.

## Purpose

- backend file storage when `APP_STORAGE_TYPE=LOCAL`
- generated smoke-test fixtures and temporary files
- local logs, caches, and scratch data created by scripts

## Rules

- do not commit runtime artifacts unless they are explicitly meant to be shared
- it is safe to delete this directory when you want a clean local environment
- keep the directory structure stable so scripts can write to predictable paths

## Current Project Defaults

- production target runtime: `Java 21`
- locally verified JDK: `Java 17`
- frontend port: `3001`
- backend port: `8081`
- local object storage base dir: `.runtime/storage`
