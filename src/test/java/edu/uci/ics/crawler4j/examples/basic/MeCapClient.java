package edu.uci.ics.crawler4j.examples.basic;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Arrays;

public class MeCapClient {
    private static final String[] WIN_RUNTIME = new String[] {"cmd.exe", "/C"};
    private static final String[] OS_LINUX_RUNTIME = new String[] {"/bin/bash", "-l", "-c"};

    private Process p2;
    private BufferedWriter writer;
    private BufferedReader inputReader;
    
    public static void main(String[] args) {
        MeCapClient client = new MeCapClient();
        client.analyseSentence("このプロジェクトのどんな点を見て、そんなに初期の段階からかかわることにされたのですか？");
    }
    
    /**
     * コンストラクタ
     */
    public MeCapClient() {
        p2 = executeCommand(new String[] {"\"C:/Program Files (x86)/MeCab/bin/mecab\" -N1"});
        inputReader = new BufferedReader(
                //new InputStreamReader(p2.getInputStream(), Charset.forName("Shift-JIS")));
                new InputStreamReader(p2.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(p2.getOutputStream()));
    }
    
    public void destroy() {
        if (p2 != null) {
            p2.destroy();
        }
    }
    
    public void analyseSentence(String sentence) {
        try {
            System.out.println("In: " + sentence);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println("Out: -----------------------------");
                        String _line = null;
                        while ((_line = inputReader.readLine()) != null) {
                            //p2.getInputStream().close();
                            //inputReader.close();
                            System.out.println(_line);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            
            writer.write(sentence);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static boolean isWin() {
        String osName = System.getProperty("os.name");
        if (osName.toUpperCase().contains("WINDOWS")) {
            return true;
        } else {
            return false;
        }
    }
    
    private static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    
    public static Process executeCommand(String[] commands) {
        try {
            String[] commandArr = null;
            if (isWin()) {
                commandArr = concat(WIN_RUNTIME, commands);
            } else {
                commandArr = concat(OS_LINUX_RUNTIME, commands);
            }
            ProcessBuilder pb = new ProcessBuilder(commandArr);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
}
