package edu.uci.ics.crawler4j.examples.basic;

import java.io.File;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.j2e.JdbcSQLiteSupporter;
import com.j2e.common.utility.japanese.Wanakana;

public abstract class AbstractMecabClient {

	protected enum ParseStatus {
		Normal, VerbStarted, NounStarted, AdjectiveStarted
	}

	protected JdbcSQLiteSupporter entrySearchCon = null;
	protected JdbcSQLiteSupporter outputUpdateCon = null;

	abstract protected List<String[]> callMecab(String sentence);

	abstract protected String getEntryDbPath();

	public AbstractMecabClient(JdbcSQLiteSupporter connection) {
		//entrySearchCon = new JdbcSQLiteSupporter(getEntryDbPath());
		//outputUpdateCon = new JdbcSQLiteSupporter(outputDbPath);
		entrySearchCon = connection;
	}

	public MecabAnalyseResult analyseInputSentence(String sentence) {
		MecabAnalyseResult result = new MecabAnalyseResult();
		List<String[]> mecabOutputList = null;
		try {
			result.setInputSentence(sentence);
			mecabOutputList = callMecab(sentence);
		} catch (Exception ex) {
			ex.printStackTrace();
			result.setResultCode(MecabAnalyseResult.RESULT_ERROR_MECAL_EXECUTE_ERROR);
			return result;
		}

		StringBuilder sb = new StringBuilder();
		for (String[] tokens : mecabOutputList) {
			for (String token : tokens) {
				sb.append(token).append("\t");
			}
			sb.append("\n");
		}
		System.out.println(sb.toString());

		analyseMecabOutput(mecabOutputList, result);
		return result;
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

	private void analyseMecabOutput(List<String[]> mecabOutputList, MecabAnalyseResult analyseResult) {
		StringBuilder sb = new StringBuilder();

		ParseStatus parseStatus = ParseStatus.Normal;
		boolean isEntryCloseWaiting = false;
		List<Integer> entryIdList = new ArrayList<Integer>();

		for (int i = 0; i < mecabOutputList.size(); i++) {
			// 表層形
			String surface = mecabOutputList.get(i)[0];
			String feature = mecabOutputList.get(i)[1];
			if (StringUtils.isEmpty(surface) || StringUtils.isEmpty(feature)) {
				continue;
			}

			String[] featureTokens = feature.split(",");
			if (featureTokens.length != 9 && featureTokens.length != 7) {
				analyseResult.setResultCode(MecabAnalyseResult.RESULT_ERROR_UNEXPECT_OUTPUT_FORMAT);
				analyseResult.setErrorLine(feature);
				return;
			}

			// 品詞
			String partOfSpeech = featureTokens[0]; 
			// 品詞細分類1
			String posCat1 = featureTokens[1]; 
//			// 品詞細分類2
//			String posCat2 = featureTokens[2]; 
//			// 品詞細分類3
//			String posCat3 = featureTokens[3]; 
//			// 活用型
//			String conjugationForm = featureTokens[4]; 
//			// 活用形
//			String conjugation = featureTokens[5]; 
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


				int entryId;
				if (originalForm.length() > 0)
					entryId = searchEntryByWord2(originalForm);
				else
					entryId = searchEntryByWord2(surface);

				boolean isEntry = entryId > 0;
				if (isEntry) {
					entryIdList.add(entryId);
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
				if (("助詞".equals(partOfSpeech)
						|| "助動詞".equals(partOfSpeech))
						&& !"副助詞".equals(posCat1)
						&& !"格助詞".equals(posCat1)
						) {
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
		}

		analyseResult.setOutputSentence(sb.toString());
		analyseResult.setEntryIdList(entryIdList);
	}

	private static final int STATE_START = 0;
	private static final int STATE_KANA = 1;
	private static final int STATE_KANJI = 2;
	private void appendRuby(StringBuilder sb, String surface, String reading) {
		String hiraganaReading = Wanakana.toHiragana(reading);
		List<String> tokenList = new ArrayList<>();
		String kanjiToken = "";
		String kanaToken = "";
		StringBuilder patternSb = new StringBuilder();
		int currentState = STATE_START;
		
		if (surface.length() == 0) return;
		
		boolean firstTokenIsKana = false;
		for (int i = 0; i < surface.length(); i++) {
			String ch = String.valueOf(surface.charAt(i));
			if (Wanakana.isKana(ch)) {
				if (i == 0) firstTokenIsKana = true;
				
				if (currentState == STATE_KANJI) {
					tokenList.add(kanjiToken);
					kanjiToken = "";
					kanaToken = ch;
				} else if (currentState == STATE_KANA) {
					kanaToken += ch;
				} else {
					kanaToken = ch;
				}
				patternSb.append(ch);
				currentState = STATE_KANA;
				
			} else {
				if (i == 0) firstTokenIsKana = false;
				
				if (currentState == STATE_KANJI) {
					kanjiToken += ch;
				} else if (currentState == STATE_KANA) {
					tokenList.add(kanaToken);
					kanaToken = "";
					kanjiToken = ch;
					patternSb.append("(.+)");
				} else {
					kanjiToken = ch;
					patternSb.append("(.+)");
				}
				currentState = STATE_KANJI;
			}
		}
		
		if (currentState == STATE_KANJI) {
			tokenList.add(kanjiToken);
		} else if (currentState == STATE_KANA) {
			tokenList.add(kanaToken);
		}
		
		Pattern pattern = Pattern.compile(patternSb.toString());
		Matcher matcher = pattern.matcher(hiraganaReading);
		if (matcher.matches()) {
			//int groupCnt = matcher.groupCount();
			int groupIdx;
			int kanjiTokenStartIdx = 0;
			if (firstTokenIsKana) {
				sb.append(tokenList.get(0));
				kanjiTokenStartIdx = 1;
			}
			for (int i = kanjiTokenStartIdx; i < tokenList.size(); i++) {
				if ((i - kanjiTokenStartIdx) % 2 == 0) {
					groupIdx = (i - kanjiTokenStartIdx) / 2 + 1;
					sb.append("|")
					.append(tokenList.get(i))
					.append("|")
					.append(matcher.group(groupIdx))
					.append("|");
				} else {
					sb.append(tokenList.get(i));
				}
			}
		} else {
			sb.append(surface);
		}
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