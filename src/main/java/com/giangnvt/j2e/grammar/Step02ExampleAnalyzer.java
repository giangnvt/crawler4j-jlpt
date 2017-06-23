package com.giangnvt.j2e.grammar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.giangnvt.j2e.grammar.mecab.AbstractMecabClient;
import com.giangnvt.j2e.grammar.mecab.MacMecabClient;
import com.giangnvt.j2e.grammar.mecab.MecabAnalyseResult;
import com.giangnvt.j2e.grammar.mecab.WinMecabClient;
import com.google.code.externalsorting.ExternalSort;
import com.j2e.IndexCreator;
import com.j2e.JdbcSQLiteSupporter;
import com.j2e.common.utility.JavaUtil;
import com.j2e.common.utility.japanese.Wanakana;

public class Step02ExampleAnalyzer {
    public static final Pattern GRAMMAR_ROOT_FOLDER_PATTERN = Pattern.compile("jlpt-n(\\d+)-grammar-list");
    JdbcSQLiteSupporter connection = null;
    AbstractMecabClient mecabClient = null;

    public static void main(String[] args) throws Exception {
        System.setProperty("line.separator", "\n");
        
        Step02ExampleAnalyzer exampleInputer = new Step02ExampleAnalyzer();
        
//        System.out.println("### generateExampleEnhancedLv1");
//        exampleInputer.generateExampleEnhancedLv1();
//        
//        System.out.println("### generateExampleEnhancedLv2");
//        exampleInputer.generateExampleEnhancedLv2();
        
        System.out.println("### insertGrammarToDB");
        exampleInputer.insertGrammarToDB();

        System.out.println("### createIndexFile");
        exampleInputer.createIndexFile(new File(getDataPath()));

        System.out.println("### createListFile");
        exampleInputer.createListFile(new File(getDataPath()));
        
        IndexCreator ic = new IndexCreator();
        int resultCnt2 = ic.executeGrammarToIndex(getDataPath() + "grammar.txt", getDataPath());
        System.out.println("executeGrammarToIndex done! Total found: " + resultCnt2);

        System.out.println("DONEEEEEEEEEEEEEEEEEEE!!!");

//        exampleInputer.test("たけしくんは飽き《っぽくて》何をやってもすぐやめてしまう。");
//        exampleInputer.test("ビールを飲んでいます《より》ミルクを飲むの方が健康に良いでしたよ。");
//        exampleInputer.test("KFCのチッキンが《一番》おいしいと思う。");
//        exampleInputer.test("１０００万円になりますのでご使用遠慮ください。");
//        exampleInputer.test("1000万円になりますのでご使用遠慮ください。");
        //exampleInputer.test("ただ人形を惜しんだが、《あえて》それを口に出しはしなかった。");
//        eampleInputer.test("分からないので");
//        exampleInputer.test("傘を《忘れ》ちゃったから、止むまで雨宿りしてるの。");
//      exampleInputer.test("トモヤが、顔を耳まで真赤に染めて立ち上がった。");
//	　　　exampleInputer.test("家に入っ#%た瞬間%#に、あの犬が泣く。");

    }
    
