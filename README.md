# Villager Society

Mod Fabric para Minecraft Java 1.21.11 (Java 21, Mojang mappings) que dá aos villagers
memória, reputação por vila, regras locais, descontos, cartas de recomendação e rebeliões.

## Status

**Etapas 1 a 4 de 7 — base, vilas, reputação, eventos/testemunhas, descontos e carta de recomendação.**

Implementado:
- Config validada (`config/villager_society.json`) e persistência no mundo.
- Vilas por POIs (camas, estações de trabalho, sino), personalidades persistentes, representante (líder) por vila.
- Reputação -100..+100 por jogador e vila: níveis, anti-duplicação, teto diário de ganhos, recuperação lazy, crimes graves.
- Eventos: quebrar plantação imatura, quebrar cama/estação/sino, roubar de contêiner comunitário (barril etc.),
  atacar/matar villager, matar monstros (defender villager), replantar, doar comida, concluir trocas.
- Testemunhas (distância + linha de visão), peso por quantidade de testemunhas, fofoca limitada com atraso e saltos.
- Descontos por reputação (via mixin único em `Villager`), calculados sobre o preço base, sem acumular.
- Carta de Recomendação: registro persistente no servidor, validação de dono, uso único, revogação de cartas antigas.

Pendente: diálogos contextuais (etapa 5), rebelião e IA de combate (etapa 6), testes de jogo e relatório final (etapa 7).

## Como usar no jogo

- `/villagersociety info`: vila, personalidade, sua reputação e distância do representante.
- Doar comida: **shift + botão direito** num villager segurando pão, cenoura, batata, beterraba ou trigo.
- Pedir a carta: **shift + botão direito** no representante (sem comida na mão). Exige reputação 80, 10 trocas e 3 boas ações.
- Apresentar a carta: **botão direito** no representante de OUTRA vila segurando a carta. Dá até +20 de confiança, limitado a
  um pouco abaixo de "Amigo da vila" (a carta não dá amizade automática).
- Mercadores recusam trocas se a vila estiver mobilizada/em rebelião ou se sua reputação estiver abaixo do limiar de rebelião.

Para testar sem esperar dias: `"debugCommands": true` na config e use
`/villagersociety debug maxout` (reputação 90 + requisitos da carta), `debug set <n>`, ou ações como `debug break_crop`.
Com `debugCommands` ligado, jogadores em criativo também geram reputação.

Limitações atuais: só Overworld; contêiner comunitário = bloco de POI que guarda itens (ex.: barril); ganhos de reputação são
limitados por dia e esse controle se reinicia ao reiniciar o servidor; fofoca entre vilas ainda não existe; presentes de
benfeitores/heróis ainda não existem.

## Compilar

Pelo GitHub: a aba **Actions** roda `gradle build` a cada push e publica o `.jar` como artefato.

Localmente: `gradle build` (Gradle 9.x compatível com o Loom) → `build/libs/`.

## Antes do primeiro build, confira

- `gradle.properties`: versões de Loom, Fabric Loader e Fabric API (valores são palpites; veja fabricmc.net/develop).
- `persistence/SocietyState.java`: o build já confirmou Loom 1.14.10 e que o id do `SavedDataType` é `String`.
- `mixin/VillagerMixin.java`: pacote da classe `Villager` na 1.21.11 e nomes `updateSpecialPrices` / `notifyTrade` (erro de mixin só aparece ao iniciar o jogo, não no build).
- `events/*`, `economy/TradeHooks.java`, `recommendation/*`: assinaturas dos callbacks do Fabric, `MerchantOffer`, `CompoundTag#getStringOr`, `PoiTypes.forState`.
