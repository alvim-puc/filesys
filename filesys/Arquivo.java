package filesys;

import java.util.ArrayList;
import java.util.List;

public class Arquivo extends Diretorio {
    private static final int TAMANHO_BLOCO = 4096; // 4KB por bloco (ajuste conforme necessário)
    private List<Bloco> blocos;
    private long tamanho;

    public Arquivo(String nome, String permissoes, String dono) {
        super(nome, permissoes, dono);
        this.blocos = new ArrayList<>();
        this.tamanho = 0;
        this.filhos = null; // Arquivos não podem ter filhos
    }

    // Classe interna para representar blocos
    public static class Bloco {
        byte[] dados;
        
        Bloco() {
            this.dados = new byte[TAMANHO_BLOCO];
        }
    }

    public void addBloco(Bloco bloco) {
        this.blocos.add(bloco);
    }

    public void clearBlocos() {
        this.blocos.clear();
        this.tamanho = 0;
    }

    public List<Bloco> getBlocos() {
        return blocos;
    }

    public void setBlocos(List<Bloco> blocos) {
        this.blocos = blocos;
    }

    public void incrementTamnho(long tamanho) {
        this.tamanho += tamanho;
    }

    public long getTamanho() {
        return tamanho;
    }

    @Override
    public boolean isArquivo() {
        return true;
    }

    @Override
    public void adicionarFilho(Diretorio filho) {
        throw new UnsupportedOperationException("Arquivos não podem conter filhos.");
    }

    @Override
    public void removerFilho(String nome) {
        throw new UnsupportedOperationException("Arquivos não podem conter filhos.");
    }
}