package edu.uci.ics.crawler4j.examples.basic;

import com.j2e.JdbcSQLiteSupporter;

import net.moraleboost.mecab.Lattice;
import net.moraleboost.mecab.Node;
import net.moraleboost.mecab.impl.StandardTagger;

import org.apache.commons.lang.StringUtils;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class MecabClient {

    public static void main(String[] args) {
//        String sentence = "そんなことは起きないでしょう。";
        //String sentence = "綺麗じゃなかった花又は美味しくないお菓子をたべたんですか？";
//        String sentence = "「最近毎日どっかで殺人事件起きてない？」「確しかに。物騒な世の中だね」";
        String sentence = "ガーリーは誰ですか";
        
        MecabClient mecabClient = new MecabClient();
        mecabClient.analyse(sentence);
    }
    
    private enum ParseStatus {
        Normal, VerbStarted, NounStarted, AdjectiveStarted
    }
    
    private ParseStatus parseStatus = ParseStatus.Normal;
    private boolean isEntryCloseWaiting = false;
    
    private JdbcSQLiteSupporter entrySearchCon = null;
    private JdbcSQLiteSupporter outputUpdateCon = null;
    
    private String previousPartOfSpeech = null;
    private String previousPosCat1 = null;
    private String previousPosCat2 = null;
    
    private static final String entryDbPath = "D:/20.GiangNVT/Private/Andori/_bk_andori/v_1.8.7/db_fortest.sqlite";
    private static final String outputDbPath = "";
    
    public MecabClient() {
        entrySearchCon = new JdbcSQLiteSupporter(entryDbPath);
        //outputUpdateCon = new JdbcSQLiteSupporter(outputDbPath);
    }
    
    public void analyse(String sentence) {
        StandardTagger tagger = new StandardTagger("N2");
        // バージョン文字列を取得
        System.out.println("MeCab version " + tagger.version());

        // Lattice（形態素解析に必要な実行時情報が格納されるオブジェクト）を構築
        Lattice lattice = tagger.createLattice();

        // 解析対象文字列をセット
        lattice.setSentence(sentence);

        // tagger.parse()を呼び出して、文字列を形態素解析する。
        tagger.parse(lattice);

        // 形態素解析結果を出力
        //System.out.println(lattice.toString());
        System.out.println(lattice.enumNBestAsString(100));

        StringBuilder resultSb = new StringBuilder();
        
        // 一つずつ形態素をたどりながら、表層形と素性を出力
        Node node = lattice.bosNode();
        while (node != null) {
            String surface = node.surface();
            String feature = node.feature();
            
            if (feature.startsWith("BOS/EOS")) {
                node = node.next();
                continue;
            }
            
            StringBuilder sb = new StringBuilder(surface);
            String[] features = feature.split(",");
            for (String string : features) {
                sb.append("\t").append(string);
            }
            System.out.println(sb.toString());
            
            analyseNode(node, resultSb);
            
            node = node.next();
        }
        
        System.out.println("==================================");
        System.out.println(resultSb.toString());
        System.out.println("==================================");

        // lattice, taggerを破壊
        lattice.destroy();
        tagger.destroy();
    }
    
    private static Map<String, Integer[]> PART_OF_SPEECH_MAP;
    {
        PART_OF_SPEECH_MAP = new HashMap<>();
        PART_OF_SPEECH_MAP.put("名詞", new Integer[] {1});
        PART_OF_SPEECH_MAP.put("連体詞", new Integer[] {1});   //（どんな）
        PART_OF_SPEECH_MAP.put("副詞", new Integer[] {1});    //（そんなに）
        PART_OF_SPEECH_MAP.put("形容詞", new Integer[] {1});
        PART_OF_SPEECH_MAP.put("動詞", new Integer[] {1});
        PART_OF_SPEECH_MAP.put("接続詞", new Integer[] {1});   //（又は）
        //助詞    （の）
        //助動詞   （です）
        //記号    （。）
        //連体詞   （この）
        //接頭詞   （お-湯）
    }
    
//    private static Map<String, Integer> POS_CAT1_MAP;
//    {
//        POS_CAT1_MAP = new HashMap<>();
//        POS_CAT1_MAP.put("固有名詞", 0);
//    }
    
    private void analyseNode(Node node, StringBuilder sb) {
        // 表層形
        String surface = node.surface();
        String feature = node.feature();
        if (StringUtils.isEmpty(surface) || StringUtils.isEmpty(feature)) {
            previousPartOfSpeech = null;
            previousPosCat1 = null;
            previousPosCat2 = null;
            return;
        }

        String[] featureTokens = feature.split(",");
        if (featureTokens.length != 9 && featureTokens.length != 7) {
            previousPartOfSpeech = null;
            previousPosCat1 = null;
            previousPosCat2 = null;
            return;
        }
        
        // 品詞
        String partOfSpeech = featureTokens[0]; 
        // 品詞細分類1
        String posCat1 = featureTokens[1]; 
        // 品詞細分類2
        String posCat2 = featureTokens[2]; 
        // 品詞細分類3
        String posCat3 = featureTokens[3]; 
        // 活用型
        String conjugationForm = featureTokens[4]; 
        // 活用形
        String conjugation = featureTokens[5]; 
        // 原形 
        String originalForm = ""; 
        // 読み
        String reading = ""; 
        // 発音
        String pronounce = "";
        if (featureTokens.length == 9) {
            originalForm = featureTokens[6];
            reading = featureTokens[7]; 
            pronounce = featureTokens[8];
        }
        
        if (PART_OF_SPEECH_MAP.containsKey(partOfSpeech)) {
            if (isEntryCloseWaiting) {
                sb.append("}");
            }
            isEntryCloseWaiting = false;
            
            
            int wordId = searchEntryByWord2(originalForm);
            boolean isEntry = wordId > 0;
            if (isEntry) {
                sb.append("{");
            }
            
            if ("動詞".equals(partOfSpeech)) {
                // For token like "います", not set as an entry
                if ("非自立".equals(posCat1)) {
                    parseStatus = ParseStatus.Normal;
                } else {
                    parseStatus = ParseStatus.VerbStarted;
                }
            } else if ("形容詞".equals(partOfSpeech)) {
                parseStatus = ParseStatus.AdjectiveStarted;
            } else if ("名詞".equals(partOfSpeech)) {
                // For な-adjective, mecab return as a noun
                if ("形容動詞語幹".equals(posCat1)) {
                    parseStatus = ParseStatus.AdjectiveStarted;
                } else {
                    parseStatus = ParseStatus.NounStarted;
                }
            }
            
            appendRuby(sb, surface, reading);
            
            if (isEntry) {
                isEntryCloseWaiting = true;
                //sb.append("}");
            }
        } else {
            if ("助詞".equals(partOfSpeech)
                    || "助動詞".equals(partOfSpeech)) {
                if (parseStatus == ParseStatus.VerbStarted
                        || parseStatus == ParseStatus.AdjectiveStarted) {
                    //TODO
                } else {
                    if (isEntryCloseWaiting) {
                        sb.append("}");
                    }
                    isEntryCloseWaiting = false;
                }
                
                sb.append(surface);
            } else {
                parseStatus = ParseStatus.Normal;
                if (isEntryCloseWaiting) {
                    sb.append("}");
                }
                sb.append(surface);
                isEntryCloseWaiting = false;
            }
        }
        
        previousPartOfSpeech = partOfSpeech;
        previousPosCat1 = posCat1;
        previousPosCat2 = posCat2;
    }
    
    private void appendRuby(StringBuilder sb, String surface, String reading) {
        //TODO
        sb.append(surface);
    }
    

  public int searchEntryByWord2(String originalForm) {
      int id = -1;
      try {
          ResultSet rs = entrySearchCon.executeSelectQuery(
                  String.format("SELECT id FROM entry WHERE word1='%s' OR word1 LIKE '%s{%%' OR word1 LIKE '%%{%s'", originalForm, originalForm, originalForm));

          if (rs.next()) {
              id = rs.getInt("id");
          }
          rs.close();
      } catch (Exception e) {
          System.err.print("IndexCreator [searchDb] error: " + e.getMessage());
          e.printStackTrace();
      }
      return id;
  }
}