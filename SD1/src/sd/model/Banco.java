package sd.model;

/**
 * Banco de cada peer
 * 
 * @author Usuario
 *
 */
public class Banco {
	
	public int idUsuario;

	public int qM;//quantidadeMoedas
	public int pV;//precoVenda
	public int qmnc=0;//quantidadeMoedasNaoConfirmadas
	
	public  byte[] as;//assinatura dos dados  acima
	
	
	@Override
	public String toString() {
		return "Banco [idUsuario=" + idUsuario 
				+ ", quantidadeMoedas=" + qM + ", precoVenda="
				+ pV + ", quantidadeMoedasNaoConfirmadas="
				+ qmnc + "]";
	}
	
	

}
