package filesys;

import exception.CaminhoJaExistenteException;
import exception.CaminhoNaoEncontradoException;
import exception.OperacaoInvalidaException;
import exception.PermissaoException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Classe {@code FileSystemImpl} implementa a interface {@link IFileSystem} e fornece a lógica para um sistema de arquivos
 * hierárquico com suporte a permissões de usuários, manipulação de arquivos e diretórios.
 * 
 * <p>Essa classe gerencia um conjunto de usuários, um diretório raiz e oferece métodos para operações como criação,
 * remoção, leitura, escrita, cópia e movimentação de arquivos e diretórios. Além disso, permite a configuração de
 * permissões específicas para usuários em diferentes caminhos do sistema de arquivos.
 * 
 * <p>O sistema de arquivos é inicializado com um diretório raiz ("/") e um usuário padrão ("root") com permissões
 * completas. Usuários adicionais podem ser adicionados e gerenciados dinamicamente.
 * 
 * <p>Principais funcionalidades:
 * <ul>
 *   <li>Gerenciamento de usuários e permissões.</li>
 *   <li>Criação e remoção de arquivos e diretórios.</li>
 *   <li>Leitura e escrita em arquivos.</li>
 *   <li>Movimentação e cópia de arquivos e diretórios.</li>
 *   <li>Listagem de conteúdo de diretórios com suporte a recursividade.</li>
 * </ul>
 * 
 * <p>Exceções específicas são lançadas para tratar erros como permissões insuficientes, caminhos inexistentes,
 * ou tentativas de operações inválidas.
 * 
 * <p>Essa classe é imutável no sentido de que não permite a modificação direta de seus atributos internos,
 * mas fornece métodos para manipular o estado do sistema de arquivos de forma controlada.
 */
public final class FileSystemImpl implements IFileSystem {
    private static final String ROOT_USER = "root";
    private Set<Usuario> users = new HashSet<>();
    private Diretorio root;

    public FileSystemImpl() {
        users.add(new Usuario(ROOT_USER, "rwx", "/"));
        root = new Diretorio("/", "rwx", ROOT_USER);
    }

    @Override
    public void addUser(Usuario user) {
        if (users.stream().anyMatch(u -> u.getNome().equals(user.getNome()))) {
            throw new IllegalArgumentException("Usuário com o mesmo nome já existe: " + user.getNome());
        }
        users.add(user);
        try {
            navegar(user.getDir()).setPermissaoUsuario(user.getNome(), user.getPermissao());
        } catch (CaminhoNaoEncontradoException e) {
            try {
                mkdir(user.getDir(), ROOT_USER);
                navegar(user.getDir()).setPermissaoUsuario(user.getNome(), user.getPermissao());
            } catch (CaminhoJaExistenteException | PermissaoException | CaminhoNaoEncontradoException | OperacaoInvalidaException ex) {
                throw new RuntimeException("Erro ao criar diretório para o usuário: " + user.getNome(), ex);
            }
        }
    }

