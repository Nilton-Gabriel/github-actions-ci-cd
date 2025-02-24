package com.projeto.sprint.projetosprint.service.cooperativa;

import com.projeto.sprint.projetosprint.controller.cooperativa.CooperativaMapper;
import com.projeto.sprint.projetosprint.controller.cooperativa.dto.CooperativaAtualizarDTO;
import com.projeto.sprint.projetosprint.controller.cooperativa.dto.CooperativaCriacaoDTO;
import com.projeto.sprint.projetosprint.controller.cooperativa.dto.CooperativaResponseDTO;
import com.projeto.sprint.projetosprint.controller.endereco.EnderecoMapper;
import com.projeto.sprint.projetosprint.controller.usuario.dto.UsuarioCriacaoDTO;
import com.projeto.sprint.projetosprint.domain.entity.cooperativa.Cooperativa;
import com.projeto.sprint.projetosprint.domain.entity.email.EmailBoasVindas;
import com.projeto.sprint.projetosprint.domain.entity.email.EmailConteudo;
import com.projeto.sprint.projetosprint.domain.entity.endereco.Endereco;
import com.projeto.sprint.projetosprint.domain.entity.material.MaterialPreco;
import com.projeto.sprint.projetosprint.domain.entity.usuario.TipoUsuario;
import com.projeto.sprint.projetosprint.domain.entity.usuario.Usuario;
import com.projeto.sprint.projetosprint.domain.repository.MaterialPrecoRepository;
import com.projeto.sprint.projetosprint.exception.EntidadeNaoEncontradaException;
import com.projeto.sprint.projetosprint.domain.repository.CooperativaRepository;
import com.projeto.sprint.projetosprint.exception.ImportacaoExportacaoException;
import com.projeto.sprint.projetosprint.infra.security.jwt.GerenciadorTokenJwt;
import com.projeto.sprint.projetosprint.service.email.EmailConteudoService;
import com.projeto.sprint.projetosprint.service.endereco.EnderecoService;
import com.projeto.sprint.projetosprint.service.usuario.UsuarioService;
import com.projeto.sprint.projetosprint.util.ListaObj;
import com.projeto.sprint.projetosprint.util.OrdenacaoCnpj;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.*;


@Service
@RequiredArgsConstructor
public class CooperativaService {
    private final CooperativaRepository repository;
    private final MaterialPrecoRepository materialPrecoRepository;
    private final UsuarioService usuarioService;
    private final EnderecoService enderecoService;
    private final EmailConteudoService emailService;
    private final GerenciadorTokenJwt tokenJwt;


    public List<CooperativaResponseDTO> listarCooperativa(){
        return this.repository.findAll().stream().map(CooperativaMapper :: of).toList();
    }

    public Cooperativa buscarCoperativaId(int id){
        return this.repository.findById(id).orElseThrow(
                () -> new EntidadeNaoEncontradaException(
                        "Campo id inválido")
        );
    }

    public Cooperativa cadastrarCooperativa(CooperativaCriacaoDTO dados){

        UsuarioCriacaoDTO usuarioCriacaoDTO = new UsuarioCriacaoDTO(dados.email, dados.senha);

        Usuario usuario = usuarioService.cadastrar(usuarioCriacaoDTO);
        usuario.setTipoUsuario(TipoUsuario.COOPERATIVA);

        Cooperativa cooperativa = CooperativaMapper.of(dados);
        cooperativa.setUsuario(usuario);

        return this.repository.save(cooperativa);
    }

    public Cooperativa atualizarCooperativa(CooperativaAtualizarDTO dados, Integer id){

        Cooperativa infoCooperativa, cooperativa;
        Usuario usuario, infoUsuario;

        if (this.repository.existsById(id)) {
            infoCooperativa = this.buscarCoperativaId(id);

            cooperativa = CooperativaMapper.of(dados);
            cooperativa.setId(id);

            usuario = cooperativa.getUsuario();
            usuario.setId(infoCooperativa.getUsuario().getId());
            infoUsuario = this.usuarioService.buscarUsuarioId(usuario.getId());

            if (usuario.getSenha() == null){
                usuario.setSenha(infoUsuario.getSenha());
            }

            if (infoUsuario.getEndereco() != null){
                usuario.getEndereco().setId(
                        infoUsuario.getEndereco().getId()
                );
            }

            Endereco endereco = this.enderecoService.cadastrarEndereco(EnderecoMapper.of(usuario.getEndereco()));

            usuario.setEndereco(endereco);
            this.usuarioService.atualizarUsuario(usuario);
            cooperativa.setUsuario(usuario);

            return this.repository.save(cooperativa);
        }

        throw new EntidadeNaoEncontradaException("Campo id inválido");
    }

