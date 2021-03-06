package br.com.aee.controller;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.Session;

import br.com.aee.model.Dependente;
import br.com.aee.model.Fatura;
import br.com.aee.report.ExecutorRelatorio;
import br.com.aee.repository.DependenteRepository;
import br.com.aee.repository.FaturaRepository;
import br.com.aee.util.JsfUtil;

@Named("geraRelatorioMB")
@ViewScoped
public class GeraRelatorioBean implements Serializable {

	private static final long serialVersionUID = 1L;

	@Inject
	private FacesContext facesContext;

	@Inject
	private HttpServletResponse response;

	@Inject
	private EntityManager manager;

	@Inject
	private FaturaRepository faturaRepository;

	@Inject
	private DependenteRepository dependenteRepository;

	private Long idBeneficiario;

	private Integer searchAno;

	private Double valor;

	private List<Dependente> listaDeDependenteDoBeneficiario;
	
	private Double valorDaFaixaEtaria;

	/**
	 * Lista fatura anual por beneficiário
	 *
	 * @return
	 */
	public List<Fatura> getListaFaturaAnualDoBeneficiario() {
		List<Fatura> listEmpty = new ArrayList<>();
		if (idBeneficiario == null || searchAno == null) {
			return listEmpty;
		} else {
			return faturaRepository.findByFaturaBeneficiarioAno(idBeneficiario, searchAno);
		}
	}

	public boolean isPossuiDependente() {
		return !dependenteRepository.findByIdBeneficiario(idBeneficiario).isEmpty();
	}

	/**
	 * Emite declaracao imposto de renda pf
	 */
	public void emitirDeclaracao() {
		List<Fatura> faturas = this.getListaFaturaAnualDoBeneficiario();
		listaDeDependenteDoBeneficiario = dependenteRepository.findByIdBeneficiario(idBeneficiario);
		faturas = faturaRepository.findByFaturaBeneficiarioAno(idBeneficiario, searchAno);
		System.out.println(">>> " + idBeneficiario + " " + searchAno);

		if (faturas.isEmpty()) {
			JsfUtil.error("Nenhum registro encontrado");
		}
	}

	/**
	 * Exporta para pdf
	 */
	public void emitirDeclaracaoImpostoDeRendaPDF() {
		if (idBeneficiario != null) {
			Map<String, Object> parametros = new HashMap<>();
			parametros.put("p_beneficiario_id", this.idBeneficiario);
			parametros.put("p_ano", this.searchAno);

			ExecutorRelatorio executor = new ExecutorRelatorio("/relatorios/declaracao_irpf_3.jasper", this.response,
					parametros, "Declaração Imposto de Renda.pdf");

			Session session = manager.unwrap(Session.class);
			session.doWork(executor);

			if (executor.isRelatorioGerado()) {
				facesContext.responseComplete();
			} else {
				JsfUtil.error("A execução do relatório não retornou dados");
			}
		} else {
			JsfUtil.error("A execução do relatório não retornou dados");
		}
	}

	/**
	 * Exporta para pdf
	 */
	public void emitirDeclaracaoImpostoDeRendaComValorInformadoPDF() {
		if (idBeneficiario != null) {
			Map<String, Object> parametros = new HashMap<>();
			parametros.put("p_beneficiario_id", this.idBeneficiario);
			parametros.put("p_valor", this.valor);

			ExecutorRelatorio executor = new ExecutorRelatorio("/relatorios/declaracao_irpf_valor_editado.jasper",
					this.response, parametros, "Declaração Imposto de Renda.pdf");

			Session session = manager.unwrap(Session.class);
			session.doWork(executor);

			if (executor.isRelatorioGerado()) {
				facesContext.responseComplete();
			} else {
				JsfUtil.error("A execução do relatório não retornou dados");
			}
		} else {
			JsfUtil.error("A execução do relatório não retornou dados");
		}
	}

