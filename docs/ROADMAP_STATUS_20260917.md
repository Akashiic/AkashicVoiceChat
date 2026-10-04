# Akashic Voice — roadmap reconciliado por evidência

Data: 2026-09-17

Este documento reconcilia `docs/ROADMAP.md` (histórico e hoje desatualizado) com os checkpoints realmente
implementados, compilados, testados e/ou observados em runtime. Ele não reescreve o histórico.

Status usados:

- **DONE** — implementação presente e coberta por gates automatizados/artefato.
- **QUALIFIED** — DONE mais carga, soak, fault/recovery ou evidência runtime relevante.
- **LIVE-GATE** — engenharia disponível; falta evidência de ambiente/hardware/aceitação real.
- **CONDITIONAL** — só deve virar código se a medição live provar necessidade.
- **DEFERRED-VNEXT** — deliberadamente fora do stable-v1 RC5; não é bug oculto nem requisito para 100% do stable-v1.

## Resumo executivo

- Baseline selado: RC5, checkpoint `AV-LEGACY-AGENT-OBSERVABILITY-RC5-20260917-47`.
- Forge RC5 SHA-256: `6fab0865c2d2c5a0e382e193f35e099aa7e0861ab4317b0c78a91796ef8d553e`.
- O mesmo JAR está atualmente instalado no cliente Technic Forge 1.7.10 real (CLIENT62).
- Estimativa atual: **99%**, faixa de julgamento **98–99.5%**.
- Stable release: **não aprovado**.
- Auditoria de fonte RC5: nenhum TODO/FIXME/not-implemented de produção encontrado.
- O 1% restante é composto por gates externos de aceitação, não por uma feature de engenharia esquecida.

## Fase A — fundação, codec e captura

| Item histórico | Estado reconciliado | Evidência/decisão |
| --- | --- | --- |
| Opus nativo por padrão + fallback Java | **DONE** | Backend nativo e Concentus seguem cobertos pela matriz Java 8 e pelos testes de interoperabilidade/qualidade. |
| Capture backend explícito + fallback | **DONE** | JavaSound/OpenAL SPI, seleção/fallback e recuperação de captura passaram pelos checkpoints de mic/capture. |
| Modo estrito sem fallback silencioso | **DONE** | Política de backend explícita testada; native-unavailable possui caminho QA próprio. |
| Resiliência de microfone/reopen | **QUALIFIED** | Continuidade de sequência, gerações de captura e autorização foram testadas; CLIENT59/62 observam OpenAL capture real em runtime. |
| Benchmarks comparáveis | **DONE** | `docs/PERFORMANCE.md` registra custo nativo vs Java; não é benchmark universal de gameplay. |
| Estado visual/configuração | **DONE** | Hub/settings/quality e diagnósticos de sessão/decoder estão implementados. |
| Soak concorrente | **QUALIFIED** | CLIENT26: 36.000 MIC + 36.000 rádio; CLIENT40: 302.400 MIC + 302.400 rádio + reconnect/transfer prolongados. |

## Fase B — protocolo seguro e transporte

| Item histórico | Estado reconciliado | Evidência/decisão |
| --- | --- | --- |
| Capability negotiation | **DONE** | `VoiceCapabilities` e caminhos epoch/legacy negociados e testados. |
| Session ID explícito | **DONE** | CLIENT22 `AV-EXPLICIT-SESSION-ID-20260914-22`, 8/8 mutações, legacy vector preservado. |
| Session ID autenticado/AAD | **DONE** | Handshake/session-bound e `SessionCrypto` session-aware sem contaminar o canal TCP do agent. |
| Taxonomia wire/epoch/reset | **DONE** | V3 + epoch/reset e fallback legado presentes, com stale/replay rejection. |
| Rehandshake/key lifecycle | **QUALIFIED** | Transfer/restart/rebind/multihop CLIENT36–44 e route recovery real CLIENT61. |
| MTU <= 1200 | **DONE** | CLIENT20: alvo UDP 1200 bytes; AV Opus 1124 no caminho legado dessa etapa e limites ajustados para session-bound posteriormente. |
| Métricas agregadas sem spam | **DONE** | CLIENT20: telemetria agregada e `/avdebug`, sem IDs/secrets no snapshot. |
| Fail-closed/security regressions | **QUALIFIED** | Replay, stale session, rate limits, NAT rebind, session identity e mutações negativas cobertas por gates posteriores. |

## Fase C — qualidade percebida, jitter e controles

