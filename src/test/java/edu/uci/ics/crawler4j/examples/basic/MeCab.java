package edu.uci.ics.crawler4j.examples.basic;

import com.j2e.common.utility.JavaUtil;

public class MeCab {
	

    public static void main(String[] args) {
//        String sentence = "そんなことは起きないでしょう。";
        String sentence = "綺麗じゃなかった花又は美味しくないお菓子をたべたんですか？";
//        String sentence = "「確しかに。物騒な世の中だね」";
//        String sentence = "最近";
//        String sentence = "ガーリーは誰ですか";
        
        AbstractMecabClient mecabClient = null;
        if (JavaUtil.isWin()) {
        	mecabClient = new WinMecabClient();
        } else {
        	mecabClient = new MacMecabClient();
        }
        MecabAnalyseResult analyseResult = mecabClient.analyseInputSentence(sentence);
        System.out.println(analyseResult.toString());
    }
}