	/**
	 * Exibe valor total para o titula
	 * 
	 * @return
	 */
	public String getSomaValorPlanoDeSaudeTitular() {
		double total = 0.00;
		for (Fatura fatura : this.getListaFaturaAnualDoBeneficiario()) {
			total += fatura.getValorPlanoDeSaude();
		}
		return new DecimalFormat("#,##0.00").format(total);
	}

	public String getSomaValorPlanoDeSaudeDependente(Dependente dependente) {
		double total = 0.00;
		this.houveMudancaDeFaixaEtaria(dependente);

		if (dependente.getBeneficiario().isApartamento()) {
			total = (dependente.getFaixaEtaria().getValorApartamento() * this.getListaFaturaAnualDoBeneficiario().size());
		} else {
			total = (dependente.getFaixaEtaria().getValorEnfermaria() * this.getListaFaturaAnualDoBeneficiario().size());
		}
		return new DecimalFormat("#,##0.00").format(total);
	}

	/**
	 * Detecta se houve mudança de faixa etaria para o ano
	 * 
	 * @param dependente
	 */
	public void houveMudancaDeFaixaEtaria(Dependente dependente) {
		int idade = dependente.getIdade();
		int faixaEtariaInicial = dependente.getFaixaEtaria().getPeriodoInicial();
		
		@SuppressWarnings("deprecation")
		int mesDeAniversario = dependente.getDataNascimento().getMonth();

		if ((idade - 1) >= faixaEtariaInicial) {
			System.out.println("A idade da " + dependente.getNomeComIniciaisMaiuscula() + " era: " + (idade - 1));
			System.out.println("Faixa atual: " + dependente.getFaixaEtaria().getPeriodo());
			setValorDaFaixaEtaria(dependente.getFaixaEtaria().getValorEnfermaria());
		} else {
			System.out.println("Houve mudança de faixa este ano " + mesDeAniversario);
			System.out.println("A idade da " + dependente.getNomeComIniciaisMaiuscula() + " era: " + (idade - 1));
			System.out.println("Faixa atual: " + dependente.getFaixaEtaria().getPeriodo());
			
			/**
			 * Detecta os meses que os valores da mensalidade devem ser diferentes
			 */
			for (Fatura fatura : this.getListaFaturaAnualDoBeneficiario()) {
				GregorianCalendar dataCal = new GregorianCalendar();
				dataCal.setTime(fatura.getVencimento());
				int mesDaFatura = dataCal.get(Calendar.MONTH);
				
				if(mesDaFatura <= mesDeAniversario) {
					setValorDaFaixaEtaria(0.00);
					System.out.println("Mes da fatura: " + mesDaFatura + " | " + fatura.getMesDaFatura() + " | " + valorDaFaixaEtaria);
				} else {
					setValorDaFaixaEtaria(dependente.getFaixaEtaria().getValorEnfermaria());
					System.out.println("Mes da fatura: " + mesDaFatura + " | " + fatura.getMesDaFatura() + " | " + valorDaFaixaEtaria);
				}
				
			}
			
		}
		
	}

	public Long getIdBeneficiario() {
		return idBeneficiario;
	}

	public void setIdBeneficiario(Long idBeneficiario) {
		this.idBeneficiario = idBeneficiario;
	}

	public Integer getSearchAno() {
		return searchAno;
	}

	public void setSearchAno(Integer searchAno) {
		this.searchAno = searchAno;
	}

	public Double getValor() {
		return valor;
	}

	public void setValor(Double valor) {
		this.valor = valor;
	}

	public List<Dependente> getListaDeDependenteDoBeneficiario() {
		return listaDeDependenteDoBeneficiario;
	}

	public List<Dependente> getDadosDoDependente(Dependente dependente) {
		return dependenteRepository.findById(dependente.getId());
	}
	
	public Double getValorDaFaixaEtaria() {
		return valorDaFaixaEtaria;
	}
	
	public void setValorDaFaixaEtaria(Double valorDaFaixaEtaria) {
		this.valorDaFaixaEtaria = valorDaFaixaEtaria;
	}
}
