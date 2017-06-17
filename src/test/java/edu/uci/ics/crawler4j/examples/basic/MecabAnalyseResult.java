package edu.uci.ics.crawler4j.examples.basic;

import java.util.List;

public class MecabAnalyseResult {
	public static final int RESULT_SUCCESS = 0;
	
	public static final int RESULT_ERROR_MECAL_EXECUTE_ERROR = 1;
	public static final int RESULT_ERROR_UNEXPECT_OUTPUT_FORMAT = 2;
	
	public MecabAnalyseResult() {
	}
	
	public MecabAnalyseResult(int resultCode) {
		this.resultCode = resultCode;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("=====").append("MecabAnalyseResult").append("=====");
		sb.append("Result code: ").append(resultCode);
		sb.append("\nInput Sentence: ").append(inputSentence);
		sb.append("\nOutput Sentence: ").append(outputSentence);
		sb.append("\nEntry Ids: ");
		if (entryIdList != null) {
			for (int i = 0; i < entryIdList.size(); i++) {
				if (i > 0) sb.append(", ");
				sb.append(entryIdList.get(i));
			}
		} else {
			sb.append(entryIdList);
		}
		return sb.toString();
	}
	
	private int resultCode;
	
	private String inputSentence;
	
	private String outputSentence;
	
	private String errorLine;
	
	private List<Integer> entryIdList;

	public String getInputSentence() {
		return inputSentence;
	}

	public void setInputSentence(String inputSentence) {
		this.inputSentence = inputSentence;
	}

	public String getOutputSentence() {
		return outputSentence;
	}

	public void setOutputSentence(String outputSentence) {
		this.outputSentence = outputSentence;
	}

	public List<Integer> getEntryIdList() {
		return entryIdList;
	}

	public void setEntryIdList(List<Integer> entryIdList) {
		this.entryIdList = entryIdList;
	}

	public int getResultCode() {
		return resultCode;
	}

	public void setResultCode(int resultCode) {
		this.resultCode = resultCode;
	}

	public String getErrorLine() {
		return errorLine;
	}

	public void setErrorLine(String errorLine) {
		this.errorLine = errorLine;
	}
}
