package com.giangnvt.j2e.grammar;

import com.giangnvt.j2e.grammar.mecab.AbstractMecabClient;
import com.giangnvt.j2e.grammar.mecab.MacMecabClient;
import com.giangnvt.j2e.grammar.mecab.MecabAnalyseResult;
import com.giangnvt.j2e.grammar.mecab.WinMecabClient;
import com.j2e.JdbcSQLiteSupporter;
import com.j2e.common.utility.JavaUtil;
import com.j2e.common.utility.japanese.Wanakana;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Step02ExampleAnalyzer {
    public static final Pattern GRAMMAR_ROOT_FOLDER_PATTERN = Pattern.compile("jlpt-n(\\d+)-grammar-list");
    JdbcSQLiteSupporter connection = null;
    AbstractMecabClient mecabClient = null;

    public static void main(String[] args) throws Exception {
        Step02ExampleAnalyzer exampleInputer = new Step02ExampleAnalyzer();
        //exampleInputer.generateExampleEnhanced();
        exampleInputer.insertGrammarToDB();
        //exampleInputer.generateGrammarInfoFromFolderName(true);

        //exampleInputer.test("傘を《忘れ》ちゃったから、止むまで雨宿りしてるの。");
//      exampleInputer.test("トモヤが、顔を耳まで真赤に染めて立ち上がった。");
//	　　　exampleInputer.test("家に入っ#%た瞬間%#に、あの犬が泣く。");

    }
    
    public void test (String line) {
        MecabAnalyseResult analyseResult = mecabClient.analyseInputSentence(line);
        System.out.println(analyseResult.toString());
    }

    public Step02ExampleAnalyzer() {
        connection = new JdbcSQLiteSupporter(getDbPath());

        if (JavaUtil.isWin()) {
            mecabClient = new WinMecabClient(connection);
        } else {
            mecabClient = new MacMecabClient(connection);
        }
    }


    /**
     * Step 02.a<br/>
     * Generate rough grammar info from folder name. 
     */
    public void generateGrammarInfoFromFolderName(boolean override) throws Exception {
        final Pattern grammarDirNamePattern = Pattern.compile("\\d{3,3}_(.*)\\((.*)\\)");
        final Pattern grammarDirNamePattern2 = Pattern.compile("\\d{3,3}_(.*)");

        final LooperOption looperOption = new LooperOption();
        looperOption.overrideFlg = true;

        FolderLooperCallback callback = new FolderLooperCallback() {

            public void onFolderFound(File currentFolder) throws Exception {
                if (!looperOption.overrideFlg && new File(currentFolder, "general.txt").exists()) {
                    System.out.println("--- Skipped: " + currentFolder.getAbsolutePath());
                    return;
                } else {
                    System.out.println("+++ Processing: " + currentFolder.getAbsolutePath());
                }
                new File(currentFolder, "general.txt").delete();

                Matcher grammarDirNameMathcer = grammarDirNamePattern.matcher(currentFolder.getName());
                String grammarWord1 = currentFolder.getName();
                String grammarWord2 = "";
                if (grammarDirNameMathcer.matches()) {
                    grammarWord1 = grammarDirNameMathcer.group(1);
                    grammarWord2 = grammarDirNameMathcer.group(2); 
                } else {
                    Matcher grammarDirNameMathcer2 = grammarDirNamePattern2.matcher(currentFolder.getName());
                    if (grammarDirNameMathcer2.matches()) {
                        grammarWord1 = grammarDirNameMathcer2.group(1);
                    } else {
                        System.out.println("XXX: " + grammarWord1);
                    }
                }
                grammarWord1 = JavaUtil.trimFull(grammarWord1);
                grammarWord2 = JavaUtil.trimFull(grammarWord2);
                grammarWord2 = Wanakana._romajiToHiragana(grammarWord2, null).replaceAll("\\s", "");

                FileWriter fw = new FileWriter(new File(currentFolder, "general.txt"));
                fw.write(grammarWord1);
                fw.write("\n");
                fw.write(grammarWord2);
                fw.close();
            }
        };

        FolderLooper folderLooper = new FolderLooper(callback, looperOption);
        folderLooper.run();
    }

    /**
     * Step 02.b<br/>
     * Generate metadata for example sentences based on example.txt files.<br/>
     * Input: 
     * <ul>
     * <li>example.txt</li> 
     * <li>general_man.txt</li>
     * </ul> 
     * Output: 
     * <ul>
     * <li>example_enhanced.txt</li>
     * <li>example_enhanced_w.txt (optional)</li>
     * </ul> 
     * 
     */
    public void generateExampleEnhanced() throws Exception {
        final LooperOption looperOption = new LooperOption();
        looperOption.overrideFlg = true;
        looperOption.parentFilterPattern = Pattern.compile("jlpt-n(\\d+)-grammar-list");
        
        // Reset previous created data (if needed)
        if (looperOption.overrideFlg) {
            resetDb();
            
            FolderLooperCallback resetCallback = new FolderLooperCallback() {
                public void onFolderFound(File folder) throws Exception {
                    new File(folder, "example_enhanced.txt").delete();
                    new File(folder, "example_enhanced_w.txt").delete();
                }
            };
            FolderLooper resetLooper = new FolderLooper(resetCallback, looperOption);
            resetLooper.run();
        }
        
        // Loop and create new data
        FolderLooperCallback callback = new FolderLooperCallback() {

            public void onFolderFound(File dirLv2) throws Exception {
                if (!looperOption.overrideFlg && new File(dirLv2, "example_enhanced.txt").exists()) {
                    System.out.println("--- Skipped: " + dirLv2.getAbsolutePath());
                    return;
                } else {
                    System.out.println("+++ Processing: " + dirLv2.getAbsolutePath());
                }

                File[] txtFiles = dirLv2.listFiles();

                List<String[]> examplePairList = new ArrayList<String[]>();
                String[] examplePair = new String[2];

                List<String> wordVariants = null;
                for (File fileLv3 : txtFiles) {
                    if (fileLv3.isFile() && fileLv3.getName().equals("general_man.txt")) {
                        String[] exampleLines = readGeneralTxtFile(fileLv3);
                        wordVariants = getWordVariants(exampleLines[0], exampleLines[1]);
                        break;
                    }
                }
                
                for (File fileLv3 : txtFiles) {
                    if (fileLv3.getName().equals("example.txt")) {
                        BufferedReader br = null;
                        BufferedWriter bw = null;
                        try {
                            bw = new BufferedWriter(new FileWriter(new File(dirLv2, "example_enhanced.txt")));
                            br = new BufferedReader(new FileReader(fileLv3));
                            String line;
                            boolean newExample = true;
                            boolean firstLine = true;
                            int addedLineCnt = 0;
                            while ((line = br.readLine()) != null) {
                                line = line.trim();
                                if (line.length() == 0) continue;

                                if (".................................".equals(line)) {
                                    if (addedLineCnt != 2) {
                                        throw new RuntimeException("Example line: " + addedLineCnt);
                                    }
                                    bw.newLine();
                                    bw.write(line);
                                    examplePairList.add(examplePair);
                                    newExample = true;
                                    examplePair = new String[2];
                                    addedLineCnt = 0;
                                } else {
                                    addedLineCnt++;
                                    if (newExample) {
                                        boolean foundGrammarKeyword = false;
                                        for (String variant : wordVariants) {
                                            if (line.contains(variant)) {
                                                line = line.replace(variant, "《" + variant + "》");
                                                foundGrammarKeyword = true;
                                                break;
                                            }
                                        }
                                        if (!foundGrammarKeyword) {
                                            new File(dirLv2, "example_enhanced_w.txt").createNewFile();
                                        }
                                        //examplePair[0] = line;
                                        newExample = false;
                                        
                                        MecabAnalyseResult mecabResult = mecabClient.analyseInputSentence(line);
                                        if (!firstLine) {
                                            bw.newLine();
                                        }
                                        // 1st line: JP sentence
                                        bw.write(mecabResult.getOutputSentence());
                                        
                                        // 2nd line: entryID
                                        bw.newLine();
                                        bw.write(JavaUtil.joinInteger(mecabResult.getEntryIdList(), ","));

                                        firstLine = false;
                                    } else {
                                        //examplePair[1] = line;
                                        // 3rd line: EN sentence
                                        bw.newLine();
                                        bw.write(line);
                                    }
                                }
                            }
                            if (addedLineCnt != 2) {
                                throw new RuntimeException("Example line: " + addedLineCnt);
                            } else {
                                examplePairList.add(examplePair);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            bw.close();
                            br.close();
                        }
                        
                        break;
                    }
                }
            }
        };
        FolderLooper folderLooper = new FolderLooper(callback, looperOption);
        folderLooper.run();
    }
    
    /**
     * Step 02.c<br/>
     * Base on rough grammar info generated on step 02.b, insert full data to DB. 
     * Input: 
     * <ul>
     * <li>example.txt</li> 
     * <li>general_man.txt</li>
     * </ul> 
     * Output: 
     * <ul>
     * <li>example_enhanced.txt</li>
     * <li>example_enhanced_w.txt (optional)</li>
     * </ul> 
     * 
     */
    public void insertGrammarToDB() throws Exception {
        final LooperOption looperOption = new LooperOption();
        looperOption.overrideFlg = true;
        looperOption.parentFilterPattern = Pattern.compile("jlpt-n(\\d+)-grammar-list");
        
        // Reset previous created data (if needed)
        if (looperOption.overrideFlg) {
            resetDb();
            
            FolderLooperCallback resetCallback = new FolderLooperCallback() {
                public void onFolderFound(File folder) throws Exception {
                    new File(folder, "DB.txt").delete();
                    new File(folder, "DB_w.txt").delete();
                }
            };
            FolderLooper resetLooper = new FolderLooper(resetCallback, looperOption);
            resetLooper.run();
        }
        
        // Loop and create new data
        FolderLooperCallback callback = new FolderLooperCallback() {
            
            public void onFolderFound(File curDir) throws Exception {
                if (!looperOption.overrideFlg && new File(curDir, "DB.txt").exists()) {
                    System.out.println("--- Skipped: " + curDir.getAbsolutePath());
                    return;
                } else {
                    System.out.println("+++ Processing: " + curDir.getAbsolutePath());
                }
                
                File[] txtFiles = curDir.listFiles();
                
                List<Example> exampleList = new ArrayList<Example>();
                Example example = new Example();
                
                for (File txtFile : txtFiles) {
                    if (txtFile.getName().equals("example_enhanced.txt")) {
                        BufferedReader br = null;
                        try {
                            br = new BufferedReader(new FileReader(txtFile));
                            String line;
                            int addedLineCnt = 0;
                            while ((line = br.readLine()) != null) {
                                line = line.trim();
                                
                                if (".................................".equals(line)) {
                                    if (addedLineCnt != 3) {
                                        throw new RuntimeException("Example line: " + addedLineCnt);
                                    }
                                    exampleList.add(example);
                                    example = new Example();
                                    addedLineCnt = 0;
                                } else {
                                    addedLineCnt++;
                                    // JP line
                                    if (addedLineCnt == 1) {
                                        example.jpSentence = line;
                                    
                                    // Entry ID line
                                    } else if (addedLineCnt == 2) {
                                        example.entryIdList = JavaUtil.stringToIntList(line, ",");
                                    // EN line
                                    } else {
                                        example.enSentence = line;
                                    }
                                }
                            }
                            if (addedLineCnt != 3) {
                                throw new RuntimeException("Example line: " + addedLineCnt);
                            } else {
                                exampleList.add(example);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            br.close();
                        }
                        
                        File parentFolder = curDir.getParentFile();
                        Matcher matcher = GRAMMAR_ROOT_FOLDER_PATTERN.matcher(parentFolder.getName());
                        int jlptLvl = 0;
                        if (matcher.matches()) {
                            jlptLvl = Integer.parseInt(matcher.group(1));
                        } else {
                            throw new RuntimeException("Unexpected folder name: " + parentFolder.getName());
                        }
                        
                        File meaningFile = new File(curDir, "meaning.txt");
                        String meaning = "";
                        if (meaningFile.exists()) {
                            meaning = JavaUtil.readWholeTextFile(meaningFile);
                        }
                        File formationFile = new File(curDir, "formation.txt");
                        String formation = "";
                        if (formationFile.exists()) {
                            formation = JavaUtil.readWholeTextFile(formationFile);
                        }
                        String[] generalLines = readGeneralTxtFile(new File(curDir, "general_man.txt"));
                        String word1 = generalLines[0];
                        String word2 = generalLines[1];
                        List<Integer> exampleIdList = insertExample(exampleList);
                        
                        insertGrammar(word1, word2, meaning, formation, jlptLvl, exampleIdList);
                        
                        new File(curDir, "DB.txt").createNewFile();
                        break;
                    }
                }
            }
        };
        FolderLooper folderLooper = new FolderLooper(callback, looperOption);
        folderLooper.run();
    }
    
    public void createIndexFile() {
        try {
            ResultSet resultSet = connection.executeSelectQuery(
                    "SELECT id, word1, word2 FROM grammar ORDER BY id ASC");
            while (resultSet.next()) {
                String id = resultSet.getString(1);
                String word1 = resultSet.getString(2);
                String word2 = resultSet.getString(3);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private List<String> getWordVariants(String word1, String word2) {
        List<String> variantList = new ArrayList<>();
        analyzeWord(word1, variantList);
        analyzeWord(word2, variantList);
        return variantList;
    }
    
    private void analyzeWord(String word, List<String> variantList) {
        if (word.length() > 0) {
            String[] tokens = word.split("\\{");
            for (String token : tokens) {
                if (token.contains("}")) {
                    String[] subTokens = token.split("\\}");
                    for (String subToken : subTokens) {
                        if (subToken.startsWith("\\")) {
                            variantList.add(subToken.substring(1));
                        } else if (subToken.startsWith("[")) {
                            
                        } else {
                            variantList.add(subToken);
                        }
                    }
                } else {
                    variantList.add(token);
                }
            }
        }
    }
    
    private String[] readGeneralTxtFile(File file) {
        String[] lines = new String[2];
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(file));
            String line;
            int idx = 0;
            while ((line = br.readLine()) != null) {
                lines[idx++] = line;
            }
            br.close();
            if (idx != 2) throw new Exception("Invalid number of line:" + idx);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return lines;
    }


    private String getDbPath() {
        if (JavaUtil.isWin()) {
            return "D:/20.GiangNVT/Private/Andori/_bk_andori/v_1.8.7/db.sqlite";
        } else {
            return "/Users/apple/Documents/workspace/AndroidStudio/_Midori_text_data/v_1.8.7/db.sqlite";
        }
    }

    private List<Integer> insertExample(List<Example> exampleList) {
        try {
            ResultSet rs = connection.executeSelectQuery("SELECT MAX(id) as maxId FROM grammar_example");
            int nextId = 0;
            if (rs.next()) {
                nextId = rs.getInt("maxId");
            }
            rs.close();

            List<Integer> idList = new ArrayList<Integer>();
            for (Example example : exampleList) {
                idList.add(++nextId);
                byte[] entryIdBytes = JavaUtil.parseIntegerListToBlob(example.entryIdList);
                connection.executeUpdate("INSERT INTO grammar_example ('id', 'ja', 'en', 'links')"
                        + " VALUES (?1, ?2, ?3, ?4)"
                        , new Object[]{nextId, example.jpSentence, example.enSentence, entryIdBytes});
            }
            connection.commitConnection();
            return idList;
        } catch (Exception e) {
            throw new RuntimeException("insertExample: error: " + e.getMessage(), e);
        } finally {
        }
    }

    
    private void insertGrammar(String word1, String word2, String meaning, String formation
            , int jlptLvl, List<Integer> exampleIdList) {
        try {
            byte[] entryIdBytes = null;
            if (exampleIdList != null && exampleIdList.size() > 0) {
                entryIdBytes = JavaUtil.parseIntegerListToBlob(exampleIdList);
            }

            connection.executeUpdate(
                    "INSERT INTO grammar ('word1', 'word2', 'id', 'jlpt_level', 'order_num', 'meaning'"
                    + ", 'formation', 'example_id')"
                    + " VALUES ("
                    + "?1"      //word1
                    + ", ?2"    //word2
                    + ", (SELECT COALESCE(MAX(id), 0) + 1 FROM grammar)"    //id
                    + ", ?3 "       //jlpt
                    + ", (SELECT COALESCE(MAX(order_num), 0) + 1 FROM grammar where jlpt_level=?3)" //order_num
                    + ", ?4"    //meaning
                    + ", ?5"    //formation
                    + ", ?6)"   //example_id
                    , new Object[]{word1, word2, jlptLvl, meaning, formation, entryIdBytes});
            connection.commitConnection();
        } catch (Exception e) {
            System.err.print("insertExample: error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void resetDb() {
        connection.deleteFromTable("grammar", null);
        connection.deleteFromTable("grammar_example", null);
    }


    public void editExampleRemoveRomajiReading() {
        String rootFolderStr = null;
        if (JavaUtil.isWin()) {
            rootFolderStr = "D:/20.GiangNVT/Private/Github/crawler4j-jlpt/crawler2/grammar/";
        } else {
            rootFolderStr = "/Users/apple/Documents/workspace/AndroidStudio/crawler4j-jlpt/crawler2/grammar/";
        }
        Pattern pattern = Pattern.compile("[^a-zA-Z0-9]*");

        File[] dirLv1s = new File(rootFolderStr).listFiles();
        for (File dirLv1 : dirLv1s) {
            if (dirLv1.isDirectory()) {
                File[] dirLv2s = dirLv1.listFiles();
                for (File dirLv2 : dirLv2s) {
                    if (dirLv2.isDirectory()) {
                        File[] fileLv3s = dirLv2.listFiles();
                        for (File fileLv3 : fileLv3s) {
                            if (fileLv3.isFile() && fileLv3.getName().matches("ex_\\d.txt")) {
                                fileLv3.delete();
                            } else if (fileLv3.isFile() && fileLv3.getName().matches("OK_we.txt")) {
                                try {
                                    BufferedReader br = new BufferedReader(new FileReader(new File(dirLv2, "example.txt")));
                                    StringBuilder writeSb = new StringBuilder();
                                    String line;
                                    boolean newExample = true;
                                    int addedLineCnt = 0;
                                    while ((line = br.readLine()) != null) {
                                        line = line.trim();
                                        if (line.length() == 0) continue;

                                        if (".................................".equals(line)) {
                                            if (addedLineCnt != 2) {
                                                new File(dirLv2, "ex_" + addedLineCnt + ".txt").createNewFile();
                                            }
                                            newExample = true;
                                            addedLineCnt = 0;
                                            writeSb.append(line + "\n");
                                        } else {
                                            if (newExample) {
                                                writeSb.append(line + "\n");
                                                addedLineCnt++;
                                                newExample = false;
                                            } else {
                                                String[] tokens = line.split("\\s+");
                                                boolean skipLine = true;
                                                for (int i = 0; i < tokens.length; i++) {
                                                    String hiragana = Wanakana._romajiToHiragana(tokens[i], null);
                                                    if (!pattern.matcher(hiragana).matches()) {
                                                        skipLine = false;
                                                        break;
                                                    }
                                                }
                                                if (!skipLine) {
                                                    writeSb.append(line + "\n");
                                                    addedLineCnt++;
                                                }
                                            }
                                        }
                                    }
                                    if (addedLineCnt != 2) {
                                        new File(dirLv2, "ex_" + addedLineCnt + ".txt").createNewFile();
                                    }
                                    br.close();

                                    File newFile = new File(dirLv2, "example.txt");
                                    BufferedWriter bw = new BufferedWriter(new FileWriter(newFile));
                                    bw.write(writeSb.toString());
                                    bw.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    class Example {
        public String jpSentence;
        public String enSentence;
        public List<Integer> entryIdList;
    }
    
    interface FolderLooperCallback {
        public void onFolderFound(File folder) throws Exception;
    }
    
    class LooperOption {
        public boolean overrideFlg = false;
        public Pattern parentFilterPattern = null;
    }
    
    class FolderLooper {
        FolderLooperCallback callback;
        LooperOption looperOption;
        
        public FolderLooper(FolderLooperCallback callback, LooperOption looperOption) {
            this.callback = callback;
            this.looperOption = looperOption;
        }

        public void run() throws Exception {
            String rootFolderStr = null;
            if (JavaUtil.isWin()) {
                rootFolderStr = "D:/20.GiangNVT/Private/Github/crawler4j-jlpt/crawler2/grammar/";
            } else {
                rootFolderStr = "/Users/apple/Documents/workspace/AndroidStudio/crawler4j-jlpt/crawler2/grammar/";
            }

            File[] dirLv1s = new File(rootFolderStr).listFiles();
            for (File dirLv1 : dirLv1s) {
                if (dirLv1.isDirectory()) {
                    if (looperOption.parentFilterPattern != null) {
                        Matcher matcher = looperOption.parentFilterPattern.matcher(dirLv1.getName());
                        if (!matcher.matches())
                            continue;
                    }
                    
                    File[] dirLv2s = dirLv1.listFiles();
                    for (File dirLv2 : dirLv2s) {
                        if (dirLv2.isDirectory()) {
                            callback.onFolderFound(dirLv2);
                        }
                    }
                }
            }
        }
    }
}