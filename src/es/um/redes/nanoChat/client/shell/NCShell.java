package es.um.redes.nanoChat.client.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import es.um.redes.nanoChat.client.comm.NCConnector;

public class NCShell 
{
	/* Scanner para leer comandos de usuario de la entrada estándar. */
	private Scanner reader;

	/* Variables que almacenan comando y argumentos. */
	byte command = NCCommands.COM_INVALID;
	String[] commandArgs = new String[0];

	/* Constructor. */
	public NCShell() 
	{
		this.reader = new Scanner(System.in);

		System.out.println("NanoChat shell");
		System.out.println("For help, type 'help'");
	}

	/* Devuelve el comando introducido por el usuario. */
	public byte getCommand() 
	{
		return this.command;
	}

	/* Devuelve los parámetros proporcionados por el usuario para el comando actual. */
	public String[] getCommandArguments() 
	{
		return this.commandArgs;
	}

	/* Espera hasta obtener un comando válido entre los comandos existentes. */
	public void readGeneralCommand() 
	{
		boolean validArgs = false;
		do 
		{
			this.commandArgs = readGeneralCommandFromStdIn();
			/* Si el comando tiene parámetros hay que validarlos. */
			validArgs = validateCommandArguments(this.commandArgs);
		} 
		while(!validArgs);
	}

	/* Usa la entrada estándar para leer comandos y procesarlos. */
	private String[] readGeneralCommandFromStdIn() 
	{
		String[] args = new String[0];
		Vector<String> vargs = new Vector<String>();
		while (true) 
		{
			System.out.print("(nanoChat) ");
			/* Obtenemos la línea tecleada por el usuario. */
			String input = reader.nextLine();
			StringTokenizer st = new StringTokenizer(input);
			/* Si no hay ni comando entonces volvemos a empezar. */
			if (!st.hasMoreTokens()) 
			{
				continue;
			}
			/* Traducimos la cadena del usuario en el código de comando correspondiente. */
			this.command = NCCommands.stringToCommand(st.nextToken());
			/* Dependiendo del comando... */
			switch (this.command) 
			{
				case NCCommands.COM_INVALID:
					/* El comando no es válido. */
					System.out.println("Invalid command.");
					continue;
				case NCCommands.COM_HELP:
					/* Mostramos la ayuda. */
					NCCommands.printCommandsHelp();
					continue;
				case NCCommands.COM_QUIT:
					break;
				case NCCommands.COM_ROOM_LIST:
					break;
				case NCCommands.COM_ROOM_ENTER:
					/* Requiere un parámetro. */
					while (st.hasMoreTokens()) 
					{
						vargs.add(st.nextToken());
					}
					break;
				case NCCommands.COM_REGISTER_NICK:
					/* Requiere un parámetro. */
					while (st.hasMoreTokens()) 
					{
						vargs.add(st.nextToken());
					}
					break;
				/* Añadido para cumplir con el modelo del autómata. */
				case NCCommands.COM_ROOM_INFO:
					/* Requiere un parámetro. */
					while (st.hasMoreTokens()) 
					{
						vargs.add(st.nextToken());
					}
					break;
				/* Caso de querer crear una nueva sala. */
				case NCCommands.COM_CREATE_ROOM:
					/* Requiere un parámetro. */
					while (st.hasMoreTokens()) 
					{
						vargs.add(st.nextToken());
					}
					break;
				default:
					System.out.println("That command is only valid if you are in a room.");
			}
			break;
		}
		/* Devolvemos los parámetros. */
		return vargs.toArray(args);
	}

	/* Espera a que haya un comando válido de sala o llegue un mensaje entrante. */
	public void readChatCommand(NCConnector ngclient) 
	{
		boolean validArgs;
		do 
		{
			this.commandArgs = readChatCommandFromStdIn(ngclient);
			/* Si hay parámetros se validan. */
			validArgs = validateCommandArguments(this.commandArgs);
		}
		while(!validArgs);
	}

