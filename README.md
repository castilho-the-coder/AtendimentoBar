# Simulação de Atendimento no Bar

Este projeto simula o atendimento em um bar, onde clientes fazem pedidos e garçons os atendem. A simulação utiliza programação concorrente com threads em Java.

## Estrutura do Projeto

- **AtendimentoBar.java**: Classe principal que orquestra a simulação do atendimento no bar.
- **Pedido**: Representa um pedido feito por um cliente.
- **Cliente**: Thread que representa um cliente fazendo pedidos.
- **Garcom**: Thread que representa um garçom atendendo os pedidos.

## Como Executar

1. Certifique-se de ter o JDK instalado em sua máquina.
2. Clone este repositório ou baixe os arquivos.
3. Navegue até o diretório do projeto no terminal.
4. Compile o código:
   ```bash
   javac AtendimentoBar.java
      ```

5. Execute a simulação:
    - Com valores padrão (10 clientes, 3 garçons, capacidade 4, 15 rodadas):
       ```bash
       java AtendimentoBar
       ```
    - Ou especifique os parâmetros na linha de comando:
       ```bash
       java AtendimentoBar <totalClientes> <numGarcons> <capacidadeGarcons> <numRodadas>
       ```
       Exemplo para 12 clientes, 4 garçons, capacidade 5, 20 rodadas:
       ```bash
       java AtendimentoBar 12 4 5 20
       ```

    Se fornecer argumentos inválidos, o programa mostrará uma mensagem de uso.