| Item histórico | Estado reconciliado | Evidência/decisão |
| --- | --- | --- |
| Jitter adaptativo | **QUALIFIED** | `DecoderStream` usa `new OpusJitterBuffer()` no caminho de produção; CLIENT27 mediu perda/jitter/reordenação com codecs reais. |
| PLC | **QUALIFIED** | CLIENT27 mediu PLC e continuidade sob perda determinística, sem decode/resync errors nos cenários aprovados. |
| Opus in-band FEC | **DEFERRED-VNEXT** | Concentus expõe controles FEC, mas `opus-jni-rust 1.0.4` usado no backend nativo não oferece paridade equivalente. Ativar só no fallback Java criaria semântica dependente do backend. |
| Volume/mute/deafen por jogador | **DONE** | CLIENT11–14: isolamento de ganho, deafen, speaker controls e GUI individual session-safe. |
| Whisper/distance/proximity semantics | **DONE** | `SvcPacketMapper` preserva `isWhispering()`/`getDistance()` em `BridgeDelivery`; Gateway os escreve em `SoundFrameV2/V3`; jitter/decoder/playback preservam flag e maxDistance até OpenAL. |
| Rotulagem honesta do DSP | **DONE** | O filtro existente é apresentado como high-pass, não como RNNoise/noise suppression geral. |
| AGC/limiter de áudio | **CONDITIONAL** | Não existe no RC5 por decisão. O roadmap original já exigia medição acústica real antes de adicionar DSP destrutivo/ganho automático. Só deve virar código se o gate acústico demonstrar necessidade. |
| Presets de qualidade | **DONE** | CLIENT15: 24/32/48/64 kbit/s, custom preservado, aplicação live entre frames, backends nativo/Java testados. |
| Cap de decoders caros | **DONE** | CLIENT28 limita streams de decoder concorrentes e preserva backlog/cleanup sob capacidade. |

## Fase D — gateway, cross-server e produto servidor

| Item histórico | Estado reconciliado | Evidência/decisão |
| --- | --- | --- |
| Gateway/bridge SVC | **QUALIFIED** | Gateway Velocity + agent + SVC Paper estão no RC5; CLIENT61 observou bootstrap/auth/route recovery no endpoint público configurado. |
| Raw Opus fast path | **DONE** | SVC Opus é encaminhado sem transcoding no caminho suportado, preservando identidade/posição/distance/whisper/category. |
| Transcoder fallback genérico | **DEFERRED-VNEXT** | Não existe no RC5 e não é necessário para a topologia fast-path suportada. Se um futuro produtor usar formato incompatível, deverá existir um adapter/transcoder explícito, não conversão silenciosa. |
| Groups cross-server | **DONE** | Diretório federado, persistência, limites/TTL e legado endurecido CLIENT21; routing deduplicado. |
| Rádio cross-server | **DONE** | CLIENT25: rádio bounded, group ∪ radio sem duplicação, reset direcionado e hot-path sem alocação de recipient sets por frame. |
| Moderação/permissions | **DONE** | CLIENT23: mute persistente/checksummed, persist-before-publish, filtros MIC/SVC e hard reset. |
| Addon API pública | **DONE** | CLIENT24: API v1 separada, 9 classes públicas, sem raw audio/per-frame callbacks, addon externo compilado. |
| Reconexão/failover/transfer | **QUALIFIED** | CLIENT36–44: restart/transfer/multihop/NAT/logical rebind; CLIENT40 soak prolongado; CLIENT61 route recovery real. |

## Fase E — mobile/touch e validação física

| Item histórico | Estado reconciliado | Evidência/decisão |
| --- | --- | --- |
| Touch PTT / GUI touch-safe | **DONE (engenharia)** | CLIENT17: 25 contratos touch, 11/11 mutações, Forge/Java8/JUnit/JAR verdes. |
| Pojav/Amethyst real | **LIVE-GATE** | Nenhum dispositivo móvel real foi autorizado/observado nesta sessão; não há evidência térmica, bateria ou input físico. |
| Native discovery/packaging | **DONE em desktop/JAR** | Natives/JNI e inventário são verificados em todos os gates de distribuição; comportamento em runtimes móveis continua LIVE-GATE. |
| Android-specific capture adapter | **CONDITIONAL** | Só deve ser implementado se dispositivos reais demonstrarem falha do backend existente. Não é requisito preventivo. |
| Touch layouts compactos | **DONE (engenharia)** | Hub, quality, speaker controls e hold-to-talk possuem fallbacks compactos/sem pausa do jogo. |
| Thermal/battery/perf mobile | **LIVE-GATE** | Requer aparelho físico e sessão prolongada; não deve ser inferido a partir de desktop/JVM. |
| Shrinker/obfuscation mobile | **CONDITIONAL** | Não há evidência de que o empacotamento atual do mod Forge exija shrinker; só entra se a cadeia mobile concreta introduzir isso. |

## Gates finais para 100% stable-v1

Os seguintes pontos continuam deliberadamente fora de `DONE`; fechar estes gates é a condição objetiva para sair de 99%:

1. **Controlled backend process restart** — reiniciar de fato o processo backend/agent autorizado e provar recuperação de voz sem sessão stale/leak. CLIENT60/61 provam lifecycle/recovery, mas não um restart controlado do processo backend.
2. **Two-client remote media/listening** — dois clientes reais, preferencialmente incluindo rota cross-backend, com voz bidirecional observada. CLIENT62 prova runtime autenticado/capture/receive, mas não prova que outro jogador foi ouvido.
3. **Acoustic/AGC decision** — escuta física e métricas de microfone/saída para decidir explicitamente `AGC/limiter = não necessário` ou implementar/qualificar o DSP se necessário.
4. **Real mobile acceptance** — Pojav/Amethyst em dispositivo real: capture/playback, touch PTT, background/focus, thermal, battery e sessão prolongada.
5. **Controlled production canary** — operador/jogadores reais, rollback pronto, telemetria/health estáveis e aceite explícito.

Nenhum desses cinco gates deve ser convertido em PASS por smoke sintético, thread dump, soak local ou documentação.