    public void test (String line) {
        MecabAnalyseResult analyseResult = mecabClient.analyseInputSentence(line, true);
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
     * <li>example_enhanced_lv1.txt</li>
     * <li>example_enhanced_lv1_w.txt (optional)</li>
     * </ul> 
     * 
     */
    public void generateExampleEnhancedLv1() throws Exception {
        final LooperOption looperOption = new LooperOption();
        looperOption.overrideFlg = true;
        looperOption.parentFilterPattern = Pattern.compile("jlpt-n(\\d+)-grammar-list");
        
        // Reset previous created data (if needed)
        if (looperOption.overrideFlg) {
            resetDb();
            
            FolderLooperCallback resetCallback = new FolderLooperCallback() {
                public void onFolderFound(File folder) throws Exception {
                    new File(folder, "example_enhanced.txt").delete();
                    new File(folder, "example_enhanced_lv1.txt").delete();
                    new File(folder, "example_enhanced_lv1_w.txt").delete();
                }
            };
            FolderLooper resetLooper = new FolderLooper(resetCallback, looperOption);
            resetLooper.run();
        }
        
        // Loop and create new data
        FolderLooperCallback callback = new FolderLooperCallback() {

            public void onFolderFound(File dirLv2) throws Exception {
                if (!looperOption.overrideFlg && new File(dirLv2, "example_enhanced_lv1.txt").exists()) {
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
                            bw = new BufferedWriter(new FileWriter(new File(dirLv2, "example_enhanced_lv1.txt")));
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
                                    bw.write("\n");
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
                                            new File(dirLv2, "example_enhanced_lv1_w.txt").createNewFile();
                                        }
                                        newExample = false;
                                        
                                        if (!firstLine) {
                                            bw.write("\n");
                                        }
                                        // 1st line: JP sentence
                                        bw.write(line);
                                        firstLine = false;
                                    } else {
                                        // 2nd line: EN sentence
                                        bw.write("\n");
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
     * Step 02.b<br/>
     * Generate metadata for example sentences based on example.txt files.<br/>
     * Input: 
     * <ul>
     * <li>example_enhanced_lv1.txt</li> 
     * </ul> 
     * Output: 
     * <ul>
     * <li>example_enhanced_lv2.txt</li>
     * </ul> 
     * 
     */
    public void generateExampleEnhancedLv2() throws Exception {
    	final LooperOption looperOption = new LooperOption();
    	looperOption.overrideFlg = true;
    	looperOption.parentFilterPattern = Pattern.compile("jlpt-n(\\d+)-grammar-list");
    	
    	// Reset previous created data (if needed)
    	if (looperOption.overrideFlg) {
    		resetDb();
    		
    		FolderLooperCallback resetCallback = new FolderLooperCallback() {
    			public void onFolderFound(File folder) throws Exception {
    				new File(folder, "example_enhanced_lv2.txt").delete();
    			}
    		};
    		FolderLooper resetLooper = new FolderLooper(resetCallback, looperOption);
    		resetLooper.run();
    	}
    	
    	// Loop and create new data
    	FolderLooperCallback callback = new FolderLooperCallback() {
    		
    		public void onFolderFound(File dirLv2) throws Exception {
    			if (!looperOption.overrideFlg && new File(dirLv2, "example_enhanced_lv2.txt").exists()) {
    				System.out.println("--- Skipped: " + dirLv2.getAbsolutePath());
    				return;
    			} else {
    				System.out.println("+++ Processing: " + dirLv2.getAbsolutePath());
    			}
    			
    			File[] txtFiles = dirLv2.listFiles();
    			
    			List<String[]> examplePairList = new ArrayList<String[]>();
    			String[] examplePair = new String[2];
    			
    			for (File fileLv3 : txtFiles) {
    				if (fileLv3.getName().equals("example_enhanced_lv1.txt")) {
    					BufferedReader br = null;
    					BufferedWriter bw = null;
    					try {
    						bw = new BufferedWriter(new FileWriter(new File(dirLv2, "example_enhanced_lv2.txt")));
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
    								bw.write("\n");
    								bw.write(line);
    								examplePairList.add(examplePair);
    								newExample = true;
    								examplePair = new String[2];
    								addedLineCnt = 0;
    							} else {
    								addedLineCnt++;
    								if (newExample) {
    									newExample = false;
    									
    									MecabAnalyseResult mecabResult = mecabClient.analyseInputSentence(line, false);
    									if (!firstLine) {
    										bw.write("\n");
    									}
    									// 1st line: JP sentence
    									bw.write(mecabResult.getOutputSentence());
    									
    									// 2nd line: entryID
    									bw.write("\n");
    									StringBuilder entryIdSb = new StringBuilder();
    									if (mecabResult.getEntryIdList() != null) {
    										for (Integer[] entryIds : (List<Integer[]>) mecabResult.getEntryIdList()) {
    											if (entryIdSb.length() > 0)
    												entryIdSb.append("#");
    											for (int i = 0; i < entryIds.length; i++) {
    												if (i > 0) entryIdSb.append(",");
    												entryIdSb.append(entryIds[i]);
    											}
    										}
    									}
    									
    									bw.write(entryIdSb.toString());
    									
    									firstLine = false;
    								} else {
    									//examplePair[1] = line;
    									// 3rd line: EN sentence
    									bw.write("\n");
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
     * <li>example_enhanced_lv2.txt</li> 
     * <li>general_man.txt</li>
     * </ul> 
     */
    public void insertGrammarToDB() throws Exception {
        final LooperOption looperOption = new LooperOption();
        looperOption.overrideFlg = true;
        looperOption.parentFilterPattern = Pattern.compile("jlpt-n(\\d+)-grammar-list");
        final IdGenerator idGenerator = new IdGenerator();
        
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
                    if (txtFile.getName().equals("example_enhanced_lv2.txt")) {
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
                                        //example.entryIdList = JavaUtil.stringToIntList(line, ",");
                                    	String[] tokens = line.split("#");
                                    	List<Integer> tmpList = new ArrayList<Integer>();
                                    	boolean incompleteJp = false;
                                    	for(String token : tokens) {
                                    	    if (token.length() == 0) continue;
                                    		if (!token.contains(",")) {
                                    			tmpList.add(Integer.parseInt(token));
                                    		} else {
                                    			incompleteJp = true;
                                    		}
                                    	}
                                    	if (incompleteJp) {
                                    		example.jpSentence = example.jpSentence.replace("{?", "");
                                    		example.jpSentence = example.jpSentence.replace("?}", "");
                                    	}
                                    	example.entryIdList = tmpList;
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
                        
                        insertGrammar(idGenerator.nextId(), word1, word2, meaning, formation, jlptLvl, exampleIdList);
                        
                        new File(curDir, "DB.txt").createNewFile();
                        break;
                    }
                }
            }
        };
        FolderLooper folderLooper = new FolderLooper(callback, looperOption);
        folderLooper.run();
    }
    
    
    
    /**
     * Step 02.d<br/>
     * Generate index file (used for search function).<br/>
     * Input:
     * <ul>
     * <li>Database</li>
     * </ul>
     * Output:
     * <ul>
     * <li>grammar.txt</li>
     * </ul>
     */
    public void createIndexFile(File dir) {
        try {
            ResultSet resultSet = connection.executeSelectQuery(
                    "SELECT id, word1, word2 FROM grammar ORDER BY id ASC");
            File rawFile = new File(dir, "grammar_raw.txt");
            BufferedWriter bw = new BufferedWriter(new FileWriter(rawFile));
            boolean firstRow = true;
            
            while (resultSet.next()) {
                String id = resultSet.getString(1);
                String word1 = resultSet.getString(2);
                String word2 = resultSet.getString(3);
                
                List<String[]> pairList = new ArrayList<String[]>();
                List<String> word1List = new ArrayList<String>();
                if (word1.length() > 0) {
                	String[] word1Tokens = word1.split("\\{");
                	for (String word1Token : word1Tokens) {
                		int idx;
                		if ((idx = word1Token.indexOf("}")) >= 0) {
                			word1List.add(word1Token.substring(0, idx));
                		} else {
                			word1List.add(word1Token);
                		}
                	}
                }
                String word1String = JavaUtil.joinString(word1List, "{");
                
                String[] word2Tokens = word2.split("\\{");
                for (String word2Token : word2Tokens) {
                	int idx;
                	if ((idx = word2Token.indexOf("}")) >= 0) {
                		String[] word2SubTokens = word2Token.split("\\}");
                		StringBuilder word2TokenSb = new StringBuilder();
                		for (int k = 1; k < word2SubTokens.length; k++) {
                			if (word2SubTokens[k].startsWith("\\")) {
                				if (word2TokenSb.length() > 0) word2TokenSb.append("{");
                				word2TokenSb.append(word2SubTokens[k].substring(1));
                			}
                 		}
                		pairList.add(new String[] {word2SubTokens[0], word2TokenSb.toString()});
                	} else {
                		pairList.add(new String[] {word2Token, word1String});
                	}
                }
                
                for (String word1Token : word1List) {
                	pairList.add(new String[] {word1Token, word2Tokens[0]});
                }
                
                for (String[] arr : pairList) {
                	String line = arr[0]  + "\t" + arr[1] + "\t" + getEncodedId(id);
                	if (!firstRow)
                	    bw.write("\n");
                	firstRow = false;
                	bw.write(line);
                	System.out.println(line);
                }
            }
            bw.close();
            
            File sortedFile = new File(dir, "grammar.txt");
            ExternalSort.mergeSortedFiles(ExternalSort.sortInBatch(rawFile), sortedFile);
            
            List<Integer> sortedIdList = getSortedIdList(sortedFile);
            for (int i = 0; i < sortedIdList.size(); i++) {
            	connection.executeUpdate("UPDATE grammar SET order_num=? WHERE id=?", new Object[]{String.valueOf(i+1), sortedIdList.get(i)});
            }
            connection.commitConnection();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Step 02.e<br/>
     * Generate list file (used for list function).<br/>
     * Input:
     * <ul>
     * <li>Database</li>
     * </ul>
     * Output:
     * <ul>
     * <li>list_grammarN.txt.txt</li>
     * </ul>
     */
    public void createListFile(File dir) {
    	try {
    		for (int i = 1; i <= 5; i++) {
    			int[] sectionIdxArr = new int[] {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
    			ResultSet grammarResultSet = connection.executeSelectQuery(
    					String.format("SELECT id, word2 FROM grammar WHERE jlpt_level=%d ORDER BY order_num ASC", i));
    			int rowIdx = 0;
    			List<Integer> idList = new ArrayList<>();
    			while (grammarResultSet.next()) {
    				int id = grammarResultSet.getInt(1);
    				String word2 = grammarResultSet.getString(2);
    				String firstChar = word2.substring(0, 1);
    				int sectionIdx;

    				idList.add(id); 

    				if (KANA_MAP.containsKey(firstChar)) {
    					sectionIdx = KANA_MAP.get(firstChar) - 1;
    				} else {
    					sectionIdx = 0;	// all character other than kana is categorized into あ section
    				}
    				if (sectionIdxArr[sectionIdx] == -1) {
    					sectionIdxArr[sectionIdx] = rowIdx;
    				}
    				rowIdx++;
    			}

    			for (int j = 0; j < sectionIdxArr.length; j++) {
    				if (sectionIdxArr[j] == -1) {
    					if (j == 0) {
    						sectionIdxArr[j] = 0;
    					} else {
    						sectionIdxArr[j] = sectionIdxArr[j-1];
    					}
    				}
    				idList.add(j, sectionIdxArr[j]);
    			}

    			FileOutputStream fos = new FileOutputStream(new File(dir, String.format("list_jlpt_grammar%d.txt", i)));
    			byte[] bytes = JavaUtil.parseIntegerListToBlob(idList);
    			fos.write(bytes);
    			fos.close();
    		}
    	} catch (Exception ex) {
    		throw new RuntimeException(ex);
    	}
    }
    
    private static final Map<String, Integer> KANA_MAP = new HashMap<>();
    {
    	KANA_MAP.put("あ", 1);
    	KANA_MAP.put("い", 1);
    	KANA_MAP.put("う", 1);
    	KANA_MAP.put("え", 1);
    	KANA_MAP.put("お", 1);
    	
    	KANA_MAP.put("か", 2);
    	KANA_MAP.put("き", 2);
    	KANA_MAP.put("く", 2);
    	KANA_MAP.put("け", 2);
    	KANA_MAP.put("こ", 2);
    	KANA_MAP.put("が", 2);
    	KANA_MAP.put("ぎ", 2);
    	KANA_MAP.put("ぐ", 2);
    	KANA_MAP.put("げ", 2);
    	KANA_MAP.put("ご", 2);
    	
    	KANA_MAP.put("さ", 3);
    	KANA_MAP.put("し", 3);
    	KANA_MAP.put("す", 3);
    	KANA_MAP.put("せ", 3);
    	KANA_MAP.put("そ", 3);
    	KANA_MAP.put("ざ", 3);
    	KANA_MAP.put("じ", 3);
    	KANA_MAP.put("ず", 3);
    	KANA_MAP.put("ぜ", 3);
    	KANA_MAP.put("ぞ", 3);
    	
    	KANA_MAP.put("た", 4);
    	KANA_MAP.put("ち", 4);
    	KANA_MAP.put("つ", 4);
    	KANA_MAP.put("て", 4);
    	KANA_MAP.put("と", 4);
    	KANA_MAP.put("だ", 4);
    	KANA_MAP.put("ぢ", 4);
    	KANA_MAP.put("づ", 4);
    	KANA_MAP.put("で", 4);
    	KANA_MAP.put("ど", 4);

    	KANA_MAP.put("な", 5);
    	KANA_MAP.put("に", 5);
    	KANA_MAP.put("ぬ", 5);
    	KANA_MAP.put("ね", 5);
    	KANA_MAP.put("の", 5);

    	KANA_MAP.put("は", 6);
    	KANA_MAP.put("ひ", 6);
    	KANA_MAP.put("ふ", 6);
    	KANA_MAP.put("へ", 6);
    	KANA_MAP.put("ほ", 6);
    	KANA_MAP.put("ば", 6);
    	KANA_MAP.put("び", 6);
    	KANA_MAP.put("ぶ", 6);
    	KANA_MAP.put("べ", 6);
    	KANA_MAP.put("ぼ", 6);
    	KANA_MAP.put("ぱ", 6);
    	KANA_MAP.put("ぴ", 6);
    	KANA_MAP.put("ぷ", 6);
    	KANA_MAP.put("ぺ", 6);
    	KANA_MAP.put("ぽ", 6);

    	KANA_MAP.put("ま", 7);
    	KANA_MAP.put("み", 7);
    	KANA_MAP.put("む", 7);
    	KANA_MAP.put("め", 7);
    	KANA_MAP.put("も", 7);

    	KANA_MAP.put("や", 8);
    	KANA_MAP.put("ゆ", 8);
    	KANA_MAP.put("よ", 8);

    	KANA_MAP.put("ら", 9);
    	KANA_MAP.put("り", 9);
    	KANA_MAP.put("る", 9);
    	KANA_MAP.put("れ", 9);
    	KANA_MAP.put("ろ", 9);

    	KANA_MAP.put("わ", 10);
    	KANA_MAP.put("を", 10);

    	KANA_MAP.put("ん", 11);
    }
    
    private static List<Integer> getSortedIdList(File inputFile) {
    	try {
    		List<Integer> sortedIdList = new ArrayList<Integer>();
    		BufferedReader br = new BufferedReader(new FileReader(inputFile));
    		String line;
    		while ((line = br.readLine()) != null) {
    			String[] tokens = line.split("\t");
    			int id = JavaUtil.convertRowIndex(tokens[2]);
    			if (!sortedIdList.contains(id)) {
    				System.out.println("id: " + id + ", word1:" + tokens[0] + ", word2:" + tokens[1]);
    				sortedIdList.add(id);
    			}
    		}
    		return sortedIdList;
    	} catch (Exception ex) {
    		throw new RuntimeException(ex);
    	}
    }
    
    private static String getEncodedId(String id) {
    	String hex = String.format("%06X", Integer.parseInt(id));
    	StringBuilder sb = new StringBuilder();
    	sb.append(encodePart(hex.substring(0, 2)))
	    	.append(hex.substring(2, 3))
	    	.append(encodePart(hex.substring(3, 5)))
	    	.append(hex.substring(5, 6));
    	String finalHex = sb.toString();
    	StringBuilder finalSb = new StringBuilder();
    	finalSb.append(String.valueOf((char) Integer.parseInt(finalHex.substring(0, 2), 16)))
	    	.append(String.valueOf((char) Integer.parseInt(finalHex.substring(2, 4), 16)))
	    	.append(String.valueOf((char) Integer.parseInt(finalHex.substring(4, 6), 16)))
	    	.append(String.valueOf((char) Integer.parseInt(finalHex.substring(6), 16)));
    	return finalSb.toString();
    }
    
    private static String encodePart(String inputHex) {
    	int decVal = Integer.parseInt(inputHex, 16);
    	int remainder = decVal % 4;
    	return String.format("%02X", (decVal - remainder) / 4 + 48) + String.valueOf(remainder + 3);
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


    private static String getDbPath() {
//        if (JavaUtil.isWin()) {
//            return "D:/20.GiangNVT/Private/Andori/_bk_andori/v_1.8.7/db.sqlite";
//        } else {
//            return "/Users/apple/Documents/workspace/AndroidStudio/_Midori_text_data/v_1.8.7/db.sqlite";
//        }
        return getDataPath() + "db.sqlite";
    }
    
    
    private static String getDataPath() {
        if (JavaUtil.isWin()) {
            return "D:/20.GiangNVT/Private/Andori/_bk_andori/v_1.8.7/";
        } else {
            return "/Users/apple/Documents/workspace/AndroidStudio/_Midori_text_data/v_1.8.7/";
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

    
    private void insertGrammar(int id, String word1, String word2, String meaning, String formation
            , int jlptLvl, List<Integer> exampleIdList) {
        try {
            byte[] entryIdBytes = null;
            if (exampleIdList != null && exampleIdList.size() > 0) {
                entryIdBytes = JavaUtil.parseIntegerListToBlob(exampleIdList);
            }

            connection.executeUpdate(
                    "INSERT INTO grammar ('id', 'word1', 'word2', 'jlpt_level', 'order_num', 'meaning'"
                    + ", 'formation', 'example_id')"
                    + " VALUES ("
                    //+ " (SELECT COALESCE(MAX(id), 0) + 1 FROM grammar)"    //id
                    + "?1"      //id
                    + ", ?2"    //word1
                    + ", ?3"    //word2
                    + ", ?4 "       //jlpt
                    + ", (SELECT COALESCE(MAX(order_num), 0) + 1 FROM grammar where jlpt_level=?4)" //order_num
                    + ", ?5"    //meaning
                    + ", ?6"    //formation
                    + ", ?7)"   //example_id
                    , new Object[]{id, word1, word2, jlptLvl, meaning, formation, entryIdBytes});
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
    
    class IdGenerator {
        int seed = -1;

        public IdGenerator() {
            try {
                ResultSet rs = connection.executeSelectQuery("SELECT COALESCE(MAX(id), -1) FROM grammar");
                if (rs.next()) {
                    int tmp = rs.getInt(1);
                    if (tmp < 0) {
                        seed = tmp;
                    } else {
                        seed = (1000000 - tmp) / 10;
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            
        }
    	
    	public int nextId() {
    		seed++;
    		return 1000000 + seed * 10;
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