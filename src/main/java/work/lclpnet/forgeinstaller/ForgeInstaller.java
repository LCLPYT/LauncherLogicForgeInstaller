package work.lclpnet.forgeinstaller;

import net.minecraftforge.installer.actions.ActionCanceledException;
import net.minecraftforge.installer.actions.ClientInstall;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Util;

import java.io.*;
import java.util.Locale;

public class ForgeInstaller {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("You need to pass exactly 2 arguments: <host> <port>");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        ProgressCallbackClient pgClient;
        pgClient = new ProgressCallbackClient(host, port);
        pgClient.setClientName("forgeInstaller");

		File tempDir = new File(System.getProperty("java.io.tmpdir"), "launcherlogic_forge");
		if(!tempDir.exists() && !tempDir.mkdirs()) throw new IllegalStateException("Could not create temp directory.");
		File logFile = new File(tempDir, String.format("llforgei_%s_stdout.txt", System.currentTimeMillis() / 1000L));
		File errFile = new File(tempDir, String.format("llforgei_%s_stderr.txt", System.currentTimeMillis() / 1000L));
		
		String logMsg = "Writing logs to: " + tempDir.getAbsolutePath();
		System.out.println(logMsg);
		pgClient.send(logMsg);
		
		FileOutputStream outOut, outErr;
		try {
			outOut = new FileOutputStream(logFile);
			outErr = new FileOutputStream(errFile);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			System.exit(1);
			return;
		}
        
		PrintStream origOut = System.out, origErr = System.err;
        LoggingPrintStream interceptor = new LoggingPrintStream(origOut, outOut, pgClient);
		System.setOut(interceptor);
        System.setErr(new LoggingPrintStream(origErr, outErr, pgClient));

        ClientInstall install = new ClientInstall(Util.loadInstallProfile(), ProgressCallback.withOutputs(interceptor));

        try {
            install.run(getMCDir(), input -> true);
        } catch (ActionCanceledException e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println(install.getSuccessMessage());

        if (ProgressCallbackClient.hasOpenSockets()) {
            try {
                Thread.sleep(1000L); // Delay to send potential pending tcp stuff (is this necessary?)
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ProgressCallbackClient.closeAllSockets();
        }
        
		try {
			outOut.close();
			outErr.close();
			System.setOut(origOut);
			System.setErr(origErr);
		} catch (IOException e) {
			e.printStackTrace(origErr);
		}
        
        System.exit(0);
    }

    public static File getMCDir() {
        String userHomeDir = System.getProperty("user.home", ".");
        String osType = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String mcDir = ".minecraft";
        if (osType.contains("win") && System.getenv("APPDATA") != null)
            return new File(System.getenv("APPDATA"), mcDir);
        else if (osType.contains("mac"))
            return new File(new File(new File(userHomeDir, "Library"), "Application Support"), "minecraft");
        return new File(userHomeDir, mcDir);
    }

}
