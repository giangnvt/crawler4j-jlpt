package edu.uci.ics.crawler4j.examples.basic;

import com.j2e.JdbcSQLiteSupporter;

import org.chasen.mecab.Node;
import org.chasen.mecab.Tagger;

import java.util.ArrayList;
import java.util.List;

public class MacMecabClient extends AbstractMecabClient {

	public MacMecabClient(JdbcSQLiteSupporter connection) {
		super(connection);
	}
	
	static {
		try {
		    System.loadLibrary("MeCab"); // MeCabを読み込む
		} catch (UnsatisfiedLinkError e) {
			// MeCabが読み込めなかったときの処理
			System.err.println("Cannot load the example native code.\nMake sure your LD_LIBRARY_PATH is defined.");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	@Override
	protected String getEntryDbPath() {
		return "/Users/apple/Documents/workspace/AndroidStudio/_Midori_text_data/v_1.8.7/db.sqlite";
	}

	@Override
	protected List<String[]> callMecab(String sentence) {
		List<String[]> outputList = new ArrayList<String[]>();
		
		Tagger tagger = new Tagger("-Ochasen");
		tagger.parse(sentence);
		Node node = tagger.parseToNode(sentence);
		
		while (node != null) {
            String surface = node.getSurface();
            String feature = node.getFeature();
            
            if (feature.startsWith("BOS/EOS")) {
                node = node.getNext();
                continue;
            }
            outputList.add(new String[] {surface, feature});
            node = node.getNext();
        }
		return outputList;
	}
}