	/* Utiliza la entrada estándar para leer comandos y comprueba si hay datos en el flujo de entrada del conector. */
	private String[] readChatCommandFromStdIn(NCConnector ncclient) 
	{
		String[] args = new String[0];
		Vector<String> vargs = new Vector<String>();
		while (true) 
		{
			System.out.print("(nanoChat-room) ");
			/* Utilizamos un BufferedReader en lugar de un Scanner porque no podemos bloquear la entrada. */
			BufferedReader standardInput = new BufferedReader(new InputStreamReader(System.in));
			boolean blocked = true;
			String input ="";
			/* Estamos esperando comando o mensaje entrante. */
			while (blocked) 
			{
				try 
				{
					if (ncclient.isDataAvailable()) 
					{
						/* Si el flujo de entrada tiene datos entonces el comando actual es SOCKET_IN y debemos salir. */
						this.command = NCCommands.COM_SOCKET_IN;
						return null;
					}
					/* Analizamos si hay datos en la entrada estándar. (el usuario tecleó INTRO) */
					else if (standardInput.ready()) 
					{
						input = standardInput.readLine();
						blocked = false;
					}
					/* Puesto que estamos sondeando las dos entradas de forma continua, esperamos para evitar un consumo alto de CPU. */
					TimeUnit.MILLISECONDS.sleep(50);
				} 
				catch (IOException | InterruptedException e) 
				{
					this.command = NCCommands.COM_INVALID;
					return null;
				}
			}
			/* Si el usuario tecleó un comando entonces procedemos de igual forma que hicimos antes para los comandos generales. */
			StringTokenizer st = new StringTokenizer(input);
			if (!st.hasMoreTokens()) continue;
			this.command = NCCommands.stringToCommand(st.nextToken());
			switch (this.command)
			{
				case NCCommands.COM_INVALID:
					System.out.println("Invalid command ("+input+").");
					continue;
				case NCCommands.COM_HELP:
					NCCommands.printCommandsHelp();
					continue;
				case NCCommands.COM_ROOM_INFO:
					/* Requiere un parámetro debido a la definición de nuestro protocolo de comunicación. */
					while (st.hasMoreTokens()) 
					{
						vargs.add(st.nextToken());
					}
					break;
				case NCCommands.COM_EXIT_ROOM:
					break;
				/* Añadido para cumplir con nuestro autómata. */
				case NCCommands.COM_QUIT:
					break;
				/* Añadido para cumplir con nuestro autómata. */
				case NCCommands.COM_ROOM_LIST:
					break;
				case NCCommands.COM_SEND_TEXT:
					StringBuffer message = new StringBuffer();
					while (st.hasMoreTokens())
						message.append(st.nextToken()+" ");
					vargs.add(message.toString());
					break;
				/* Caso de querer actualizar el nombre de nuestra sala. */
				case NCCommands.COM_RENAME_ROOM:
					/* Requiere un parámetro debido a la definición de nuestro protocolo de comunicación. */
					while (st.hasMoreTokens()) 
					{
						vargs.add(st.nextToken());
					}
					break;
				default:
					System.out.println("That command is only valid if you are not in a room.");;
			}
			break;
		}
		return vargs.toArray(args);
	}


	/* Algunos comandos requieren un parámetro. Este método comprueba si se proporciona parámetro para los comandos. */
	private boolean validateCommandArguments(String[] args) 
	{
		switch(this.command) 
		{
			/* enter requiere el parámetro <room>. */
			case NCCommands.COM_ROOM_ENTER:
				if (args.length == 0 || args.length > 1) 
				{
					System.out.println("Correct use: enter <room>");
					return false;
				}
				break;
			/* nick requiere el parámetro <nickname> */
			case NCCommands.COM_REGISTER_NICK:
				if (args.length == 0 || args.length > 1) 
				{
					System.out.println("Correct use: nick <nickname>");
					return false;
				}
				break;
			/* send requiere el parámetro <message> */
			case NCCommands.COM_SEND_TEXT:
				if (args.length == 0) 
				{
					System.out.println("Correct use: send <message>");
					return false;
				}
				break;
			/* Añadido para cumplir con nuestro autómata. info requiere el parámetro <room> */
			case NCCommands.COM_ROOM_INFO:
				if (args.length > 1)
				{
					System.out.println("Correct use: info <room> or info alone if you are in a room");
					return false;
				}
				break;
			/* Añadido para la creación de salas. */
			case NCCommands.COM_CREATE_ROOM:
				if (args.length == 0 || args.length > 1)
				{
					System.out.println("Correct use: create <room>");
					return false;
				}
				break;
			/* rename requiere un parámetro. */
			case NCCommands.COM_RENAME_ROOM:
				if (args.length == 0 || args.length > 1)
				{
					System.out.println("Correct use: rename <new name>");
					return false;
				}
				break;
			default:
		}
		/* El resto no requieren parámetro. */
		return true;
	}
}