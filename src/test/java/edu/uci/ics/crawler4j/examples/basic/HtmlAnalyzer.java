package edu.uci.ics.crawler4j.examples.basic;

import com.google.common.io.Files;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;


public class HtmlAnalyzer {
    public static final String NEWLINE = "\n";
    
	public static void main (String[] args) throws Exception {
		HtmlAnalyzer analyzer = new HtmlAnalyzer();
		String rootFolderStr = "D:\\20.GiangNVT\\Private\\Andori\\_bk_andori\\crawler2\\";
		rootFolderStr += "grammar\\";
		File rootFolder = new File(rootFolderStr);
		
		boolean rearrangeFlg = false;
		boolean overwriteFlg = true;

		if (rearrangeFlg) {
		    File[] dirLv1s = rootFolder.listFiles();
		    for (File dirLv1 : dirLv1s) {
		        if (dirLv1.isDirectory()) {
		            File[] fileLv2s = dirLv1.listFiles();
		            for (File fileLv2 : fileLv2s) {
		                if (fileLv2.isFile()) {
		                    File newDir = new File(fileLv2.getAbsolutePath().replace(".html", ""));
		                    newDir.mkdirs();
		                    File newFile = new File(newDir, fileLv2.getName());
		                    Files.move(fileLv2, newFile);
		                }
		            }
		        }
		    }
		}
		
		File[] dirLv1s = rootFolder.listFiles();
        for (File dirLv1 : dirLv1s) {
            if (dirLv1.isDirectory()) {
                File[] dirLv2s = dirLv1.listFiles();
                for (File dirLv2 : dirLv2s) {
                    if (dirLv2.isDirectory()) {
                        if ("_X".equals(dirLv2.getName())) continue;
                        
                        File[] fileLv3s = dirLv2.listFiles();
                        if (fileLv3s.length > 1 && !overwriteFlg) continue;
                        for (File fileLv3 : fileLv3s) {
                            if (fileLv3.isFile() && fileLv3.getName().endsWith(".html")) {
                                System.out.println("Processing: " + fileLv3.getAbsolutePath());
                                analyzer.analyze(fileLv3, dirLv2, overwriteFlg); 
                            }
                        }
                    }
                }
            }
        }
	}
	
	enum State {
	    Undefined, Meaning, Formation, Example
	}

