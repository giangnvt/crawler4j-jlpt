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

	static class Option {
		public Option(String rootDir, boolean rearrangeFlg, boolean overwriteFlg, boolean improveFlg) {
			super();
			this.rootDir = rootDir;
			this.rearrangeFlg = rearrangeFlg;
			this.overwriteFlg = overwriteFlg;
			this.improveFlg = improveFlg;
			
		}

		String rootDir;
		boolean rearrangeFlg = false;
		boolean overwriteFlg = false;
		boolean improveFlg = false;
	}
	
    
	public static void main (String[] args) throws Exception {
		//String rootFolderStr = "D:\\20.GiangNVT\\Private\\Andori\\_bk_andori\\crawler2\\";
		String rootFolderStr = "/Users/apple/Documents/workspace/AndroidStudio/crawler4j-jlpt/crawler2/";
		Option option = new Option(rootFolderStr, false, false, true);
		
		HtmlAnalyzer analyzer = new HtmlAnalyzer();
		analyzer.execute(option);
		System.out.println("Done!!!");
	}
	
	
	public void execute (Option option) throws Exception {
		String rootDirStr = option.rootDir;
		rootDirStr += "grammar/";
		File rootDir = new File(rootDirStr);
		
		boolean rearrangeFlg = option.rearrangeFlg;
		boolean overwriteFlg = option.overwriteFlg;

		if (rearrangeFlg) {
		    File[] dirLv1s = rootDir.listFiles();
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
		
		File[] dirLv1s = rootDir.listFiles();
        for (File dirLv1 : dirLv1s) {
            if (dirLv1.isDirectory()) {
                File[] dirLv2s = dirLv1.listFiles();
                for (File dirLv2 : dirLv2s) {
                    if (dirLv2.isDirectory()) {
                        if ("_X".equals(dirLv2.getName())) continue;
                        
                        File[] fileLv3s = dirLv2.listFiles();
                        //if (fileLv3s.length > 1 && !overwriteFlg) continue;
                        for (File fileLv3 : fileLv3s) {
                            if (fileLv3.isFile() && fileLv3.getName().endsWith(".html")) {
                                analyze(fileLv3, dirLv2, option); 
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

	private boolean shouldSkipFolder(File folder) {
		if (folder == null) return true;
		File[] files = folder.listFiles();
		for (File file : files) {
			if (file.getName().matches("OK.*txt")) {
				return true;
			}
		}
		return false;
	}
	
	public void analyze (File inputHtml, File parentDir, Option option) throws Exception {
		if (shouldSkipFolder(parentDir)) {
			if (option.improveFlg) {
				
			}
			if (!option.overwriteFlg) {
				System.out.println("--- Skip :" + inputHtml.getAbsolutePath());
				return;
			}
	    } else {
	    	System.out.println("+++ Analyzing :" + inputHtml.getAbsolutePath());
	    }
	    
		File[] txtFiles = parentDir.listFiles();
		for (File txtFile : txtFiles) {
			if (txtFile.getName().matches(".*txt"))
				txtFile.delete();
		}
		
		Document doc = Jsoup.parse(inputHtml, "UTF-8", "http://japanesetest4you.com/");
		boolean okFlg = false;
		int exampleCount = 0;
		boolean exampleWarning = false;
		String exceptionMsg = "unknown";
		State state = State.Undefined;

		boolean meaningFound = false;
		boolean formationFound = false;
		boolean exampleFound = false;
		
		try {
		    Element content = doc.getElementById("content");
		    Elements entryTags = content.getElementsByClass("entry");
		    if (entryTags.size() != 1) {
		        throw new Exception("more than 1 entry tag");
		    }
		    
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
		                        throw new Exception ("unknow start section: [" + tagHeaderText + "]");
		                        
		                    case Meaning:
		                        if (meaningSb.length() > 0)
		                            meaningSb.append(NEWLINE);
		                        meaningSb.append(text);
	                            break;
	                            
		                    case Formation:
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
		            }
		        }
		    }
		    
		    if (meaningSb.length() > 0) {
		    	meaningFound = true;
		        meaningBw = new BufferedWriter(new FileWriter(new File(parentDir, "meaning.txt")));
		        meaningBw.write(meaningSb.toString());
		        meaningBw.close();
		    } else {
		    	meaningFound = false;
		        //throw new Exception("blank meaning");
		    }
		    
		    if (formationSb.length() > 0) {
		    	formationFound = true;
		        formationBw = new BufferedWriter(new FileWriter(new File(parentDir, "formation.txt")));
		        formationBw.write(formationSb.toString());
		        formationBw.close();
		    } else {
		    	formationFound = false;
		        //throw new Exception("blank formation");
		    }
		    
		    if (exampleSb.length() > 0) {
		    	exampleFound = true;
		        exampleBw = new BufferedWriter(new FileWriter(new File(parentDir, "example.txt")));
		        exampleBw.write(exampleSb.toString());
		        exampleBw.close();
		    } else {
		    	exampleFound = false;
		        //throw new Exception("blank example");
		    }
		    okFlg = meaningFound || formationFound || exampleFound;
		} catch (Exception ex) {
			okFlg = false;
		    exceptionMsg = ex.getMessage();
		}
		
		if (!okFlg) {
		    FileWriter fw = new FileWriter(new File(parentDir, "NG.txt"));
		    fw.write(exceptionMsg);
		    fw.close();
		} else {
			StringBuilder fileSb = new StringBuilder("OK");
			if (!meaningFound)
				fileSb.append("_m");
			if (!formationFound)
				fileSb.append("_f");
			if (!exampleFound)
				fileSb.append("_e");
		    if (exampleWarning)
		    	fileSb.append("_ew");
		    fileSb.append(".txt");
		    
		    new File(parentDir, fileSb.toString()).createNewFile();
		}
	}

}
