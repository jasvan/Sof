package klfr.sof.cli;

// MOAR STANDARD LIBRARY
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import klfr.sof.IOInterface;
import klfr.sof.Interpreter;
import klfr.sof.Preprocessor;

public class CLI {

	public static final String HELP_STRING = "" + "sof - Interpreter for Stack with Objects and       %n"
			+ "      Functions (SOF) Programming Language.        %n"
			+ "usage: sof [-hvdpP] [-c COMMAND]                   %n"
			+ "           FILENAME [...FILENAMES]                 %n"
			+ "                                                   %n"
			+ "positional arguments:                              %n"
			+ "   filename  Path to a file to be read and         %n"
			+ "             executed. Can be a list of files that %n"
			+ "             are executed in order.                %n"
			+ "                                                   %n"
			+ "options:                                           %n"
			+ "   --help, -h                                      %n"
			+ "             Display this help message and exit.   %n"
			+ "   --version, -v                                   %n"
			+ "             Display version information and exit. %n"
			+ "   -d        Execute in debug mode. Read the manual%n"
			+ "             for more information.                 %n"
			+ "   -p        Run the preprocessor and exit.        %n"
			+ "   -P        Do not run the preprocessor before    %n"
			+ "             executing the input file(s).          %n"
			+ "   --command, -c COMMAND                           %n"
			+ "             Execute COMMAND and exit.             %n"
			+ "                                                   %n"
			+ "When used without execution-starting arguments (-c %n"
			+ "or filename), sof is started in interactive mode.  %n"
			+ "                                                   %n"
			+ "Quit the program with ^C.                          %n"
			+ "                                                   %n";

	public static final String INFO_STRING = String.format("sof version %s (built %s)", Interpreter.VERSION,
			// awww yesss, the Java Time API 😋
			DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(buildTime().atZone(ZoneId.systemDefault())));

	private static final Logger log = Logger.getLogger(CLI.class.getCanonicalName());

	public static void main(String[] args) throws InvocationTargetException, UnsupportedEncodingException, IOException {
		// setup console info logging
		LogManager.getLogManager().reset();
		Logger.getLogger("").setLevel(Level.FINEST);
		var ch = new ConsoleHandler();
		ch.setLevel(Level.OFF);
		Logger.getLogger("").addHandler(ch);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			Logger.getLogger("").info("SOF exiting.");
		}));

		var opt = Options.parseOptions(args);

		IOInterface io = new IOInterface();
		io.setInOut(System.in, System.out);

		if ((opt.flags & Options.DEBUG) > 0) {
			try {
				LogManager.getLogManager().reset();
				var rootLog = Logger.getLogger("");
				rootLog.setLevel(Level.ALL);
				ch = new ConsoleHandler();
				ch.setLevel(Level.FINE);

				ch.setFormatter(new Formatter() {
					@Override
					public String format(LogRecord record) {
						final var msg = record.getMessage();
						try {
							record.getResourceBundle().getString(record.getMessage());
						} catch (MissingResourceException | NullPointerException e) {
							// do nothing
						}
						final var time = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
								.format(record.getInstant().atZone(ZoneId.systemDefault()));
						final var level = record.getLevel().getLocalizedName().substring(0,
								Math.min(record.getLevel().getLocalizedName().length(), 6));
						final var logName = record.getLoggerName().replace("klfr.sof", "~");

						return String.format("[%s %-20s |%6s] %s%n", time, logName, level, msg) + (record.getThrown() == null
								? ""
								: String.format("EXCEPTION: %s | Stack trace:%n%s", record.getThrown().toString(),
										Arrays.asList(record.getThrown().getStackTrace()).stream().map(x -> x.toString()).collect(
												() -> new StringBuilder(),
												(builder, str) -> builder.append("in ").append(str).append(System.lineSeparator()),
												(b1, b2) -> b1.append(b2))));
					}
				});
				rootLog.addHandler(ch);
				var handler = new FileHandler("sof-log.log");
				handler.setFormatter(new SimpleFormatter());
				handler.setEncoding("utf-8");
				handler.setLevel(Level.FINEST);
				rootLog.addHandler(handler);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// execute
		var error = opt.apply(io);
		if (error.isPresent()) {
			var t = error.get();
			log.log(Level.SEVERE,
					String.format("Uncaught Interpreter exception: %s%nStack trace:%n%s", t.getLocalizedMessage(),
							Arrays.asList(t.getStackTrace()).stream().map(ste -> ste.toString())
									.reduce((a, b) -> a + System.lineSeparator() + b).orElse("")));
		}
	}

	/**
	 * Does full execution on SOF source code given the environment.
	 * 
	 * @param codeStream      A reader that reads source code.
	 * @param interpreter     The interpreter to use for the execution. It is reset
	 *                        and its source code replaced.
	 * @param io              The Input-Output interface that the full execution
	 *                        should use.
	 * @param doPreprocessing Whether to execute the preprocessor on the source code
	 *                        before passing it into the interpreter.
	 */
	public static void doFullExecution(Reader codeStream, Interpreter interpreter, IOInterface io,
			boolean doPreprocessing) throws Exception {
		log.entering(CLI.class.getCanonicalName(), "doFullExecution");
		String code = "";
		try {
			StringWriter writer = new StringWriter();
			codeStream.transferTo(writer);
			code = writer.getBuffer().toString();
		} catch (IOException e) {
			throw new Exception("Unknown exception occurred during input reading.", e);
		}

		if (doPreprocessing)
			code = Preprocessor.preprocessCode(code);

		interpreter.reset() // reset interpreter
				.setCode(code) // set the code to execute
						.internal.setIO(io); // set the I/O system to use
		while (interpreter.canExecute())
			interpreter.executeOnce();

		log.exiting(CLI.class.getCanonicalName(), "doFullExecution");
	}

	/**
	 * Runs the SOF preprocessor on the reader and prints the result to the given IO
	 * interface output
	 * 
	 * @param reader A reader that reads SOF source code.
	 */
	public static void runPreprocessor(Reader reader, IOInterface io) throws Exception {
		String code = "";
		try {
			StringWriter writer = new StringWriter();
			reader.transferTo(writer);
			code = writer.getBuffer().toString();
		} catch (IOException e) {
			throw new Exception("Unknown exception occurred during input reading.", e);
		}
		io.print(Preprocessor.preprocessCode(code));
	}

	public static void exitUnnormal(int status) {
		System.out.println("So long, and thank's for all the fish...");
		System.exit(status);
	}

	public static Instant buildTime() {
		try {
			URI classuri = CLI.class.getClassLoader()
					.getResource(CLI.class.getCanonicalName().replace(".", "/") + ".class").toURI();
			// log.finest(classuri.getScheme() + " " + classuri.getPath());
			if (classuri.getScheme().equals("rsrc") || classuri.getScheme().equals("jar")) {
				// we are in a jar file
				// returns the containing folder of the jar file
				// String jarpath = new
				// File(ClassLoader.getSystemResource(".").getFile()).getCanonicalPath();
				String jarfilepath = new File(".").getCanonicalPath() + File.separator
						+ System.getProperty("java.class.path");
				// log.finest(jarfilepath);
				return Instant.ofEpochMilli(new File(jarfilepath).lastModified());
			} else if (classuri.getScheme().equals("file")) {
				return Instant.ofEpochMilli(new File(classuri.getPath()).lastModified());
			}
		} catch (URISyntaxException e) {
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}
