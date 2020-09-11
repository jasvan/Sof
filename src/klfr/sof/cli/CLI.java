package klfr.sof.cli;

import static klfr.sof.Interpreter.R;

// MOAR STANDARD LIBRARY
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.charset.Charset;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.*;

import klfr.sof.*;
import klfr.sof.ast.*;

public class CLI {

	public static final String INFO_STRING = String.format(R.getString("sof.cli.version"), Interpreter.VERSION,
			// awww yesss, the Java Time API 😋
			DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(buildTime().atZone(ZoneId.systemDefault())));

	private static final Logger log = Logger.getLogger(CLI.class.getCanonicalName());

	/**
	 * Parsed Code for the SOF preamble, which is responsible for the builtin
	 * function setup.
	 */
	private static Node preambleCode;
	private static String preambleCodeStr;

	public static void main(String[] args) throws InvocationTargetException, UnsupportedEncodingException, IOException {
		// setup console info logging
		LogManager.getLogManager().reset();
		final var bl = Logger.getLogger("");
		bl.setLevel(Level.FINEST);
		var ch = new ConsoleHandler();
		ch.setLevel(Level.OFF);
		bl.addHandler(ch);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			bl.info("SOF exiting.");
		}));
		//System.out.println(ResourceBundle.getBundle(Interpreter.MESSAGE_RESOURCE).getBaseBundleName());
		//bl.setResourceBundle(R);

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

				ch.setFormatter(new java.util.logging.Formatter() {
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
	 * @param codeStream  A reader that reads source code.
	 * @param interpreter The interpreter to use for the execution.
	 * @param io          The Input-Output interface that the full execution should
	 *                    use.
	 * @param flags       The flags passed to the program on the command line.
	 */
	public static void doFullExecution(Reader codeStream, Interpreter interpreter, IOInterface io, int flags)
			throws Exception {
		log.entering(CLI.class.getCanonicalName(), "doFullExecution");
		String code = "";
		try {
			StringWriter writer = new StringWriter();
			codeStream.transferTo(writer);
			code = writer.getBuffer().toString();
		} catch (IOException e) {
			throw new Exception("Unknown exception occurred during input reading.", e);
		}

		// if no preprocessing flag NOT set
		if ((flags & Options.NO_PREPROCESSOR) == 0)
			code = Preprocessor.preprocessCode(code);

		// parse
		Node ast = Parser.parse(code);
		if (io.debug)
			io.println(ast);

		// count nodes
		var nc = 0;
		if (io.debug || (flags & Options.PERFORMANCE) > 0) {
			for (@SuppressWarnings("unused")
			Node n : ast)
				++nc;
		}
		final var nodeCount = nc;

		// run preamble
		runPreamble(interpreter);

		// run code
		final var startTime = System.nanoTime();
		interpreter.run(ast, code);
		final var finishTime = System.nanoTime();
		final var execTimeµs = (finishTime - startTime) / 1_000d;

		// logging, performance
		log.info(String.format("Ran %d asserts.", interpreter.getAssertCount()));
		final Supplier<String> perfInfo = () -> String.format(
				"PERFORMANCE: Ran %9.3f ms (%4d nodes in %12.3f µs, avg %7.2f µs/node)", execTimeµs / 1_000d, nodeCount,
				execTimeµs, execTimeµs / nodeCount);
		if ((flags & Options.PERFORMANCE) > 0)
			io.println(perfInfo.get());
		log.exiting(CLI.class.getCanonicalName(), "doFullExecution");
	}

	/**
	 * Helper function that runs the preamble code on the interpreter, and may parse
	 * the preamble code if necessary.
	 * 
	 * @param interpreter The interpreter on which the preamble should be run.
	 */
	public static void runPreamble(Interpreter interpreter) {
		// parse preamble if not yet done
		if (preambleCode == null) {
			try {
				// get code
				final var pStream = CLI.class.getModule().getClassLoader().getResource("klfr/sof/lib/preamble.sof").openStream();
				final var pReader = new InputStreamReader(pStream, Charset.forName("utf-8"));
				final var pWriter = new StringWriter();
				pReader.transferTo(pWriter);
				preambleCodeStr = pWriter.toString();
				pWriter.close(); pStream.close();

				// parse code
				preambleCodeStr = Preprocessor.preprocessCode(preambleCodeStr);
				preambleCode = Parser.parse(preambleCodeStr);
			} catch (IOException | NullPointerException e) {
				interpreter.getIO().println(R.getString("sof.cli.nopreamble"));
				throw new RuntimeException(e);
			}
		}
		interpreter.run(preambleCode, preambleCodeStr);
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

/*  
The SOF programming language interpreter.
Copyright (C) 2019-2020  kleinesfilmröllchen

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
