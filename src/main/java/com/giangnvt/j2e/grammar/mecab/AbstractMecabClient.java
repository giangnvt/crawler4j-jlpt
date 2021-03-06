package com.giangnvt.j2e.grammar.mecab;

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
		Normal, VerbStarted, NounStarted, AdjectiveStarted, Joshi, JoDoushi
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

	public MecabAnalyseResult analyseInputSentence(String sentence, boolean printTrace) {
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
		if (printTrace)
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
		PART_OF_SPEECH_MAP.put("感動詞", new Integer[] {1});   //（すみません）
		//助詞    （の）
		//助動詞   （です）
		//記号    （。）
		//連体詞   （この）
		//接頭詞   （お-湯）
	}

	private void analyseMecabOutput(List<String[]> mecabOutputList, MecabAnalyseResult analyseResult) {
		StringBuilder sb = new StringBuilder();

		ParseStatus parseStatus = ParseStatus.Normal;
		boolean isEntryCloseFixedWaiting = false;
		boolean isEntryCloseUnfixedWaiting = false;
		List<Integer[]> entryIdList = new ArrayList<Integer[]>();
		boolean insideKeyword = false;

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
			
			boolean skipEntryLookup = false;
			if ("《".equals(surface)) {
			    insideKeyword = true;
			} else if ("》".equals(surface)) {
			    insideKeyword = false;
			}
			skipEntryLookup |= insideKeyword;
			skipEntryLookup |= Wanakana.isFullwidthNumeric(surface);

			if (PART_OF_SPEECH_MAP.containsKey(partOfSpeech)) {
				// For token like "います", not set as an entry
				if (("動詞".equals(partOfSpeech)
						&& "非自立".equals(posCat1)
						&& parseStatus == ParseStatus.Joshi)) {
					parseStatus = ParseStatus.VerbStarted;
					appendRuby(sb, surface, reading);
					continue;
				}
				
				if (isEntryCloseFixedWaiting) {
					sb.append("}");
				} else if (isEntryCloseUnfixedWaiting) {
					sb.append("?}");
				}
				isEntryCloseFixedWaiting = false;
				isEntryCloseUnfixedWaiting = false;

				Integer[] currentIds = null;
				//int entryId = 0;
				if (!skipEntryLookup) {
				    if (originalForm.length() > 0)
				    	currentIds = searchEntryByWord2(originalForm);
				    else
				    	currentIds = searchEntryByWord2(surface);
				}

				//boolean isEntry = currentIds != null && currentIds.length > 0;
				if (currentIds != null && currentIds.length == 1) {
					entryIdList.add(currentIds);
					sb.append("{");
					isEntryCloseFixedWaiting = true;
				} else if (currentIds != null && currentIds.length > 1) {
					entryIdList.add(currentIds);
					sb.append("{?");
					isEntryCloseUnfixedWaiting = true;
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
					} else if ("数".equals(posCat1)) {
						parseStatus = ParseStatus.Normal;
					} else {
					    parseStatus = ParseStatus.NounStarted;
					}
				}

				appendRuby(sb, surface, reading);

//				if (isEntry) {
//					isEntryCloseWaiting = true;
//					//sb.append("}");
//				}
			} else {
				if (("助詞".equals(partOfSpeech)
						&& !"副助詞".equals(posCat1)
						&& !"格助詞".equals(posCat1)
						//&& !"接続助詞".equals(posCat1)
						&& !"係助詞".equals(posCat1)
						&& !"連体化".equals(posCat1)
						&& !"副詞化".equals(posCat1)
						&& parseStatus != ParseStatus.JoDoushi)
						
						|| ("助動詞".equals(partOfSpeech)
								&& !"副助詞".equals(posCat1)
								&& !"格助詞".equals(posCat1)
								//&& !"接続助詞".equals(posCat1)
								&& !"係助詞".equals(posCat1)
								&& !"です".equals(originalForm))
								&& !"仮定形".equals(conjugation)		//なら
								&& !"未然形".equals(conjugation)		//だろ
						) {
					if (parseStatus == ParseStatus.VerbStarted
							|| parseStatus == ParseStatus.AdjectiveStarted
							|| parseStatus == ParseStatus.JoDoushi) {
						if ("助詞".equals(partOfSpeech))
							parseStatus = ParseStatus.Joshi;
						else
							parseStatus = ParseStatus.JoDoushi;
					} else {
						if (isEntryCloseFixedWaiting) {
							sb.append("}");
						} else if (isEntryCloseUnfixedWaiting) {
							sb.append("?}");
						}
						isEntryCloseFixedWaiting = false;
						isEntryCloseUnfixedWaiting = false;
					}

					sb.append(surface);
				} else {
					parseStatus = ParseStatus.Normal;
					if (isEntryCloseFixedWaiting) {
						sb.append("}");
					} else if (isEntryCloseUnfixedWaiting) {
						sb.append("?}");
					}
					sb.append(surface);
					isEntryCloseFixedWaiting = false;
					isEntryCloseUnfixedWaiting = false;
				}
			}
		}
		if (isEntryCloseFixedWaiting) {
			sb.append("}");
		} else if (isEntryCloseUnfixedWaiting) {
			sb.append("?}");
		}

		//String outputSentence = sb.toString().replace("《", "#%");
		//outputSentence = outputSentence.replace("", "");
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
		
		if (Wanakana.isFullwidthNumeric(surface)) {
		    sb.append(surface);
		    return;
		}
		
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


	public Integer[] searchEntryByWord2(String originalForm) {
		List<Integer> matchIdList = new ArrayList<Integer>();
		try {
			StringBuilder sb1 = new StringBuilder();
			sb1.append("SELECT id FROM entry")
			.append(" WHERE word1 = '").append(originalForm).append("'")
			.append("    OR word1 LIKE '").append(originalForm).append("{%'")
			.append("    OR word1 LIKE '%{").append(originalForm).append("'")
			.append("    OR word1 LIKE '%{").append(originalForm).append("{%'")
			;
			ResultSet rs = entrySearchCon.executeSelectQuery(sb1.toString());

			if (rs.next()) {
				//matchIdList.add(rs.getInt("id"));
				return new Integer[] {rs.getInt("id")} ;
			} else {
				StringBuilder sb2 = new StringBuilder();
				sb2.append("SELECT id FROM entry")
				.append(" WHERE word2 = '").append(originalForm).append("'")
				.append("    OR word2 LIKE '").append(originalForm).append("{%'")
				.append("    OR word2 LIKE '%{").append(originalForm).append("'")
				.append("    OR word2 LIKE '%{").append(originalForm).append("{%'")
				.append("    OR word2 LIKE '%{").append(originalForm).append("}%'")
				;
				ResultSet rs2 = entrySearchCon.executeSelectQuery(sb2.toString());

				while (rs2.next()) {
					matchIdList.add(rs2.getInt("id"));
				}
			}
			rs.close();
		} catch (Exception e) {
			System.err.print("IndexCreator [searchDb] error: " + e.getMessage());
			e.printStackTrace();
		}
		Integer[] arr = new Integer[matchIdList.size()];
		for (int i = 0; i < matchIdList.size(); i++) {
			arr[i] = matchIdList.get(i);
		}
		return arr;
	}
}