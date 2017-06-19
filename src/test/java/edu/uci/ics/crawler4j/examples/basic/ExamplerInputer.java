package edu.uci.ics.crawler4j.examples.basic;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.j2e.JdbcSQLiteSupporter;
import com.j2e.common.utility.JavaUtil;
import com.j2e.common.utility.japanese.Wanakana;

public class ExamplerInputer {
	JdbcSQLiteSupporter connection = null;
	AbstractMecabClient mecabClient = null;
	
	public static void main(String[] args) throws Exception {
		ExamplerInputer exampleInputer = new ExamplerInputer();
		//exampleInputer.insertExampleToDb(true);
		
		exampleInputer.generateGrammarInfoFromFolderName(true);
		
		//MecabAnalyseResult analyseResult = exampleInputer.parseSentenceWithMecab("傘を忘れちゃったから、止むまで雨宿りしてるの。");
//		MecabAnalyseResult analyseResult = exampleInputer.parseSentenceWithMecab("トモヤが、顔を耳まで真赤に染めて立ち上がった。");
//		MecabAnalyseResult analyseResult = exampleInputer.parseSentenceWithMecab("家に入っ#%た瞬間%#に、あの犬が泣く。");
//		System.out.println(analyseResult.toString());
		
	}
	
	public ExamplerInputer() {
		connection = new JdbcSQLiteSupporter(getDbPath());
		
        if (JavaUtil.isWin()) {
        	mecabClient = new WinMecabClient(connection);
        } else {
        	mecabClient = new MacMecabClient(connection);
        }
	}
	
	
	private String getDbPath() {
		if (JavaUtil.isWin()) {
			return "D:/20.GiangNVT/Private/Andori/_bk_andori/v_1.8.7/db.sqlite";
		} else {
			return "/Users/apple/Documents/workspace/AndroidStudio/_Midori_text_data/v_1.8.7/db.sqlite";
		}
	}
	
	   
    public void generateGrammarInfoFromFolderName(boolean override) throws Exception {
        String rootFolderStr = null;
        if (JavaUtil.isWin()) {
            rootFolderStr = "D:/20.GiangNVT/Private/Github/crawler4j-jlpt/crawler2/grammar/";
        } else {
            rootFolderStr = "/Users/apple/Documents/workspace/AndroidStudio/crawler4j-jlpt/crawler2/grammar/";
        }
        
        Pattern grammarDirNamePattern = Pattern.compile("\\d{3,3}_(.*)\\((.*)\\)");
        Pattern grammarDirNamePattern2 = Pattern.compile("\\d{3,3}_(.*)");
        
        File[] dirLv1s = new File(rootFolderStr).listFiles();
        for (File dirLv1 : dirLv1s) {
            if (dirLv1.isDirectory()) {
                File[] dirLv2s = dirLv1.listFiles();
                for (File dirLv2 : dirLv2s) {
                    if (dirLv2.isDirectory()) {
                        if (!override && new File(dirLv2, "general.txt").exists()) {
                            System.out.println("--- Skipped: " + dirLv2.getAbsolutePath());
                            continue;
                        } else {
                            System.out.println("+++ Processing: " + dirLv2.getAbsolutePath());
                        }
                        new File(dirLv2, "general.txt").delete();
                        
                        Matcher grammarDirNameMathcer = grammarDirNamePattern.matcher(dirLv2.getName());
                        String grammarWord1 = dirLv2.getName();
                        String grammarWord2 = "";
                        if (grammarDirNameMathcer.matches()) {
                            grammarWord1 = grammarDirNameMathcer.group(1);
                            grammarWord2 = grammarDirNameMathcer.group(2); 
                        } else {
                            Matcher grammarDirNameMathcer2 = grammarDirNamePattern2.matcher(dirLv2.getName());
                            if (grammarDirNameMathcer2.matches()) {
                                grammarWord1 = grammarDirNameMathcer2.group(1);
                            } else {
                                System.out.println("XXX: " + grammarWord1);
                            }
                        }
                        grammarWord1 = JavaUtil.trimFull(grammarWord1);
                        grammarWord2 = JavaUtil.trimFull(grammarWord2);
                        grammarWord2 = Wanakana._romajiToHiragana(grammarWord2, null).replaceAll("\\s", "");
                        
                        FileWriter fw = new FileWriter(new File(dirLv2, "general.txt"));
                        fw.write(grammarWord1);
                        fw.write("\n");
                        fw.write(grammarWord2);
                        fw.close();
                    }
                }
            }
        }
    }
	
	
	private List<Integer> insertExample(List<String[]> exampleList) {
		try {
			ResultSet rs = connection.executeSelectQuery("SELECT MAX(id) as maxId FROM grammar_example");
			int nextId = 0;
			if (rs.next()) {
				nextId = rs.getInt("maxId");
			}
			rs.close();

			List<Integer> idList = new ArrayList<Integer>();
			for (String[] pair : exampleList) {
				String jpSentence = pair[0];
				String enSentence = pair[1];
				MecabAnalyseResult mecabResult = parseSentenceWithMecab(jpSentence);
				byte[] entryIdBytes = JavaUtil.parseIntegerListToBlob(mecabResult.getEntryIdList());
				idList.add(++nextId);
				
				connection.executeUpdate("INSERT INTO grammar_example ('id', 'ja', 'en', 'links')"
						+ " VALUES (?1, ?2, ?3, ?4)"
						, new Object[]{nextId, mecabResult.getOutputSentence(), enSentence, entryIdBytes});
			}
			connection.commitConnection();
			return idList;
		} catch (Exception e) {
			System.err.print("insertExample: error: " + e.getMessage());
			e.printStackTrace();
			return null;
		} finally {
		}
	}
	
	
	private void insertGrammar(String name, String meaning, String formation, int jlptLvl, List<Integer> exampleIdList) {
		try {
			byte[] entryIdBytes = null;
			if (exampleIdList != null && exampleIdList.size() > 0) {
				entryIdBytes = JavaUtil.parseIntegerListToBlob(exampleIdList);
			}

			connection.executeUpdate("INSERT INTO grammar ('name', 'id', 'jlpt_level', 'order_num', 'meaning', 'formation', 'example_id')"
					+ " VALUES ("
					+ "?1"
					+ ", (SELECT COALESCE(MAX(id), 0) + 1 FROM grammar)"
					+ ", ?2 "
					+ ", (SELECT COALESCE(MAX(order_num), 0) + 1 FROM grammar where jlpt_level=?2)"
					+ ", ?3, ?4, ?5)"
					, new Object[]{name, jlptLvl, meaning, formation, entryIdBytes});
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
	
	public void insertExampleToDb(boolean override) throws Exception {
		if (override)
			resetDb();
		
		String rootFolderStr = null;
		if (JavaUtil.isWin()) {
			rootFolderStr = "D:/20.GiangNVT/Private/Github/crawler4j-jlpt/crawler2/grammar/";
		} else {
			rootFolderStr = "/Users/apple/Documents/workspace/AndroidStudio/crawler4j-jlpt/crawler2/grammar/";
		}
		
		Pattern categoryPattern = Pattern.compile("jlpt-n(\\d+)-grammar-list");
		Pattern grammarDirNamePattern = Pattern.compile("\\d{3,3}_(.*)\\(.*\\)");
		Pattern grammarDirNamePattern2 = Pattern.compile("\\d{3,3}_(.*)");
		
		int jlptLvl = 0;
		File[] dirLv1s = new File(rootFolderStr).listFiles();
        for (File dirLv1 : dirLv1s) {
            if (dirLv1.isDirectory()) {
            	Matcher matcher = categoryPattern.matcher(dirLv1.getName());
            	if (matcher.matches()) {
            		jlptLvl = Integer.parseInt(matcher.group(1));
            	} else {
            		throw new RuntimeException("Unexpected folder name: " + dirLv1.getName());
            	}
            	
                File[] dirLv2s = dirLv1.listFiles();
                for (File dirLv2 : dirLv2s) {
                    if (dirLv2.isDirectory()) {
                    	if (!override && new File(dirLv2, "DB.txt").exists()) {
                    		System.out.println("--- Skipped: " + dirLv2.getAbsolutePath());
                    		continue;
                    	} else {
                    		System.out.println("+++ Processing: " + dirLv2.getAbsolutePath());
                    	}
                    	new File(dirLv2, "DB.txt").delete();
                    	new File(dirLv2, "DB_w.txt").delete();
                    	
                        File[] fileLv3s = dirLv2.listFiles();
                        Matcher grammarDirNameMathcer = grammarDirNamePattern.matcher(dirLv2.getName());
                        String grammarName = dirLv2.getName();
                        if (grammarDirNameMathcer.matches()) {
                        	grammarName = grammarDirNameMathcer.group(1);
                        } else {
                        	Matcher grammarDirNameMathcer2 = grammarDirNamePattern2.matcher(dirLv2.getName());
                        	if (grammarDirNameMathcer2.matches()) {
                            	grammarName = grammarDirNameMathcer2.group(1);
                            } else {
                            	System.out.println("XXX: " + grammarName);
                            }
                        }
                        grammarName = JavaUtil.trimFull(grammarName);
                        
                        String meaning = null;
                        String formation = null;
                        List<String[]> examplePairList = new ArrayList<String[]>();
                        String[] examplePair = new String[2];
                        
                        for (File fileLv3 : fileLv3s) {
                            if (fileLv3.isFile() && fileLv3.getName().equals("meaning.txt")) {
                            	meaning = JavaUtil.readWholeTextFile(new FileInputStream(fileLv3));
                            } else if (fileLv3.isFile() && fileLv3.getName().equals("formation.txt")) {
                            	formation = JavaUtil.readWholeTextFile(new FileInputStream(fileLv3));
                            } else if (fileLv3.isFile() && fileLv3.getName().equals("example.txt")) {
                            	BufferedReader br = null;
                                try {
									br = new BufferedReader(new FileReader(fileLv3));
									String line;
									boolean newExample = true;
									int addedLineCnt = 0;
									while ((line = br.readLine()) != null) {
										line = line.trim();
										if (line.length() == 0) continue;
										
										if (".................................".equals(line)) {
											if (addedLineCnt != 2) {
												throw new RuntimeException("Example line: " + addedLineCnt);
											}
											examplePairList.add(examplePair);
											newExample = true;
											examplePair = new String[2];
											addedLineCnt = 0;
										} else {
											addedLineCnt++;
											if (newExample) {
												if (line.contains(grammarName)) {
													line = line.replace(grammarName, "#%" + grammarName + "%#");
												} else {
													new File(dirLv2, "DB_w.txt").createNewFile();
												}
												examplePair[0] = line;
												newExample = false;
											} else {
												examplePair[1] = line;
											}
										}
									}
									if (addedLineCnt != 2) {
										throw new RuntimeException("Example line: " + addedLineCnt);
									} else {
										examplePairList.add(examplePair);
									}
									br.close();
								} catch (Exception e) {
									throw new RuntimeException(e);
								} finally {
									br.close();
								}
                            }
                        }
                        
                        List<Integer> exampleIdList = insertExample(examplePairList);
                        insertGrammar(grammarName, meaning, formation, jlptLvl, exampleIdList);
                        new File(dirLv2, "DB.txt").createNewFile();
                    }
                }
            }
        }
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

    public MecabAnalyseResult parseSentenceWithMecab(String sentence) {
//        String sentence = "そんなことは起きないでしょう。";
//        String sentence = "綺麗じゃなかった花又は美味しくないお菓子をたべたんですか？";
//        String sentence = "「確しかに。物騒な世の中だね」";
//        String sentence = "最近";
//        String sentence = "ガーリーは誰ですか";
        
        
        MecabAnalyseResult analyseResult = mecabClient.analyseInputSentence(sentence);
        //System.out.println(analyseResult.toString());
        return analyseResult;
    }
}