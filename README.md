# Villager Society

Mod Fabric para Minecraft Java 1.21.11 (Java 21, Mojang mappings) que transforma os villagers vanilla em uma
sociedade com memória, reputação por vila, personalidades, descontos, cartas de recomendação e rebeliões
organizadas contra jogadores hostis. Sem IA externa e **sem falas**: os villagers gostam ou odeiam você pelos seus feitos,
mostrando isso em preços, recusa de trocas, sons e ações; nenhuma entidade vanilla é substituída.

> **Estado honesto:** todas as 7 etapas estão escritas. As etapas 1 a 4 já compilaram e o mod chegou a carregar no jogo.
> A etapa 6 (rebelião e combate) foi escrita depois do último build confirmado e **ainda não foi
> compilada nem testada no jogo**. Veja `RELATORIO.md` para o que está pronto, o que é heurística e o que falta.

## Instalação e compilação

Requisitos: Java 21, Minecraft 1.21.11, Fabric Loader e Fabric API para 1.21.11.

- **GitHub:** a aba **Actions** roda `gradle build` a cada push e publica o `.jar` como artefato.
- **Local:** `gradle build` (Gradle 9.x compatível com o Loom) e o `.jar` sai em `build/libs/`.
- **Instalar:** copie o `.jar` para a pasta `mods/` junto com a Fabric API.

Versões em `gradle.properties` (Loom, Loader, Fabric API): se o build reclamar, ajuste para os números de fabricmc.net/develop.

## Como usar no jogo

- **Painel de reputação:** uma barra translúcida com texto, acima da hotbar, aparece sozinha enquanto você está dentro de uma vila:
  `Reputação: +45 (Amigo da vila)`, com `| PROCURADO` e a fase da rebelião quando houver. Verde = a vila gosta de você, vermelho = odeia.
  Precisa do mod instalado no cliente (e no servidor, se for multiplayer). Config: `hudEnabled` e `hudOffsetY` (altura da barra).
- **Doar comida:** shift + botão direito num villager segurando pão, cenoura, batata, beterraba ou trigo.
- **Pedir a carta:** shift + botão direito no representante (sem comida na mão). Exige reputação 80, 10 trocas e 3 boas ações.
- **Apresentar a carta:** botão direito no representante de OUTRA vila segurando a carta (até +20 de confiança, nunca chega a "Amigo").
- **Rebelião:** reputação baixa faz a vila passar por suspeita, advertência, mobilização, rebelião, perseguição e recuperação.
  Matar um villager ou quebrar um bloco de feno (com testemunha) torna você inimigo na hora.

### Testar rápido

Ponha `"debugCommands": true` em `config/villager_society.json` (sem isso nenhum comando existe; com isso, criativo também gera reputação):

- `/villagersociety debug info`: vila, personalidade, reputação, fase e distância do representante.
- `/villagersociety debug maxout`: reputação 90 + requisitos da carta.
- `/villagersociety debug enemy`: reputação -100 e bloqueio de recuperação (dispara a rebelião sozinha em segundos).
- `/villagersociety debug set <n>`: define a reputação (-100 a 100).
- `/villagersociety debug <acao>`: aplica uma ação (`break_crop`, `trade`, `donate_item`, `kill_villager`, `break_hay_bale`...).

## Configuração (`config/villager_society.json`)

Criada no primeiro início, com todos os valores validados e limitados a faixas seguras. Principais grupos:
reputação por ação, distâncias de testemunha/vila, fofoca (raio, saltos, atraso), rebelião (mínimo/máximo de participantes,
intervalo da IA, duração e distância da perseguição, dano/alcance/intervalo do golpe, atacantes simultâneos, armas visíveis,
limiares de cada fase), descontos (máximo 50%), carta (requisitos e bônus), crimes que causam hostilidade imediata.

## Estrutura

`config/` configuração · `persistence/` dados salvos no mundo · `village/` vilas, personalidades, líder ·
`reputation/` regras de reputação · `network/` envio da reputação ao cliente · `events/` eventos, testemunhas, fofoca, interações · `feedback/` avisos só do fluxo da carta · `economy/` descontos e recusa de troca ·
`recommendation/` carta · `rebellion/` fases, papéis, gerenciador · `ai/` táticas e armas · `command/` comandos ·
`mixin/` único mixin (preços e troca concluída) · `src/client/` painel (HUD) do cliente.

## Testes automatizados

`gradle test` executa os testes unitários da lógica pura (config, reputação, agrupamento de vilas, testemunhas/fofoca,
descontos, carta, máquina de estados, papéis, táticas). Não há testes automatizados de jogo: veja a lista manual em `RELATORIO.md`.

## Se o jogo travar ao iniciar

O mixin precisa que `Villager#updateSpecialPrices(Player)` e `Villager#rewardTradeXp(MerchantOffer)` existam nos mappings.
Um nome errado causa `InvalidInjectionException` na inicialização, citando o método. Envie o trecho do log.
