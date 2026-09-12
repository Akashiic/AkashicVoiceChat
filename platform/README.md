# Akashic Voice Platform

Server-side Java platform for cross-backend and cross-protocol Akashic Voice routing.

This directory is intentionally isolated from the Forge 1.7.10 client artifact. The legacy mod remains Java 8; Paper/SVC modules target Java 21; Velocity 4.0.0 modules target Java 25.

## Modules

- `bridge-core`: Minecraft-independent bridge models and Gateway link contracts.
- `svc-paper`: Paper 1.21.1 adapter compiled only against the public Simple Voice Chat API 2.6.20.
- `gateway-velocity`: Velocity 4.0.0 gateway/bootstrap module.

## Current Gate 6 milestone

The current SVC adapter and Gateway bootstrap codec compile and have mapping/model tests. The adapter uses raw 48 kHz / 960-sample / 20 ms Opus frames without decoding them.

The `svc-paper` plugin currently installs a deliberately disconnected `GatewayLink`. This milestone is **not** a functional cross-server bridge yet. Secure agent transport and Gateway UDP routing are the next gates.
