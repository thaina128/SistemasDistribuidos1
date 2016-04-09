package sd;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import com.google.gson.Gson;

import sd.model.Banco;
import sd.model.BancoDeChaves;
import sd.model.Mensagem;

public class Peer extends Thread {

	MulticastSocket socket = null;
	int portaServer;
	ProcessoBase p; // processo base que cirou o peer
	InetAddress group;
	int porta;
	int portaUni;
	private PrivateKey priv;
	private PublicKey pub;
	Thread tEscutaBroadCasts; // verificar erro no tracker
	
	int id;// id do Peer

	ArrayList<Integer> idIntegrantes = new ArrayList<>();

	boolean minerador; 
	Random r = new Random();

	Gson gson = new Gson();

	int KEY_SIZE = 1024;//tamanho da java UNUSED

	ArrayList<Banco> bancoDeDados; // banco de dados distribuidos
	Banco meuBanco;//banco de dados local proprio do peer

	ArrayList<BancoDeChaves> bancoDeChaves; // banco de dados distribuidos
	BancoDeChaves minhaChaves;
	
	//GUI
	
	JTextArea texto1,texto2,texto3,texto4;
	public boolean DEBUG = false;//para ativar algusn prints de debug

	public Peer(MulticastSocket s, ProcessoBase p, InetAddress i, int porta,
			int id, int portaUni, int portaServer, boolean minerador) {
		socket = s;
		this.p = p;
		this.porta = porta;
		this.id = id;
		group = i;
		this.portaServer = portaServer;
		this.portaUni = portaUni;
		System.out.println(portaUni);
		geraChaves();

		this.minerador = minerador;
	}

	@Override
	public void run() {
	

		inicializaBanco();

		abreJanela();

		tEscutaBroadCasts = new EscutaBroadCasts();
		tEscutaBroadCasts.start();

		EnviaChavePublica tEnviaChave = new EnviaChavePublica();
		tEnviaChave.start();

		EnviaBanco tEnviaBanco = new EnviaBanco();
		tEnviaBanco.start();

//		Para fazer compras automaticas
//		FazCompras tFazCompras = new FazCompras();
//		tFazCompras.start();
		
		AtualizaTela tAtualizaTela = new AtualizaTela();
		tAtualizaTela.start();

		iniciaListenersSockets();

	}

	/**
	 * Cria seu proprio banco, da de 1000 a 9000 moedas para o peer.
	 * 
	 * Define a cotacao das moedas baseado na quantidade. Quanto mais moedas, maior o valor da troca.
	 * 
	 * 
	 */
	private void inicializaBanco() {
		// cria o meu banco
		meuBanco = new Banco();
		meuBanco.idUsuario = id;

		meuBanco.qM = r.nextInt(9000) + 1000;// de 1000 a 9000
		meuBanco.pV = meuBanco.qM / 10;// quanto menos moedas voce tem, mais
										// barato vc vende =/
		meuBanco.qmnc = 0;

		System.out.println(meuBanco);

		adicionaBancos(meuBanco);

		// cria banco de chaves privadas e publicas
		minhaChaves = new BancoDeChaves();
		minhaChaves.idUsuario = "" + id;
		minhaChaves.chavePublia = Base64.getEncoder().encodeToString(
				pub.getEncoded());

	}

	/**
	 * Adiciona ou atualiza banco de dados no registro de bancos
	 * 
	 * @param meuBanco
	 */
	public void adicionaBancos(Banco novoBanco) {

		if (bancoDeDados == null)
			bancoDeDados = new ArrayList<Banco>();

		boolean contains = false;
		for (Banco banco : bancoDeDados) {
			if (banco.idUsuario == novoBanco.idUsuario) {
				// atualiza
				banco.qM = novoBanco.qM;
				banco.pV = novoBanco.pV;
				banco.qmnc = novoBanco.qmnc;

				contains = true;
				break;

			}
		}

		if (!contains) {
			bancoDeDados.add(novoBanco);
			
			System.out.println(id + " Adicionou banco: " + novoBanco);
		}

	}

