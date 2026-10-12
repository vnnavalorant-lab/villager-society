# Villager Society

Mod Fabric para Minecraft Java 1.21.11 (Java 21, Mojang mappings) que dá aos villagers
memória, reputação por vila, regras locais, descontos, cartas de recomendação e rebeliões.

## Status

**Etapa 2 de 7 — vilas e reputação.**

Implementado: build, configuração validada (`config/villager_society.json`), persistência no mundo,
detecção e registro de vilas por POIs (camas, estações de trabalho, sino), personalidades persistentes,
reputação -100..+100 por jogador e vila (níveis, anti-duplicação, recuperação lazy, crimes graves),
comando `/villagersociety info`.

Para testar a reputação no jogo, ponha `"debugCommands": true` na config e use
`/villagersociety debug break_crop` (ou `trade`, `donate_item`, `kill_villager`...), depois `/villagersociety info`.

Pendente: eventos e testemunhas, economia, carta de recomendação, diálogos, rebelião/IA.
Limitações atuais: só Overworld; líder da vila ainda não é escolhido; as ações ainda não são disparadas
pelo jogo (só pelo comando de debug).

## Compilar

Pelo GitHub: a aba **Actions** roda `gradle build` a cada push e publica o `.jar` como artefato.

Localmente: `gradle build` (Gradle 9.x compatível com o Loom) → `build/libs/`.

## Antes do primeiro build, confira

- `gradle.properties`: versões de Loom, Fabric Loader e Fabric API (valores são palpites; veja fabricmc.net/develop).
- `persistence/SocietyState.java`: o build já confirmou Loom 1.14.10 e que o id do `SavedDataType` é `String`; resta validar os demais pontos de API.
