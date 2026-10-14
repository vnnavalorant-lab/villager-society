# Relatório de recursos e limitações

Legenda: **Escrito** = código existe; **Compilado** = passou no build do GitHub Actions; **Testado em jogo** = verificado jogando.
Nada abaixo foi testado em jogo. Etapas 1 a 4 compilaram; a etapa 6 (rebelião) e as mudanças recentes ainda não foram compiladas.

## Recursos

| Área | O que existe | Compilado |
|---|---|---|
| Config | Arquivo JSON, todos os campos limitados a faixas seguras, limiares das fases sempre ordenados | sim |
| Persistência | Vilas, reputação, histórico, personalidade, líder, cartas emitidas/usadas em `SavedData` do Overworld; sem salvar por tick | sim |
| Vilas | Detecção por POIs (cama, estação, sino), agrupamento encadeado, associação ao centro registrado, cache por chunk | sim |
| Personalidades | 5 tipos sorteados de forma determinística e persistida; só mudam multiplicadores | sim |
| Reputação | -100..+100 por jogador e vila, 7 níveis, acidental x deliberado, anti-duplicação, teto diário, recuperação lazy, crimes graves travam recuperação | sim |
| Eventos | Plantação imatura, cama/estação/sino, barril/contêiner de POI, ataque/morte de villager, monstros, replantio, doação, troca | sim |
| Testemunhas e fofoca | Distância + linha de visão, peso por nº de testemunhas, memória direta x relato, fofoca com atraso, saltos e limites | sim |
| Hostilidade imediata | Matar villager ou quebrar feno (com testemunha) leva a -80 e bloqueia recuperação | não |
| Descontos | Calculados sobre o preço base, somados ao ajuste vanilla (não acumulam), teto configurável | sim (mixin: ver abaixo) |
| Recusa de troca | Só contra o jogador-alvo da rebelião ou com reputação abaixo do limiar; ofertas intactas | não |
| Carta | Item próprio; registro persistente; dono, uso único, revogação da anterior, bônus limitado abaixo de "Amigo" | sim |
| Representante | Villager adulto escolhido de forma determinística, salvo por UUID, sem entidade nova | sim |
| Painel de reputação (HUD) | Barra translúcida com texto acima da hotbar: reputação, nível, PROCURADO e fase da rebelião, só dentro de vila; o servidor envia os dados 1x/s só quando mudam | não |
| Esmeraldas | Ctrl + botão direito com esmeralda: +3 de reputação por unidade, sem teto diário nem corte de crime grave; encerra rebelião se a reputação passar de -50 | não |
| Feno | Só é crime quando metade dos blocos da vila foi quebrada (contagem feita na 1ª quebra, salva com a vila) | não |
| Líder da vila (jogador) | Sino + Shift + botão direito com reputação ≥ 85; cajado próprio; perde a liderança abaixo de 40; tudo salvo no mundo | não |
| Imigração | Cajado no modo Imigração: morador vai (teletransporte) para outra vila com cama livre, esquece casa e trabalho | não |
| Trabalhadores | Mineração, lenhador e plantação com ferramenta virtual; itens marcados vão ao baú/barril mais próximo; só de dia, só em chunks carregados | não |
| Mensagens | Chat só no fluxo da carta; cajado usa a barra de ações; texto repetido bloqueado por 5 s; duplo processamento do clique corrigido | não |
| Reações sem falas | Villagers não falam: recusam troca, fazem o som "não"/"sim" do jogo, doação tem retorno sonoro, preços mudam com a reputação | não |
| Rebelião | Máquina de 6 fases com histerese e limites; papéis (defensor, flanqueador, protetor, alertador, fuga); sino; golpes reais com alcance, intervalo e dano configuráveis; atacantes simultâneos limitados; perseguição com limite de tempo e distância; encerramento seguro | não |
| Armas visuais | Machado/enxada na mão principal, marcada por tag, limpa ao terminar, ao morrer e ao carregar a entidade | não |

## Verificações pendentes (rodando o jogo)

1. O jogo abre sem `InvalidInjectionException` (nomes `updateSpecialPrices` e `rewardTradeXp`).
2. `Villager` ainda mantém profissões, rotinas, reprodução e trocas vanilla.
3. Villagers realmente andam até o jogador e atacam (o movimento usa o `WALK_TARGET` do cérebro; a rotina vanilla pode competir, principalmente à noite).
4. Armas aparecem e somem.
5. Descontos aparecem na tela de troca.
6. Reiniciar o mundo preserva reputação, personalidade, líder e validade das cartas.
7. Duas vilas próximas não se misturam; vários jogadores em multiplayer mantêm reputações independentes.

## Lista de testes manuais (do prompt original)

1 vila nova registrada · 2 destruir plantação · 3 infração com testemunhas · 4 provocar rebelião · 5 fugir e encerrar a perseguição ·
6 reiniciar o mundo · 7 amizade e descontos · 8 receber carta · 9 apresentar em outra vila · 10 carta com outro jogador ·
11 reutilizar carta · 12 recuperar reputação · 13 duas vilas próximas · 14 multiplayer · 15 vanilla preservado ·
16 villagers mortos/removidos/descarregados · 17 desempenho com várias vilas.
**Status de todos: não executados.**

## Limitações conhecidas

- Só Overworld; sem fofoca entre vilas; sem presentes de benfeitores/heróis; sem falas (só o fluxo da carta mostra mensagens de sistema).
- Contêiner comunitário = bloco de POI que guarda itens (ex.: barril). Baús comuns não contam.
- Crime sem testemunha só pesa 30% e não causa hostilidade imediata (vale também para a regra do feno).
- Comida não dá mais reputação (só esmeralda). A tecla "Ctrl" é a tecla de correr: se você a remapear, é ela que vale.
- Trabalhadores: a mineração não distingue pedra de fundação de casas; use a marca longe das construções. Alvos inalcançáveis são ignorados por 2 min.
  O baú só vale se tiver espaço; baú duplo conta como duas metades. Não há fila nem trabalho à noite.
- Imigração é por teletransporte e só no Overworld; o morador pode demorar a achar cama/trabalho, como qualquer villager.
- A contagem de feno usa só chunks carregados na hora da 1ª quebra e não é refeita se você repuser blocos.
- Fuga dos villagers = afastar-se do jogador; não procuram camas/abrigo.
- Teto diário de reputação, anti-duplicação, memória de testemunhas e rebeliões ativas são transitórios (reiniciam com o servidor);
  reputação e crimes graves persistem.
- Um villager à noite/dormindo pode demorar a reagir (a rotina vanilla de descanso compete com as ordens de movimento).
- O líder só é escolhido quando alguém interage com a vila (ou com `/villagersociety debug info`). Não há mais comando para localizá-lo: dê shift + botão direito nos villagers até achar o representante.
- O painel só aparece se o cliente tiver o mod; clientes sem o mod entram normalmente, mas não veem o painel.