	/**
	 * Adiciona no banco de chaves uma nova chave publica de um Peer.
	 * 
	 * Nao permite atualizar chave publica
	 * 
	 * @param novoBanco
	 */
	public void adicionaChaves(BancoDeChaves novoBanco) {

		if (bancoDeChaves == null)
			bancoDeChaves = new ArrayList<BancoDeChaves>();

		boolean contains = false;
		for (BancoDeChaves banco : bancoDeChaves) {
			if (banco.idUsuario .equals(novoBanco.idUsuario)) {

				 // nao atualiza
				// chave publica pois so pode enviar 1 vez

				contains = true;
				break;

			}
		}

		if (!contains) {
			bancoDeChaves.add(novoBanco);
			
			System.out.println(id + " Adicionou banco de chaves: " + novoBanco);
		}

	}

	
// Criptografia foi substituita por assinatura
	
//	private byte[] encryptMsg(byte[] msg, Key Key) {
//		Cipher cipher;
//		byte[] cipherText = new byte[(KEY_SIZE / 8) - 11];
//		try {
//			cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
//			cipher.init(Cipher.ENCRYPT_MODE, Key);
//			cipherText = cipher.doFinal(msg);
//		} catch (NoSuchAlgorithmException | NoSuchPaddingException
//				| InvalidKeyException | IllegalBlockSizeException
//				| BadPaddingException ex) {
//			Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
//		}
//		return cipherText;
//	}
//
//	public String decrypt(byte[] text, Key key) {
//		byte[] dectyptedText = new byte[(KEY_SIZE / 8) - 11];
//		try {
//			// get an RSA cipher object and print the provider
//			final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
//
//			// decrypt the text using the private key
//			cipher.init(Cipher.DECRYPT_MODE, key);
//			dectyptedText = cipher.doFinal(text);
//
//		} catch (Exception ex) {
//			ex.printStackTrace();
//		}
//
//		return new String(dectyptedText);
//	}

