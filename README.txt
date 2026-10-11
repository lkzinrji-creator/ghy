REAL CRAFT - Mesa de trabalho realista (Fabric, Minecraft 26.2, Java 25)

COMO USAR
Clique direito com um item no topo coloca uma unidade no slot apontado; clique direito com a mao vazia em um ingrediente o retira. Com uma receita valida, o resultado aparece flutuando sobre a mesa. Clique direito na bancada com a mao vazia (ou diretamente no resultado) para fabricar e soltar o produto no chao; os ingredientes sao consumidos. Use Shift+clique direito em um ingrediente para retira-lo enquanto ha um resultado pronto. Com o Martelo de Pedra na mao, clique direito na bancada ou no resultado: toca o som de bigorna, consome os ingredientes e solta o produto no chao. Sem receita valida, nao fabrica. O martelo permanece na mao e nunca e inserido na grade.

RECEITA DO MARTELO DE PEDRA
Esta e uma receita shaped vanilla, disponivel no inventario do jogador (grade 2x2) e na grade 3x3. Coloque uma pedra (minecraft:stone) imediatamente acima de um graveto (minecraft:stick), na mesma coluna. O exemplo abaixo usa a segunda coluna; o padrao tambem pode ser deslocado para outra coluna e rotacionado:
    [vazio] [pedra]   [vazio]
    [vazio] [graveto] [vazio]
    [vazio] [vazio]   [vazio]
O resultado e um Martelo de Pedra (realcraft:stone_hammer). Bater no resultado flutuante solta o martelo no mundo.

DETALHES
A mesa consulta o RecipeManager vanilla no servidor. O resultado possui um alvo invisivel selecionavel, persistente e dimensionado conforme o modelo que esta sendo mostrado. O ataque e validado e executado no servidor tanto em mundo single-player quanto em servidor dedicado. O alvo e removido quando a receita ou a mesa desaparece.

VERSOES E BUILD
Minecraft 26.2, Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2 e Fabric Loom 1.17.21. Com Java 25 e Gradle 9.8.1 instalados, rode na pasta raiz:
    gradle --no-daemon clean build
O arquivo gerado fica em build/libs/realcraft-1.0.6.jar. Instale esse JAR e o Fabric API na pasta mods do perfil Fabric 26.2.
