package sd.model;

import java.io.Serializable;

public class Mensagem implements Serializable {
	
	public static int NOVO_MEMBRO = 1;			// uma especie de hello
	public static int CHAVE_PUBLICA = 2;		// anuncia sua propria chave publica (tem que seperar do banco de dados)
	public static int PRECO_MOEDAS = 4;		// acho que isso pode estar com o banco de dados
	public static int ANUNCIO_BANCO = 8;		// anuncia seu proprio banco de dados
	public static int RESULTADO_MINERACAO = 16;  // quando o minerador termina, avisa para todos
	
	public static int PEDIDO_COMPRA = 32;  // quando o minerador termina, avisa para todos
	public static int INFORME_DE_VENDA = 64;// quando o vendedor faz uma transferencia

	public int tipo;
	
	public String rI;// remetenteId  id do processo que esta enviando
	public String dI;// destinatarioId   em branco se brodcast
	
	public String m;//mensagem   criptografada
	public String mP;// mensagemPublica   para enviar mensagens abertas, como novo membro e chave publica
	public String valor;
	public  byte[] as;//assinatura dos dados  acima
	
	
	@Override
	public String toString() {
		return "Mensagem [tipo=" + tipo + ", remetenteId=" + rI
				+ ", destinatarioId=" + dI + ", mensagem="
				+ m + ", mensagemPublica=" + mP + "]";
	}
	
	
}
 