	/**
	 * Gera chaves proprias para assinatura das mensagens
	 * 
	 */
	private void geraChaves() {
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
			keyGen.initialize(KEY_SIZE, random);
			KeyPair pair = keyGen.generateKeyPair();

			priv = pair.getPrivate(); // chave privada
			pub = pair.getPublic(); // chave publica que sera¡ enviada aos peers

		} catch (NoSuchAlgorithmException | NoSuchProviderException ex) {
			Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
		}
	}



	class EnviaBanco extends Thread {

		/**
		 * Envia seu banco junto com a cotação para a rede via broadcast
		 */
		@Override
		public void run() {

			while (true) {
				try {
					Thread.currentThread().sleep(500);
				} catch (InterruptedException ex) {
					Logger.getLogger(ProcessoBase.class.getName()).log(
							Level.SEVERE, null, ex);
				}

				if(DEBUG)
				System.out.println("Enviando banco...: " + id);

				if (gson == null)
					gson = new Gson();

				// monta mensagem
				Mensagem m = new Mensagem();
				m.tipo = Mensagem.ANUNCIO_BANCO;
				m.rI = String.valueOf(id);

				// assina o banco
				String bancoString = "" + meuBanco.idUsuario + meuBanco.qM
						+ meuBanco.pV;
				byte[] bancoBytes = bancoString.getBytes();
				byte[] signatureBytes = null;

				Signature sig;
				try {
					sig = Signature.getInstance("MD5WithRSA");

					sig.initSign(priv);
					sig.update(bancoBytes);
					signatureBytes = sig.sign();

				} catch (NoSuchAlgorithmException | InvalidKeyException
						| SignatureException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				if(DEBUG)
				System.out
						.println("Singature:"
								+ Base64.getEncoder().encode(signatureBytes)
										.toString());

				meuBanco.as = Base64.getEncoder().encode(signatureBytes);

			
				m.m = gson.toJson(meuBanco);

				String json = gson.toJson(m);
				if(DEBUG)
				System.out.println("Mensagem Enviada: " + json);
				byte msg[] = json.getBytes();

				DatagramPacket dp = new DatagramPacket(msg, msg.length, group,
						porta);

				try {
					socket.send(dp);
				} catch (IOException e) {

					e.printStackTrace();
				}

			}

		}

	}

	/**
	 * Dependendo do humor (aleatorio) faz compras de outras pessoas
	 * 
	 * @author Usuario
	 * 
	 */
	class FazCompras extends Thread {

		/**
		 * Efetua compras aleatoriamente, (UNUSED)
		 */
		@Override
		public void run() {

			while (true) {
				try {
					Thread.currentThread().sleep(500);
				} catch (InterruptedException ex) {
					Logger.getLogger(ProcessoBase.class.getName()).log(
							Level.SEVERE, null, ex);
				}

				if (r.nextInt(3) != 1)
					continue;

				if (meuBanco.qmnc != 0) {
					System.out.println("Não posso comprar.");
					return;
				}

				System.out.println("Fazendo ordem de compra...: " + id);

				if (gson == null)
					gson = new Gson();

				// monta mensagem
				Mensagem m = new Mensagem();
				m.tipo = Mensagem.PEDIDO_COMPRA;
				m.rI = String.valueOf(id);

				Banco bancoAlvo = bancoDeDados.get(r.nextInt(bancoDeDados
						.size()));
				// escolhe um destinatario
				m.dI = "" + bancoAlvo.idUsuario;

				// nao posso comprar de mim mesmo
				if (bancoAlvo.idUsuario == id) {
					return;// operacao proibibida
				}

				m.m = "" + bancoAlvo.qM / 10;// pede 1 decimo do que o peer
												// possui

				enviaUnicast(m);

			}

		}

		private void enviaUnicast(Mensagem m) {

			String json = gson.toJson(m);
			System.out.println("Mensagem Unicast Enviando...: " + json);
			byte msg[] = json.getBytes();

			Socket s = null;
			try {

				s = new Socket("localhost", portaServer
						+ Integer.parseInt(m.dI));// porta

				try {
					ObjectOutputStream objectOutput = new ObjectOutputStream(
							s.getOutputStream());
					objectOutput.writeObject(json);

				} catch (IOException e) {
				}
			} catch (UnknownHostException e) {
				System.out.println("Socket:" + e.getMessage());
			} catch (EOFException e) {
				System.out.println("EOF:" + e.getMessage());
			} catch (IOException e) {
				System.out.println("readline:" + e.getMessage());
			} finally {
				if (s != null) {
					try {
						s.close();
					} catch (IOException e) {
						System.out.println("close:" + e.getMessage());
					}
				}
			}

		}

	}

	
	class AtualizaTela extends Thread {

		/**
		 * Atualiza o banco de dados local na tela. (GUI)
		 */
		@Override
		public void run() {

			int cont= 0;
			while (true) {
				try {
					Thread.currentThread().sleep(500);
				} catch (InterruptedException ex) {
					Logger.getLogger(ProcessoBase.class.getName()).log(
							Level.SEVERE, null, ex);
				}

				if(texto1!=null){
					if(minerador){
						texto4.setText(""+ cont+ " minerador");
					}else{
						texto4.setText(""+ cont);
					}
					
					texto1.setText("Quantidade: "+meuBanco.qM);
					texto2.setText("Cotação: "+meuBanco.pV);
					texto3.setText("Não confirmada: "+meuBanco.qmnc);
					
					cont++;
				}

		}
			
		}

		public void enviaUnicast(Mensagem m) {

			String json = gson.toJson(m);
			System.out.println("Mensagem Unicast Enviando...: " + json);
			byte msg[] = json.getBytes();

			Socket s = null;
			try {

				s = new Socket("localhost", portaServer
						+ Integer.parseInt(m.dI));// porta

				try {
					ObjectOutputStream objectOutput = new ObjectOutputStream(
							s.getOutputStream());
					objectOutput.writeObject(json);

				} catch (IOException e) {
				}
			} catch (UnknownHostException e) {
				System.out.println("Socket:" + e.getMessage());
			} catch (EOFException e) {
				System.out.println("EOF:" + e.getMessage());
			} catch (IOException e) {
				System.out.println("readline:" + e.getMessage());
			} finally {
				if (s != null) {
					try {
						s.close();
					} catch (IOException e) {
						System.out.println("close:" + e.getMessage());
					}
				}
			}

		}

	}

	
	
	
	public static byte[] fromHexString(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
					.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	class EnviaChavePublica extends Thread {

		/**
		 * Envia seu banco com sua chave publica para o broadcast
		 */
		@Override
		public void run() {

			while (true) {

				if(DEBUG)
				System.out.println("Enviando chave publica...: " + id);

				if (gson == null)
					gson = new Gson();

				// monta mensagem
				Mensagem m = new Mensagem();
				m.tipo = Mensagem.CHAVE_PUBLICA;
				m.rI = String.valueOf(id);

				m.m = gson.toJson(minhaChaves);
				// / fim da mensagem

				String json = gson.toJson(m);
				if(DEBUG)
				System.out.println("Mensagem Enviada: " + json);
				byte msg[] = json.getBytes();

				DatagramPacket dp = new DatagramPacket(msg, msg.length, group,
						porta);

				try {
					socket.send(dp);
				} catch (IOException e) {

					e.printStackTrace();
				}

				try {
					Thread.currentThread().sleep(1000);
				} catch (InterruptedException ex) {
					Logger.getLogger(ProcessoBase.class.getName()).log(
							Level.SEVERE, null, ex);
				}

			}

		}

	}

	/**
	 * Separar a interface da lÃ³gica
	 */
	public void abreJanela() {

		final JFrame teste = new JFrame();
		teste.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		teste.setTitle("ID: " + id);
		teste.setLayout(new BorderLayout());
		teste.setSize(280, 400);

		Container c = teste.getContentPane();
		c.setLayout(new FlowLayout());

		

		JButton busca = new JButton("Tentar Comprar");
		final JTextField campoVendedor = new JTextField(5);
		campoVendedor.setToolTipText("Id Vendedor");
		final JTextField campoQuantidade = new JTextField(5);
		campoQuantidade.setToolTipText("Quantidade");
		busca.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				
				
				String strVendedorId = campoVendedor.getText();
			
				String strQuantidadeId = campoQuantidade.getText();
				
				campoVendedor.setText("");
				campoQuantidade.setText("");
				
				int vendedorId,quantidade;
				try{
				 vendedorId = Integer.parseInt(strVendedorId);
				
				 quantidade = Integer.parseInt(strQuantidadeId);
				}catch(Exception e2 ){
					texto4.setText("Erro ao capturar informacoes");
					return;
				}
				
				//NAo permite comprar mais que possui
			

				if (meuBanco.qmnc != 0) {
					System.out.println("Não posso comprar.");
					
					texto4.setText("Voce esta bloqueado, nao pode comprar.");
					return;
				}

				System.out.println("Fazendo ordem de compra...: " + id);

				if (gson == null)
					gson = new Gson();

				// monta mensagem
				Mensagem m = new Mensagem();
				m.tipo = Mensagem.PEDIDO_COMPRA;
				m.rI = String.valueOf(id);

				Banco bancoAlvo=null;
				//encontra o id do usuario
				for(Banco banco : bancoDeDados){
					if(banco.idUsuario==vendedorId){
						bancoAlvo = banco;
						break;
					}
				}
				if(bancoAlvo==null){
					texto4.setText("Usuario nao encontrado");
					return;
				}
				
				// escolhe um destinatario
				m.dI = "" + bancoAlvo.idUsuario;

				// nao posso comprar de mim mesmo
				if (bancoAlvo.idUsuario == id) {
					texto4.setText("Transacao Proibida");
					return;// operacao proibibida
				}

				m.m = "" + quantidade;

				enviaUnicast(m);
				
				
			
			}
		});

		
		
		 texto1 = new JTextArea();
		texto1.setText("Quatidade Moedas: ");
		
		 texto2 = new JTextArea();
		texto2.setText("Valor: ");
		
		 texto3 = new JTextArea();
		texto3.setText("Moedas Nao Confirmadas");
		 texto4 = new JTextArea();
		
		teste.getContentPane().add(campoQuantidade);
		teste.getContentPane().add(campoVendedor);
		teste.getContentPane().add(busca);
		teste.getContentPane().add(texto4);
		teste.getContentPane().add(texto1);
		teste.getContentPane().add(texto2);
		teste.getContentPane().add(texto3);
		
		

	
		teste.setVisible(true);

	}

	protected void enviaUnicast(Mensagem m) {
		String json = gson.toJson(m);
		System.out.println("Mensagem Unicast Enviando...: " + json);
		byte msg[] = json.getBytes();

		Socket s = null;
		try {

			s = new Socket("localhost", portaServer
					+ Integer.parseInt(m.dI));// porta

		
			try {
				ObjectOutputStream objectOutput = new ObjectOutputStream(
						s.getOutputStream());
				objectOutput.writeObject(json);

			} catch (IOException e) {
			}
		} catch (UnknownHostException e) {
			System.out.println("Socket:" + e.getMessage());
		} catch (EOFException e) {
			System.out.println("EOF:" + e.getMessage());
		} catch (IOException e) {
			System.out.println("readline:" + e.getMessage());
		} finally {
			if (s != null) {
				try {
					s.close();
				} catch (IOException e) {
					System.out.println("close:" + e.getMessage());
				}
			}
		}

	}

		
	
	/**
	 * Abre o canal de escuta do socket
	 */
	public void iniciaListenersSockets() {

		try {
			int serverPort = portaUni; // the server port
			System.out.println("Escuta porta:" + portaUni);

			ServerSocket listenSocket = new ServerSocket(serverPort);

			while (true) {

				Socket clientSocket = listenSocket.accept();
				ConnectionCliente c = new ConnectionCliente(clientSocket, this);
				c.start();
			}
		} catch (IOException e) {
			System.out.println("Listen socket:" + e.getMessage());
		}
	}

	



	
	/**
	 * Responsavel por escutar todas mensagens de broadcast
	 */
	class EscutaBroadCasts extends Thread {

		@Override
		public void run() {
			
			while (true) {
				try {
					byte[] buffer = new byte[1024];
					DatagramPacket messageIn = new DatagramPacket(buffer,
							buffer.length);
					socket.setSoTimeout(6000); // timeout de 6s
					socket.receive(messageIn);
					// System.out.println(new String(messageIn.getData()));

					// trata as mensagens que recebe em broadcast

					String msgLidaString = new String(messageIn.getData());
					msgLidaString = msgLidaString.trim();
					// System.out.println("Mensagem Recebida: "+msgLidaString);

					Mensagem mLida = new Mensagem();
					mLida = gson.fromJson(msgLidaString, Mensagem.class);

					// le a mensagem recebida:

					if(DEBUG)
					System.out.println(id + " Mensagem recebida: " + mLida);

					if (mLida.tipo == Mensagem.ANUNCIO_BANCO) {
					

						trataBancoRecebido(mLida);

					} else if (mLida.tipo == Mensagem.CHAVE_PUBLICA) {
						try {
							BancoDeChaves banco = new BancoDeChaves();
							banco = gson.fromJson(mLida.m, BancoDeChaves.class);

							adicionaChaves(banco);// possivel erro de sinc
						} catch (Exception e) {
							System.out.println("Chave não valido");
						}
					} else if (mLida.tipo == Mensagem.INFORME_DE_VENDA) {

						// se voce for o minerador

						if (minerador) {
							// valida ou nao a transacao e ganha 10 reais

							mineradorValidaCompra(mLida);
						}

						// se voce for o solicitador (no caso o destinatario)
						if (mLida.dI.equals("" + id)) {
							// coloca para quantidade de moedas nao confirmadas
							solicitadorCompraResposta(mLida);

						}

						// se voce for o vendedor (no caso o remetente)

						// nao faz nada

					} else if (mLida.tipo == Mensagem.RESULTADO_MINERACAO) {

						// se voce for o comprador ou vendedor atualiza a
						// quantidade de moedas
						
						respostaMineracao(mLida);
						
						
						

					}

				} catch (SocketTimeoutException e) {
					System.out.println("Timeout de escuta de broadcast");

				} catch (SocketException ex) {
					Logger.getLogger(Peer.class.getName()).log(Level.SEVERE,
							null, ex);
				} catch (IOException ex) {
					Logger.getLogger(Peer.class.getName()).log(Level.SEVERE,
							null, ex);
				}
			}
		}

		/**
		 * // se voce for o comprador ou vendedor atualiza a
			// quantidade de moedas
		 * 
		 * @param mLida
		 */
		private void respostaMineracao(Mensagem mLida) {

			//formato da mensagem
//			Mensagem m = new Mensagem();
//			m.tipo = Mensagem.RESULTADO_MINERACAO;
//			m.rI = mRec.rI;
//			m.dI = mRec.dI;
//			m.m = "" + mRec.m;
//			m.valor = "" + mRec.valor;
//
//			if (!b) {// se recusou envia 0 no valor
//				m.valor = "0";
//			}
			
			// se voce for o comprador ou vendedor atualiza a
			// quantidade de moedas
			
			//
			if (mLida.dI.equals("" + id) || mLida.rI.equals("" + id)) { //comprador 
				
				//se foi aprovado
				if(!mLida.valor.equals("0")){
					meuBanco.qM += meuBanco.qmnc; //atualiza a quantidade de moeadas
				}
				
				meuBanco.qmnc = 0;//zera as moedas bloqueadas para poder destravar
				
				//if(DEBUG)
				System.out.println("Transacao autorizada, atualizei meu banco. ");
				System.out.println(id+" Compra ou venda, meu total foi para: "+meuBanco.qM);
			
			}

			
		}

		/**
		 * Minerador verifica a assinatura da transacao, se estiver ok retorna
		 * OK, e soma seu saldo. Se nao estiver ok retorna nOK e nao soma seu
		 * saldo
		 * 
		 * 
		 * @param mLida
		 */
		private void mineradorValidaCompra(Mensagem mLida) {

			// Mensagem m = new Mensagem();
			// m.tipo = Mensagem.INFORME_DE_VENDA;
			// m.rI = String.valueOf(id);
			// m.dI = idSolicitante;
			// m.m = ""+quantidadeSolicitada ;//quantidade
			// m.valor = ""+meuBanco.pV;

			String chavePublicaRemetente = null;
			// busca a chave
			boolean encontrou = false;
			for (BancoDeChaves chave : bancoDeChaves) {
				if (chave.idUsuario.equals(mLida.rI)) {
					chavePublicaRemetente = chave.chavePublia;
					encontrou = true;
				}
			}

			if (!encontrou) {
				System.out.println("Sem chave publica para o remetente: "
						+ mLida.rI);
				return;
			}

			byte[] publicBytes = Base64.getDecoder().decode(
					chavePublicaRemetente);// chavePublicaRemetente.getBytes();
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
			KeyFactory keyFactory;
			PublicKey pubKey = null;
			byte[] assBytes = null;
			Signature sig = null;
			try {
				keyFactory = KeyFactory.getInstance("RSA");

				pubKey = keyFactory.generatePublic(keySpec);

				// valida banco
				String assinaturaString = "" + mLida.rI + mLida.m + mLida.valor;

				assBytes = Base64.getDecoder().decode(mLida.as);

				sig = Signature.getInstance("MD5WithRSA");
				sig.initVerify(pubKey);
				sig.update(assinaturaString.getBytes());

				if (sig.verify(assBytes)) {

					System.out
							.println("Operacao de venda  autorizada: Vendedor: "
									+ mLida.rI
									+ " Comprador: "
									+ mLida.dI
									+ " Quantidade: " + mLida.m);

					meuBanco.qM += 10;// comissao do minerador
					System.out.println(id + "Ganhei minha comissao ");

					enviaResultadoMineracao(mLida, true);

				} else {
					enviaResultadoMineracao(mLida, false);
					System.out
							.println("Operacao de venda nao autorizada: Vendedor: "
									+ mLida.rI
									+ " Comprador: "
									+ mLida.dI
									+ " Quantidade: " + mLida.m);
					// Enviar broadcast de nao aceitacao

				}
			} catch (NoSuchAlgorithmException | InvalidKeySpecException
					| InvalidKeyException | SignatureException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();

				System.out
						.println("Operacao de venda nao autorizada: Vendedor: "
								+ mLida.rI + " Comprador: " + mLida.dI
								+ " Quantidade: " + mLida.m);
				enviaResultadoMineracao(mLida, false);

			}

			// se ok

			// se nao ok
			// envia resposta

		}

		/**
		 * Quando o solicitador de uma compra recebe uma confirmacao (em
		 * broadcast) do vendedor.
		 * 
		 * Ele deve atualizar a quantidade de moedas nao confirmadas
		 * 
		 * @param mLida
		 */
		private void solicitadorCompraResposta(Mensagem m) {

			int quantidadeSolicitada = Integer.parseInt(m.m);
			int precoCompra = Integer.parseInt(m.valor);

			meuBanco.qmnc = (+quantidadeSolicitada)
					- (quantidadeSolicitada * precoCompra);

			System.out
					.println("Sou o comprador e tenho essas moedas nao confirmadas: "
							+ meuBanco.qmnc);

		}

		/**
		 * Recebe uma mansagem, valida a autoria da mesma e atualiza o banco de
		 * dados
		 * 
		 * @param mLida
		 */
		private void trataBancoRecebido(Mensagem mLida) {
			try {

				// decodifica
				// pega a chave desse id e tenta decodificar se der formato
				// valido ok

				// nao le o proprio banco
				if (mLida.rI.equals("" + id))
					return;

				String chavePublicaRemetente = null;
				// busca a chave
				boolean encontrou = false;
				for (BancoDeChaves chave : bancoDeChaves) {
					if (chave.idUsuario.equals(mLida.rI)) {
						chavePublicaRemetente = chave.chavePublia;
						encontrou = true;
					}
				}

				if (!encontrou) {
					System.out.println("Sem chave publica para o remetente: "
							+ mLida.rI);
					return;
				}

				byte[] publicBytes = Base64.getDecoder().decode(
						chavePublicaRemetente);// chavePublicaRemetente.getBytes();
				X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
				KeyFactory keyFactory = KeyFactory.getInstance("RSA");
				PublicKey pubKey = keyFactory.generatePublic(keySpec);
				

				// valida se banco é mesmo original

				Banco banco = new Banco();
				banco = gson.fromJson(mLida.m, Banco.class);

				// ve se eh o banco de verdade
				String bancoString = "" + banco.idUsuario + banco.qM + banco.pV;

				byte[] bancoBytes = Base64.getDecoder().decode(banco.as);
		
				Signature sig = Signature.getInstance("MD5WithRSA");
				sig.initVerify(pubKey);
				sig.update(bancoString.getBytes());

				if (sig.verify(bancoBytes)) {
					adicionaBancos(banco);// possivel erro de sinc
					if(DEBUG)
					System.out.println("Banco original");
				} else {
					System.out.println(id + "Banco falsificado" + mLida.rI);
				}

			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Banco não valido");
			}

		}
	}

	/**
	 * Classe de escuta da conexÃ£o
	 * 
	 * @author 
	 */
	class ConnectionCliente extends Thread {

		DataInputStream in;
		DataOutputStream out;
		Socket clientSocket;
		int idIntegrantes;
		Peer c;

	
		public ConnectionCliente(Socket aClientSocket, Peer c)
				throws IOException {

			this.c = c;
			clientSocket = aClientSocket;

			try {
				clientSocket = aClientSocket;
				in = new DataInputStream(clientSocket.getInputStream());
				out = new DataOutputStream(clientSocket.getOutputStream());
				
			} catch (IOException e) {
				System.out.println("Connection:" + e.getMessage());
			}

		}

		@Override
		public void run() {
			while (true) {
				try {

					ObjectInputStream objectInput = new ObjectInputStream(
							clientSocket.getInputStream()); // Error Line!
					try {
						Object object = objectInput.readObject();

						Mensagem m = gson.fromJson((String) object,
								Mensagem.class);
						System.out.println("Mensagem recebida no socket: " + m);

						if (m.tipo == Mensagem.PEDIDO_COMPRA) {

							// cria uma operacao d evenda, multcast com
							// assinatura
							// atualiza seu banco

							enviaOperacaoDeVenda(m);

							// o comprador ao receber devera atualizar seu banco
							// o minerador devera validar

							// apos o minerado validar o comprador atualiza seu
							// banco e o vendedor

						}

					

					} catch (ClassNotFoundException e) {
						System.out
								.println("Erro.");
						e.printStackTrace();
					}

				} catch (EOFException e) {
					// System.out.println("EOF:" + e.getMessage());
				} catch (IOException e) {
					// System.out.println("readline:" + e.getMessage());
				}

			}
		}

		/**
		 * Quando vendedor inicia uma venda. Ele deve atualizar seu banco. E
		 * fazer um broadcast.
		 * 
		 * @param m
		 */
		private void enviaOperacaoDeVenda(Mensagem mRec) {
			

			if (meuBanco.qmnc != 0) {
				System.out.println("Não posso vender.");
				return;
			}

			int quantidadeSolicitada = Integer.parseInt(mRec.m);
			String idSolicitante = mRec.rI;

			System.out.println("Iniciando transacao de venda de:: " + id
					+ " para: " + idSolicitante + " no valor: "
					+ quantidadeSolicitada);

			if (gson == null)
				gson = new Gson();

			// monta mensagem
			Mensagem m = new Mensagem();
			m.tipo = Mensagem.INFORME_DE_VENDA;
			m.rI = String.valueOf(id);
			m.dI = idSolicitante;
			m.m = "" + quantidadeSolicitada;// quantidade
			m.valor = "" + meuBanco.pV;

	
			// Substitui o timestamp por um lock. (Se tiver mensagem nao validadas ele bloqueia)
			
			// atualiza banco dele
			// - a quantidade solicitada + (solicitada*valor venda) - 10 taxa
			//
			meuBanco.qmnc = (-quantidadeSolicitada)
					+ (quantidadeSolicitada * meuBanco.pV) - 10;

			System.out
					.println("Sou o vendedor e tenho essas moedas nao confirmadas: "
							+ meuBanco.qmnc);

			// assina o banco com id do vendedor, quantidade solicitada e valor
			String bancoString = "" + meuBanco.idUsuario + quantidadeSolicitada
					+ meuBanco.pV;
			byte[] bancoBytes = bancoString.getBytes();
			byte[] signatureBytes = null;

			Signature sig;
			try {
				sig = Signature.getInstance("MD5WithRSA");

				sig.initSign(priv);
				sig.update(bancoBytes);
				signatureBytes = sig.sign();

			} catch (NoSuchAlgorithmException | InvalidKeyException
					| SignatureException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			if(DEBUG)
			System.out.println("Singature:"
					+ Base64.getEncoder().encode(signatureBytes).toString());

			m.as = Base64.getEncoder().encode(signatureBytes);

			String json = gson.toJson(m);
			System.out.println("Mensagem Enviada: " + json);
			byte msg[] = json.getBytes();

			DatagramPacket dp = new DatagramPacket(msg, msg.length, group,
					porta);

			try {
				socket.send(dp);
			} catch (IOException e) {

				e.printStackTrace();
			}

		}
	}

	/**
	 * Quando um minerador avisa se a operacao foi aprovada ou nao via broadcast
	 * 
	 * @param b
	 * 
	 * @param m
	 */
	private void enviaResultadoMineracao(Mensagem mRec, boolean b) {

		// Formato da mensagem que ele recebe
		
		// Mensagem m = new Mensagem();
		// m.tipo = Mensagem.INFORME_DE_VENDA;
		// m.rI = String.valueOf(id);
		// m.dI = idSolicitante;
		// m.m = ""+quantidadeSolicitada ;//quantidade
		// m.valor = ""+meuBanco.pV;

		int quantidadeSolicitada = Integer.parseInt(mRec.m);
		String idSolicitante = mRec.rI;

		System.out.println("Iniciando transacao de venda de:: " + id
				+ " para: " + idSolicitante + " no valor: "
				+ quantidadeSolicitada);

		if (gson == null)
			gson = new Gson();

		// monta mensagem
		Mensagem m = new Mensagem();
		m.tipo = Mensagem.RESULTADO_MINERACAO;
		m.rI = mRec.rI;
		m.dI = mRec.dI;
		m.m = "" + mRec.m;
		m.valor = "" + mRec.valor;

		if (!b) {// se recusou envia 0 no valor
			m.valor = "0";
		}

		String json = gson.toJson(m);
		System.out.println("Mensagem Enviada de Mineracao: " + json);
		byte msg[] = json.getBytes();

		DatagramPacket dp = new DatagramPacket(msg, msg.length, group, porta);

		try {
			socket.send(dp);
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

}