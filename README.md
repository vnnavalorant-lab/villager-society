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

- **Painel de reputação:** barra translúcida acima da hotbar, só dentro de uma vila: `Reputação: +45 (Amigo da vila)`, com
  `| PROCURADO`, `| Líder` e a fase da rebelião quando houver. Verde = a vila gosta de você, vermelho = odeia.
  Config: `hudEnabled` e `hudOffsetY`.
- **Recuperar reputação com esmeraldas:** segure uma esmeralda e aperte **Ctrl + botão direito** num villager (Ctrl é a tecla de correr;
  funciona parado). Cada clique entrega 1 esmeralda e dá `repDonateItem` pontos (padrão 3), mesmo para quem é inimigo, sem teto diário
  e sem o corte pela metade dos crimes graves. Se a reputação subir acima de -50, uma rebelião em andamento termina.
- **Comida não dá mais reputação.**
- **Feno:** quebrar blocos de feno só é crime quando **metade** dos blocos da vila já foi quebrada (`hayFractionForCrime`). A contagem
  é feita na primeira quebra e fica salva. Antes disso, nada acontece; depois, com testemunha, a vila vira inimiga.
- **Virar líder da vila:** com reputação ≥ 85 (`leaderMinReputation`), segure um **sino** e dê **Shift + botão direito** num villager da vila.
  O sino é consumido e você recebe o **Cajado do Líder** (use o sino de novo para ganhar outro). Quem cai abaixo de 40 de reputação perde a liderança.
- **Cajado do Líder** (só o líder da vila usa nos moradores dela):
  - **Shift + botão direito** (no ar ou num bloco): troca o modo (Mineração, Lenhador, Plantação, Imigração). O nome aparece na barra de ações.
  - **Botão direito num bloco:** define a **marca** da área de trabalho (raio `workAreaRadius`, padrão 8). Sem marca, a área é em volta do morador.
  - **Botão direito num morador:** nos 3 modos de trabalho, designa o trabalho (de novo cancela). No modo Imigração, seleciona o morador.
  - **Imigração:** com um morador selecionado, clique num bloco de **outra vila** (precisa de cama livre, de 2 blocos de ar no ponto e de
    você não ser procurado lá). O morador é teletransportado, esquece a casa e o trabalho antigos e se acomoda na nova vila.
  - **Trabalhos:** só de dia, até `maxWorkersPerLeader` (8). Mineração quebra pedra com uma face exposta; Lenhador derruba árvores naturais
    inteiras (casas de madeira são ignoradas); Plantação colhe o que está maduro e replanta. Os itens ficam marcados no inventário do morador
    e, com 4 pilhas, ele os leva ao **baú ou barril com espaço mais próximo** (até `workerChestRadius`). A comida e as sementes do próprio
    morador não são tocadas. Ferramentas são virtuais (nada é gasto).
  - Escolha áreas **longe das construções**: a mineração não distingue pedra de fundação.
- **Pedir a carta:** **Shift + botão direito** no representante da vila (o villager sorteado, não é o líder jogador).
  Exige reputação 80, 10 trocas e 3 boas ações.
- **Apresentar a carta:** **botão direito** no representante de OUTRA vila segurando a carta (+20 de confiança, nunca chega a "Amigo").
- **Rebelião:** reputação baixa faz a vila passar por suspeita, advertência, mobilização, rebelião, perseguição e recuperação.
  Matar um villager (com testemunha) torna você inimigo na hora. Um inimigo conhecido que volta à vila é mobilizado contra de imediato.
- **Mensagens:** os villagers não falam. O chat só mostra o fluxo da carta; o cajado usa a barra de ações. Textos iguais não se repetem em 5 s.

### Testar rápido

Ponha `"debugCommands": true` em `config/villager_society.json` (sem isso nenhum comando existe; com isso, criativo também gera reputação):

- `/villagersociety debug info`: vila, personalidade, reputação, fase e distância do representante.
- `/villagersociety debug maxout`: reputação 90 + requisitos da carta.
- `/villagersociety debug enemy`: reputação -100 e bloqueio de recuperação (dispara a rebelião sozinha em segundos).
- `/villagersociety debug set <n>`: define a reputação (-100 a 100).
- `/villagersociety debug <acao>`: aplica uma ação (`break_crop`, `trade`, `donate_item` = 1 esmeralda, `kill_villager`, `break_hay_bale`...).

## Configuração (`config/villager_society.json`)

Criada no primeiro início, com todos os valores validados e limitados a faixas seguras. Principais grupos:
reputação por ação, distâncias de testemunha/vila, fofoca (raio, saltos, atraso), rebelião (mínimo/máximo de participantes,
intervalo da IA, duração e distância da perseguição, dano/alcance/intervalo do golpe, atacantes simultâneos, armas visíveis,
limiares de cada fase), descontos (máximo 50%), carta (requisitos e bônus), crimes que causam hostilidade imediata (incluindo a fração de feno),
líder e trabalhadores (reputação para liderar, limite de trabalhadores, raio da área, intervalo das ações, raio de busca do baú).

## Estrutura

`config/` configuração · `persistence/` dados salvos no mundo · `village/` vilas, personalidades, líder ·
`reputation/` regras de reputação · `network/` envio da reputação ao cliente · `events/` eventos, testemunhas, fofoca, interações · `feedback/` avisos só do fluxo da carta · `economy/` descontos e recusa de troca ·
`recommendation/` carta · `rebellion/` fases, papéis, gerenciador · `ai/` táticas e armas · `command/` comandos ·
`leader/` líder da vila, cajado e imigração · `workers/` trabalhos dos moradores · `mixin/` único mixin (preços e troca concluída) · `src/client/` painel (HUD) do cliente.

## Testes automatizados

`gradle test` executa os testes unitários da lógica pura (config, reputação, agrupamento de vilas, testemunhas/fofoca,
descontos, carta, máquina de estados, papéis, táticas). Não há testes automatizados de jogo: veja a lista manual em `RELATORIO.md`.

## Se o jogo travar ao iniciar

O mixin precisa que `Villager#updateSpecialPrices(Player)` e `Villager#rewardTradeXp(MerchantOffer)` existam nos mappings.
Um nome errado causa `InvalidInjectionException` na inicialização, citando o método. Envie o trecho do log.
