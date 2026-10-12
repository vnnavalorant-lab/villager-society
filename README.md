# Villager Society

Mod Fabric para Minecraft Java 1.21.11 (Java 21, Mojang mappings) que dá aos villagers
memória, reputação por vila, regras locais, descontos, cartas de recomendação e rebeliões.

## Status

**Etapa 1 de 7 — base (não compilada ainda).**

Implementado: build, `fabric.mod.json`, configuração validada (`config/villager_society.json`),
persistência no mundo (vilas, reputação, recomendações) e testes da config.

Pendente: vilas/personalidades, reputação, eventos e testemunhas, economia, carta de recomendação,
diálogos, rebelião/IA, testes de jogo.

## Compilar

Pelo GitHub: a aba **Actions** roda `gradle build` a cada push e publica o `.jar` como artefato.

Localmente: `gradle build` (Gradle 9.x compatível com o Loom) → `build/libs/`.

## Antes do primeiro build, confira

- `gradle.properties`: versões de Loom, Fabric Loader e Fabric API (valores são palpites; veja fabricmc.net/develop).
- `persistence/SocietyState.java`: assinatura de `SavedDataType` e uso de `Identifier` na 1.21.11.
