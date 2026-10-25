# Akashic Voice — resumo em português

**Data:** 2026-10-04 · **Estado:** release candidate **RC7**, sem versão estável, sem deploy de produção aprovado.

Este é um resumo para o dono do projeto (Gabriel / `Akashiic`). Os documentos técnicos completos estão em inglês e são a referência: [`PROJECT_STATE.md`](PROJECT_STATE.md), [`HANDOFF.md`](HANDOFF.md), [`HOMOLOGATION.md`](HOMOLOGATION.md).

## O que é

Voice chat espacial (Opus) para Minecraft 1.7.10 / Forge 10.13.4.1614, derivado do ProxyChat (MIT, Julius Meinking) e reescrito para a Rede Akashic. Cliente em Java 8; transporte UDP 24454 com HKDF-SHA-256 + AES-128-GCM; codec libopus nativo com Concentus como reserva. Os "companions" de Paper (1.21.1, Simple Voice Chat 2.6.20) e Velocity (4.0.0) ficam dentro do próprio projeto, na pasta `platform/`.

## Sobre a versão que você usa

O rótulo `0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty` **não identifica um build**: ele só diz "HEAD era o commit 022dc1f, com alterações não commitadas". Esse mesmo rótulo aparece no JAR de desenvolvimento e em RC1 a RC7. O JAR instalado no seu cliente Technic é o **RC7** (SHA-256 `c0f9d86c…eb081`, conferido por hash), e não o código do commit 022dc1f. Detalhes e tabela de hashes: [`VERSIONING_AND_PROVENANCE.md`](VERSIONING_AND_PROVENANCE.md).

## Onde o projeto está

- Portões automatizados (build, matriz Java 8, 186 testes da plataforma, mutações, verificação independente do selo): **todos PASS no RC7**, segundo os recibos de 2026-09-17. Eu **não reexecutei** esses testes nesta consolidação.
- Progresso estimado (julgamento, não medição): 99 % do escopo estável-v1 principal (RC5) e 97 % do escopo expandido com recursos premium (RC7). Meu parecer: tratar os 97 % como **limite superior** até triar os defeitos de campo abaixo.
- Nada está **aprovado ao vivo**. Faltam seis portões ao vivo (G1–G6): reinício controlado do backend, escuta com dois clientes via WAN, revisão acústica + decisão sobre AGC, aceitação em celular real, canário em produção, aceitação dos extras premium.

## O que os logs reais mostraram (154 logs de desktop, 59 de Android)

1. **Desktop:** depois de um recarregamento do sistema de som do Minecraft, as bibliotecas nativas do OpenAL ficam "desligadas" e falham a thread do microfone, a reprodução por quadro e a sonda de EFX (issue #3).
2. **Desktop:** 32 timeouts do servidor de voz perto de transferências de backend (issue #4); eventos de recuperação de saída em sessões comuns (issue #5).
3. **Android (aparelho real):** o mod carrega e autentica uma sessão local, mas **nenhum backend de captura abriu o microfone (10 de 10 sessões)** (issue #18); numa execução a thread de áudio morreu por `OutOfMemoryError: Out of stack space`, associado ao poll de saúde de saída do RC7 em ambiente LWJGL 3; a causa exata (hipótese H3, vazamento de `MemoryStack`) ainda não foi provada (issue #17).
4. Menores: `modern_fonts.json` ausente (#6); assinatura de OOM na thread de áudio (#7).

**Atualização 2026-10-25:** quando o AkashicMixer está instalado, o Voice não recarrega mais todos os sons do jogo a cada 5 s quando o fone/caixa some; ele espera o AkashicMixer reiniciar o motor de som (que agora faz isso sozinho e sem congelar). Quando o AkashicMixer segue o dispositivo padrão do Windows, a troca de dispositivo padrão também passa a esperar o Mixer (antes essa detecção nunca disparava no pack, porque o OpenAL da versão usada não atualizava a lista de dispositivos). Só compilado (BUILD), não testado no jogo. Detalhes: `ISSUES_REGISTER.md` e a evidência no AkashicMixer.

Os testes automatizados usam áudio falso/loopback e por isso não enxergaram nada disso. Análise completa: [`FIELD_EVIDENCE_2026-10.md`](FIELD_EVIDENCE_2026-10.md).

## Pesquisa em outros projetos do PC

Não existe pasta "Akashic Compatibility Lab". Os projetos relacionados encontrados (launcher Android, modpack Technic, world-firewall, Player-Backport usado como modelo de documentação etc.) estão em [`RELATED_PROJECTS.md`](RELATED_PROJECTS.md).

## O que fazer em seguida (ordem recomendada)

1. Definir identidade de build única (#2, #16) e reconstruir a partir de um commit limpo (#15).
2. Triar os defeitos de campo (#3, #4, #5, #7, #17, #18) com testes de regressão que usem o OpenAL real / LWJGL 3.
3. Reverificar os itens herdados (#8).
4. Só depois rodar os portões ao vivo G1–G6 (#9–#14).

## Regras que continuam valendo

Sem tag de release nem afirmação de produção sem evidência; nenhum segredo (chaves, PSKs, host/porta de produção) em documentos ou issues; nunca resetar/limpar mudanças de outro trabalhador; revisão por um assistente não é revisão externa independente.

## Sobre este repositório

A pasta original `Desktop\Codex\Akashic Voice` **não foi alterada**. Este repositório contém o histórico completo (ProxyChat → Akashic Voice), um snapshot da árvore não commitada, o código-fonte do RC7, a documentação e o arquivo de evidências (handoffs e recibos JSON, sem JARs, com host/porta de produção redigidos). Os candidatos grandes (`audit/`, `dist/`) continuam apenas na pasta original; veja [`FRAGMENTATION_MAP.md`](FRAGMENTATION_MAP.md).
