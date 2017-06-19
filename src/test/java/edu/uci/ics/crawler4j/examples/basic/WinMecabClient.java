package edu.uci.ics.crawler4j.examples.basic;

import java.util.ArrayList;
import java.util.List;

import com.j2e.JdbcSQLiteSupporter;

import net.moraleboost.mecab.Lattice;
import net.moraleboost.mecab.Node;
import net.moraleboost.mecab.impl.StandardTagger;
import edu.uci.ics.crawler4j.examples.basic.AbstractMecabClient;;

public class WinMecabClient extends AbstractMecabClient {
	   
	@Override
	protected String getEntryDbPath() {
		return "D:/20.GiangNVT/Private/Andori/_bk_andori/v_1.8.7/db.sqlite";
	}
	
	public WinMecabClient(JdbcSQLiteSupporter connection) {
		super(connection);
	}
	
    protected List<String[]> callMecab(String sentence) {
    	List<String[]> outputList = new ArrayList<String[]>();
    	
        StandardTagger tagger = new StandardTagger("");
        // Lattice（形態素解析に必要な実行時情報が格納されるオブジェクト）を構築
        Lattice lattice = tagger.createLattice();
        // 解析対象文字列をセット
        lattice.setSentence(sentence);

        // tagger.parse()を呼び出して、文字列を形態素解析する。
        tagger.parse(lattice);

        // 形態素解析結果を出力
        //System.out.println(lattice.toString());

        // 一つずつ形態素をたどりながら、表層形と素性を出力
        Node node = lattice.bosNode();
        while (node != null) {
            String surface = node.surface();
            String feature = node.feature();
            
            if (feature.startsWith("BOS/EOS")) {
                node = node.next();
                continue;
            }
            outputList.add(new String[] {surface, feature});
            node = node.next();
        }
        
        // lattice, taggerを破壊
        lattice.destroy();
        tagger.destroy();
        
        return outputList;
    }

}