    @Override
    public void removeUser(String username) {
        Usuario user = users.stream()
                .filter(u -> u.getNome().equals(username))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + username));
        if (user.getNome().equals(ROOT_USER))
            throw new IllegalArgumentException("Não é possível remover o usuário root.");
        users.remove(user);
    }

    private Diretorio navegar(String caminho) throws CaminhoNaoEncontradoException {
        if (caminho.equals("/") || caminho.isEmpty()) {
            return root;
        }

        Diretorio atual = root;
        StringTokenizer tokenizer = new StringTokenizer(caminho, "/");

        while (tokenizer.hasMoreTokens()) {
            String parte = tokenizer.nextToken();
            if (!atual.getFilhos().containsKey(parte)) {
                throw new CaminhoNaoEncontradoException("Caminho não encontrado: " + caminho);
            }
            atual = atual.getFilhos().get(parte);
        }

        return atual;
    }

    @Override
    public void mkdir(String caminho, String usuario)
            throws CaminhoJaExistenteException, PermissaoException, OperacaoInvalidaException {
        if (caminho.equals("/")) return;
    
        StringTokenizer tokenizer = new StringTokenizer(caminho, "/");
        Diretorio atual = root;
        StringBuilder caminhoAtual = new StringBuilder();
    
        while (tokenizer.hasMoreTokens()) {
            String parte = tokenizer.nextToken();
            caminhoAtual.append("/").append(parte);
    
            Map<String, Diretorio> filhos = atual.getFilhos();
    
            if (!filhos.containsKey(parte)) {
                if (!atual.temPermissao(usuario, 'w')) {
                    throw new PermissaoException("Sem permissão para criar em: " + caminhoAtual);
                }
    
                Usuario user = users.stream()
                        .filter(u -> u.getNome().equals(usuario))
                        .findFirst()
                        .orElseThrow(() -> new PermissaoException("Usuário não encontrado: " + usuario));
    
                Diretorio novo = new Diretorio(parte, atual.getPermissoes(), user.getNome());
                atual.adicionarFilho(novo);
                atual = novo;
            } else {
                if (filhos.get(parte).isArquivo()) {
                    throw new OperacaoInvalidaException("Não é possível criar um diretório dentro de um arquivo.");
                }
                atual = filhos.get(parte);
            }
        }
    }    

    @Override
    public void chmod(String caminho, String usuario, String usuarioAlvo, String permissao)
            throws CaminhoNaoEncontradoException, PermissaoException {
        Diretorio dir = navegar(caminho);

        if (!dir.temPermissao(usuario, 'w')) {
            throw new PermissaoException("Usuário sem permissão para alterar permissões em: " + caminho);
        }

        dir.setPermissaoUsuario(usuarioAlvo, permissao);
    }

    @Override
    public void rm(String caminho, String usuario, boolean recursivo)
            throws CaminhoNaoEncontradoException, PermissaoException {
        if (caminho.equals("/")) {
            throw new PermissaoException("Não é possível remover o diretório raiz.");
        }

        Diretorio pai = navegar(caminho.substring(0, caminho.lastIndexOf('/')));
        String nomeAlvo = caminho.substring(caminho.lastIndexOf('/') + 1);

        Diretorio alvo = pai.getFilhos().get(nomeAlvo);
        if (alvo == null) throw new CaminhoNaoEncontradoException("Caminho não encontrado: " + caminho);

        if (!alvo.temPermissao(usuario, 'w'))
            throw new PermissaoException("Sem permissão para remover: " + caminho);

        if (!alvo.isArquivo()) {
            if (!recursivo && !alvo.getFilhos().isEmpty()) {
                throw new PermissaoException("Diretório não está vazio. Use recursivo=true.");
            }

            if (recursivo) {
                removerRecursivo(alvo, usuario);
            }
        }

        pai.removerFilho(nomeAlvo);
    }

    private void removerRecursivo(Diretorio dir, String usuario) throws PermissaoException {
        for (Diretorio filho : dir.getFilhos().values()) {
            if (!filho.temPermissao(usuario, 'w')) {
                throw new PermissaoException("Sem permissão para remover: " + filho.getNome());
            }

            if (!filho.isArquivo()) {
                removerRecursivo(filho, usuario);
            }
        }
        dir.getFilhos().clear();
    }

    @Override
    public void touch(String caminho, String usuario)
            throws CaminhoJaExistenteException, PermissaoException, CaminhoNaoEncontradoException, OperacaoInvalidaException {
        if (caminho.equals("/") || caminho.endsWith("/")) {
            throw new OperacaoInvalidaException("Nome de arquivo inválido.");
        }

        int lastSlash = caminho.lastIndexOf('/');
        String caminhoPai = (lastSlash <= 0) ? "/" : caminho.substring(0, lastSlash);
        String nomeArquivo = caminho.substring(lastSlash + 1);

        // Cria os diretórios intermediários (como mkdir -p)
        mkdir(caminhoPai, usuario);

        Diretorio parent = navegar(caminhoPai);

        if (!parent.temPermissao(usuario, 'w')) {
            throw new PermissaoException("Sem permissão para criar arquivo em: " + caminhoPai);
        }

        if (parent.isArquivo()) {
            throw new OperacaoInvalidaException("Não é possível criar um arquivo dentro de um arquivo.");
        }

        if (parent.getFilhos().containsKey(nomeArquivo)) {
            throw new CaminhoJaExistenteException("Arquivo já existe: " + nomeArquivo);
        }

        Usuario user = users.stream()
                .filter(u -> u.getNome().equals(usuario))
                .findFirst()
                .orElseThrow(() -> new PermissaoException("Usuário não encontrado: " + usuario));

        parent.adicionarFilho(new Arquivo(nomeArquivo, user.getPermissao(), user.getNome()));
    }

    @Override
    public void write(String caminho, String usuario, boolean anexar, byte[] buffer)
            throws CaminhoNaoEncontradoException, PermissaoException, OperacaoInvalidaException {
        Diretorio dir = navegar(caminho);

        if (!dir.isArquivo())
            throw new OperacaoInvalidaException("Não é possível escrever em um diretório.");
        if (!dir.temPermissao(usuario, 'w')) throw new PermissaoException("Sem permissão de escrita.");

        Arquivo arquivo = (Arquivo) dir;

        if (!anexar) arquivo.clearBlocos();

        int offset = 0;
        while (offset < buffer.length) {
            int length = Math.min(buffer.length - offset, arquivo.getTamMaxBloco()); // supondo um tamanho fixo
            byte[] dados = new byte[length];
            System.arraycopy(buffer, offset, dados, 0, length);
        
            Arquivo.Bloco bloco = new Arquivo.Bloco(dados); // criar com dados já definidos
            arquivo.addBloco(bloco);
            arquivo.incrementTamnho(length); // incrementa o tamanho do arquivo
            offset += length;
        }        
    }

    public void read(String caminho, String usuario, byte[] buffer, Offset offset)
            throws CaminhoNaoEncontradoException, PermissaoException, OperacaoInvalidaException {
        Diretorio dir = navegar(caminho);

        if (!dir.isArquivo()) throw new OperacaoInvalidaException("Não é possível ler de um diretório.");
        if (!dir.temPermissao(usuario, 'r')) throw new PermissaoException("Sem permissão de leitura.");

        Arquivo arquivo = (Arquivo) dir;
        long fileSize = arquivo.getTamanho();
        offset.setMax((int) fileSize); // garante limite do offset

        if (offset.getValue() >= fileSize) return;

        int arquivoOffset = offset.getValue();
        int bufferOffset = 0;

        for (Arquivo.Bloco bloco : arquivo.getBlocos()) {
            if (arquivoOffset >= bloco.dados.length) {
                arquivoOffset -= bloco.dados.length;
                continue;
            }

            int bytesDisponiveis = bloco.dados.length - arquivoOffset;
            int bytesParaLer = Math.min(buffer.length - bufferOffset, bytesDisponiveis);
            System.arraycopy(bloco.dados, arquivoOffset, buffer, bufferOffset, bytesParaLer);

            bufferOffset += bytesParaLer;
            arquivoOffset = 0;

            if (bufferOffset >= buffer.length) break;
        }

        offset.add(bufferOffset); // avança o offset
    }

    @Override
    public void mv(String caminhoAntigo, String caminhoNovo, String usuario)
            throws CaminhoNaoEncontradoException, PermissaoException {
        if (caminhoAntigo.equals("/") || caminhoNovo.equals("/")) {
            throw new PermissaoException("Não é possível mover o diretório raiz.");
        }

        Diretorio paiAntigo = navegar(caminhoAntigo.substring(0, caminhoAntigo.lastIndexOf('/')));
        String nomeAntigo = caminhoAntigo.substring(caminhoAntigo.lastIndexOf('/') + 1);
        Diretorio alvo = paiAntigo.getFilhos().get(nomeAntigo);

        if (alvo == null) throw new CaminhoNaoEncontradoException("Origem não encontrada.");
        if (!alvo.temPermissao(usuario, 'w'))
            throw new PermissaoException("Sem permissão de escrita no item de origem.");

        Diretorio paiNovo = navegar(caminhoNovo.substring(0, caminhoNovo.lastIndexOf('/')));
        String nomeNovo = caminhoNovo.substring(caminhoNovo.lastIndexOf('/') + 1);

        if (paiNovo.getFilhos().containsKey(nomeNovo)) {
            throw new PermissaoException("Já existe um item no destino com esse nome.");
        }

        paiAntigo.removerFilho(nomeAntigo);
        alvo.setNome(nomeNovo);
        paiNovo.adicionarFilho(alvo);
    }

    @Override
    public void ls(String caminho, String usuario, boolean recursivo)
            throws CaminhoNaoEncontradoException, PermissaoException {
        Diretorio dir = navegar(caminho);

        if (!dir.temPermissao(usuario, 'r')) {
            throw new PermissaoException("Sem permissão de leitura para listar o diretório.");
        }

        String output = listar(dir, caminho, recursivo, usuario);
        System.out.print(output);
    }

    private String listar(Diretorio dir, String caminho, boolean recursivo, String usuario) {
        StringBuilder output = new StringBuilder();
        output.append(caminho).append(":\n");
    
        // Listar arquivos e diretórios filhos
        for (Map.Entry<String, Diretorio> entry : dir.getFilhos().entrySet()) {
            Diretorio filho = entry.getValue();
            output.append("  ").append(filho.toString()).append("\n");
        }
    
        // Recursão: somente depois de listar os filhos diretos
        if (recursivo) {
            for (Map.Entry<String, Diretorio> entry : dir.getFilhos().entrySet()) {
                Diretorio filho = entry.getValue();
                if (!filho.isArquivo()) {
                    String novoCaminho = caminho.equals("/") ? "/" + filho.getNome() : caminho + "/" + filho.getNome();
                    output.append(listar(filho, novoCaminho, true, usuario));
                }
            }
        }
    
        return output.toString();
    }      

    @Override
    public void cp(String caminhoOrigem, String caminhoDestino, String usuario, boolean recursivo)
            throws CaminhoNaoEncontradoException, PermissaoException {
        Diretorio origem = navegar(caminhoOrigem);
        Diretorio destino = navegar(caminhoDestino);

        if (!origem.temPermissao(usuario, 'r')) {
            throw new PermissaoException("Sem permissão de leitura na origem.");
        }
        if (!destino.temPermissao(usuario, 'w')) {
            throw new PermissaoException("Sem permissão de escrita no destino.");
        }

        if (origem.isArquivo()) {
            copiarArquivo((Arquivo) origem, destino);
        } else {
            if (!recursivo) {
                throw new PermissaoException("Diretório precisa de recursividade para cópia.");
            }
            copiarDiretorio((Diretorio) origem, destino, usuario);
        }
    }

    private void copiarArquivo(Arquivo origem, Diretorio destino) {
        Arquivo copia = new Arquivo(origem.getNome(), origem.getPermissoes(), origem.getDono());
        for (Arquivo.Bloco bloco : origem.getBlocos()) {
            copia.addBloco(bloco);
            copia.incrementTamnho(bloco.dados.length);
        }
        destino.adicionarFilho(copia);
    }

    private void copiarDiretorio(Diretorio origem, Diretorio destino, String usuario) throws PermissaoException {
        Diretorio copia = new Diretorio(origem.getNome(), origem.getPermissoes(), origem.getDono());
        destino.adicionarFilho(copia);
        for (Diretorio filho : origem.getFilhos().values()) {
            if (filho.isArquivo()) {
                copiarArquivo((Arquivo) filho, copia);
            } else {
                copiarDiretorio(filho, copia, usuario);
            }
        }
    }
}