	public void analyze (File inputHtml, File parentDir, boolean overwriteFlg) throws Exception {
	    if (!overwriteFlg && new File(parentDir, "OK^^.txt").exists()) {
	        return;
	    }
	    
		Document doc = Jsoup.parse(inputHtml, "UTF-8", "http://japanesetest4you.com/");
		boolean allOk = false;
		int exampleCount = 0;
		boolean exampleWarning = false;
		String ngReason = "unknown";
		State state = State.Undefined;

		try {
		    Element content = doc.getElementById("content");
		    Elements entryTags = content.getElementsByClass("entry");
		    if (entryTags.size() != 1) {
		        throw new Exception("more than 1 entry tag");
		    }
		    
//		    boolean meaningFound = false;
//		    boolean formationFound = false;
//		    boolean exampleFound = false;
		    
		    StringBuilder meaningSb = new StringBuilder();
		    StringBuilder formationSb = new StringBuilder();
		    StringBuilder exampleSb = new StringBuilder();
		    
		    BufferedWriter meaningBw = null;
		    BufferedWriter formationBw = null;
		    BufferedWriter exampleBw = null;

		    for (Element entryTag : entryTags) {
		        //Elements pTags = entryTag.getElementsByTag("p");
		        Elements childElements = entryTag.children();
		        for (Element pTag : childElements) {
		            
		            if (!"p".equals(pTag.tagName()))
		                continue;
		            
		            exampleCount = 0;
		            
		            // Image section
		            //TODO get the image
		            if (pTag.getElementsByTag("a").size() > 0) {
		                continue;
		            }
		            
		            //				Node parentNode = pTag.parentNode();
		            //				if (parentNode.attr("class").contains("ads"))
		            //					continue;

		            String tagHeaderText = pTag.getElementsByTag("strong").text().trim();
		            if (tagHeaderText.startsWith("Meaning:")) {
		                state = State.Meaning;
		            } else if (tagHeaderText.startsWith("Formation:")) {
		                state = State.Formation;
		            } else if (tagHeaderText.startsWith("Example")) {
		                state = State.Example;
		            }
		            
		            List<TextNode> textNodeList = pTag.textNodes();
		            if (textNodeList.size() == 0) continue;
		            
		            if (state == State.Example && exampleSb.length() > 0) {
		                // New example pairs
		                exampleSb.append(NEWLINE).append(NEWLINE);
		            }
		            
		            for (TextNode textNode : textNodeList) {
		                String text = textNode.text().trim();
		                
		                if (text.trim().length() == 0)
		                    continue;

		                switch (state) {
		                    case Undefined:
//		                        int meaningHeadIdx = text.indexOf("Meaning:");
//		                        if (meaningHeadIdx >= 0) {
//		                            state = State.Meaning;
//
//		                            String meaning = text.substring(meaningHeadIdx).trim();
//		                            if (meaning.length() > 0)
//		                                meaningSb.append(meaning);
//		                        } else {
//		                            throw new Exception ("no meaning");
//		                        }
		                        throw new Exception ("unknow start section: [" + tagHeaderText + "]");
		                        
		                    case Meaning:
//		                        int formationHeadIdx = text.indexOf("Formation:");
//		                        int exampleHeadIdx = text.indexOf("Example sentences:");
//	                            if (formationHeadIdx >= 0) {
//	                                state = State.Formation;
//	                                
//	                                String formation = text.substring(formationHeadIdx).trim();
//	                                if (formation.length() > 0)
//	                                    formationSb.append(formation);
//	                            } else if (exampleHeadIdx >= 0) {
//	                                throw new Exception("example before formation");
//	                            } else {
//	                                if (meaningSb.length() > 0)
//	                                    meaningSb.append(NEWLINE);
//	                                meaningSb.append(text);
//	                            }
		                        if (meaningSb.length() > 0)
		                            meaningSb.append(NEWLINE);
		                        meaningSb.append(text);
	                            break;
	                            
		                    case Formation:
//		                        exampleHeadIdx = text.indexOf("Example sentences:");
//                                if (exampleHeadIdx >= 0) {
//                                    state = State.Example;
//                                    
//                                    String example = text.substring(exampleHeadIdx).trim();
//                                    exampleSb.append(example);
//                                } else {
//                                    if (formationSb.length() > 0)
//                                        formationSb.append(NEWLINE);
//                                    formationSb.append(text);
//                                }
		                        if (formationSb.length() > 0)
		                            formationSb.append(NEWLINE);
		                        formationSb.append(text);
                                break;
		                    case Example:
		                        if (exampleSb.length() > 0)
		                            exampleSb.append(NEWLINE);
		                        exampleSb.append(text);
		                        exampleCount++;
		                        if (exampleCount > 2)
		                            exampleWarning = true;
		                        break;
		                }
//		                if (text.trim().length() > 0)
//		                    System.out.println("......" + text);
		            }
		        }
		    }
		    
		    if (meaningSb.length() > 0) {
		        meaningBw = new BufferedWriter(new FileWriter(new File(parentDir, "meaning.txt")));
		        meaningBw.write(meaningSb.toString());
		        meaningBw.close();
		    } else {
		        throw new Exception("blank meaning");
		    }
		    
		    if (formationSb.length() > 0) {
		        formationBw = new BufferedWriter(new FileWriter(new File(parentDir, "formation.txt")));
		        formationBw.write(formationSb.toString());
		        formationBw.close();
		    } else {
		        throw new Exception("blank formation");
		    }
		    
		    if (exampleSb.length() > 0) {
		        exampleBw = new BufferedWriter(new FileWriter(new File(parentDir, "example.txt")));
		        exampleBw.write(exampleSb.toString());
		        exampleBw.close();
		    } else {
		        throw new Exception("blank example");
		    }
		    allOk = true;
		} catch (Exception ex) {
		    ngReason = ex.getMessage();
		}
		
		if (!allOk) {
		    new File(parentDir, "OK^^.txt").delete();
		    new File(parentDir, "OKvv.txt").delete();
		    FileWriter fw = new FileWriter(new File(parentDir, "NG.txt"));
		    fw.write(ngReason);
		    fw.close();
		} else {
		    new File(parentDir, "NG.txt").delete();
		    if (exampleWarning)
		        new File(parentDir, "OKvv.txt").createNewFile();
		    else
		        new File(parentDir, "OK^^.txt").createNewFile();
		}
	}

}
