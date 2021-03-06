package hudson.plugins.perforce;

import java.io.*;
import java.util.Map;
import java.util.Set;

import com.tek42.perforce.PerforceException;
import com.tek42.perforce.process.Executor;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.Hudson;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.util.StreamTaskListener;

/**
 * Implementation of the P4Java Executor interface that provides support for
 * remotely executing Perforce commands.
 * <p/>
 * User contract: Use this class only once to execute a command. ,to execute
 * another command, spawn another Exector using the Exector Factory
 *
 * @author Victor Szoltysek
 */
public class HudsonP4DefaultExecutor implements HudsonP4Executor {

    private BufferedReader reader;
    private BufferedWriter writer;

    private Launcher hudsonLauncher;
    private String[] env;
    private FilePath filePath;

    /**
     * Constructor that takes Hudson specific details for launching the
     * Perforce commands.
     *
     * @param hudsonLauncher
     * @param envMap
     * @param filePath
     */
    HudsonP4DefaultExecutor(Launcher hudsonLauncher, Map<String, String> envMap, FilePath filePath) {
        this.hudsonLauncher = hudsonLauncher;
        this.env = convertEnvMaptoArray(envMap);
        this.filePath = filePath;
    }

    public void close() {
        // Need to close writer
        // (reader gets closed in HudsonPipedOutputStream)
        try {
            writer.close();
        } catch(IOException e) {
            // Do nothing
        }
    }

    public void exec(String[] cmd) throws PerforceException {
        try {
			// ensure we actually have a valid hudson launcher
			if (null == hudsonLauncher) {
				hudsonLauncher = Hudson.getInstance().createLauncher(new StreamTaskListener(System.out));
			}

            // hudsonOut->p4in->reader
            HudsonPipedOutputStream hudsonOut = new HudsonPipedOutputStream();
            FastPipedInputStream p4in = new FastPipedInputStream(hudsonOut);
            reader = new BufferedReader(new InputStreamReader(p4in));

            // hudsonIn<-p4Out<-writer
            FastPipedInputStream hudsonIn = new FastPipedInputStream();
            FastPipedOutputStream p4out = new FastPipedOutputStream(hudsonIn);
            writer = new BufferedWriter(new OutputStreamWriter(p4out));
            
            Proc process = hudsonLauncher.launch().cmds(cmd).envs(env).stdin(hudsonIn).stdout(hudsonOut).pwd(filePath).start();
            
            // Required to close hudsonOut stream
            hudsonOut.closeOnProcess(process);

        } catch(IOException e) {
            //try to close all the pipes before throwing an exception
            closeBuffers();
            
            throw new PerforceException("Could not run perforce command.", e);
        }
    }

    /**
     * Function for converting map of environment variables to a String
     * array as hudson does not provide a launcher method that takes
     * <p/>
     * (1) Environment Map (2) InputStream (3) OutputStream
     * <p> .. at the same time
     *
     * @param envMap
     *
     * @return
     */
    private String[] convertEnvMaptoArray(Map<String, String> envMap) {
        Set<String> keySet = envMap.keySet();
        String[] keys = keySet.toArray(new String[0]);

        String[] result = new String[keys.length];
        for (int i = 0; i < keys.length; i++)
            result[i] = keys[i] + "=" + envMap.get(keys[i]);

        return result;
    }

    public BufferedReader getReader() {
        return reader;
    }

    public BufferedWriter getWriter() {
        return writer;
    }

    private void closeBuffers(){
        try {
            reader.close();
        } catch(IOException ignoredException) {};
        try {
            writer.close();
        } catch(IOException ignoredException) {};
    }

}