    public void deletarCooperativa(Integer id){
        if(this.repository.existsById(id)){
            this.repository.deleteById(id);
        }

        throw new EntidadeNaoEncontradaException("Campo id inválido");
    }

    public List<Cooperativa> listarCooperativas(){
        return this.repository.findAll();
    }

    public byte[] downloadCooperativaCsv(int id){
        List<Cooperativa> cooperativas = new ArrayList<>();

        if (id != 0) cooperativas.add(buscarCoperativaId(id));
        else cooperativas = listarCooperativas();

        ListaObj<Cooperativa> lista = new ListaObj(cooperativas.size());
        OrdenacaoCnpj.ordenarPorCnpj(cooperativas).stream()
                .forEach(c -> lista.adiciona(c));

        try{
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

            ICSVWriter csvWriter = new CSVWriterBuilder(outputStreamWriter)
                    .withSeparator(';')
                    .build();

            String[] cabecalho = {"Id", "Nome", "Email", "CNPJ"};
            csvWriter.writeNext(cabecalho);

            for (int i = 0; i < lista.getTamanho(); i++){
                Cooperativa c = lista.getElemento(i);
                //GRAVANDO OS DADOS DA COOPERATIVA NO ARQUIVO
                String[] linha = {
                        c.getId().toString(),
                        c.getNome(),
                        c.getUsuario().getEmail(),
                        c.getCnpj()
                };
                csvWriter.writeNext(linha);
            }
            csvWriter.close();
            outputStreamWriter.close();
            byte[] csvBytes = byteArrayOutputStream.toByteArray();
            return csvBytes;
        }
        catch (IOException e) {
            throw new ImportacaoExportacaoException(e.getMessage());
        }
    }

    public byte[] downloadCooopeativaTxt(int id){
        Cooperativa cooperativa = buscarCoperativaId(id);

        try{
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

            String header = "00COOPERATIVA";
            header += LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));

            outputStreamWriter.write(header+"\n");

            String corpo = "02";
            corpo += String.format("%-25.25s", cooperativa.getNome());
            corpo += String.format("%-16.16s", cooperativa.getCnpj());
            corpo += String.format("%-30.30s", cooperativa.getUsuario().getEmail());

            outputStreamWriter.write(corpo+"\n");
            List<MaterialPreco> listMaterial = materialPrecoRepository.findByCooperativaIdOrderByVlrMaterial(id);

            String[] materiais = new String[]{"PET", "Ferro", "Papelão","Alumínio"};

            for (String material : materiais){
                String corpo2 = "03";
                corpo2 += String.format("%-17.17s","Material-"+material);
                Double valor = 0.0;
                for(MaterialPreco m : listMaterial){
                    if (m.getNome().equals(material)){
                        valor = m.getVlrMaterial();
                    }
                }
                corpo2 += String.format("%3.2f",valor);
                outputStreamWriter.write(corpo2+"\n");
            }

            String trailer = "01";
            trailer += String.format("%10d",listMaterial.size() +1);

            outputStreamWriter.write(trailer);
            outputStreamWriter.close();
            return byteArrayOutputStream.toByteArray();
        }
        catch (IOException e){
            throw new ImportacaoExportacaoException(e.getMessage());
        }
    }

    public Cooperativa buscarCooperativaEmail(String email){
        if (email.contains("@")){
            return this.repository.findByUsuarioEmail(email).orElseThrow(
                    () -> new EntidadeNaoEncontradaException(
                            "Email inválido"
                    )
            );
        }
        else{
            throw new EntidadeNaoEncontradaException("Email inválido");
        }
    }

    public Cooperativa buscarCooperativa(String auth) {
        String email = tokenJwt.getUsernameFromToken(auth);

        return this.repository.findByUsuarioEmail(email).orElseThrow(
                () -> new EntidadeNaoEncontradaException(
                        "Email inválido"
                )
        );
    }
}
